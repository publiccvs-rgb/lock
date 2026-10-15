#!/usr/bin/env python3
"""
End-to-end test of Lock against the real Minecraft 1.21.11 client (Linux, needs Xvfb + Mesa).

Prerequisites:  gradle :agent:jar :mapping:setupMinecraftAssets
                a singleplayer world named "Test" in .mc-dev/run/saves (see docs/TESTING.md)
Run:            python3 tools/integration_test.py [--keep-screenshots DIR]

Checks: agent loads and reports READY over IPC; every hook is applied or legitimately pending;
modules toggle and settings change through IPC and persist to config.json; the Click GUI and HUD
editor open; the runtime announcement disappears when the game exits; and the agent's disable path
leaves the game running without Lock.
"""
import glob, json, os, shutil, socket, subprocess, sys, tempfile, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOME = tempfile.mkdtemp(prefix="lock-it-")
SHOTS = sys.argv[sys.argv.index("--keep-screenshots") + 1] if "--keep-screenshots" in sys.argv else None
failures = []


def check(cond, msg):
    print(("  PASS  " if cond else "  FAIL  ") + msg)
    if not cond:
        failures.append(msg)


def runtime_info():
    files = glob.glob(os.path.join(HOME, "runtime", "*.json"))
    return json.load(open(files[0])) if files else None


def ipc(cmd, **args):
    info = runtime_info()
    s = socket.create_connection(("127.0.0.1", info["port"]), timeout=10)
    f = s.makefile("rw")
    req = dict(args, cmd=cmd, token=info["token"])
    f.write(json.dumps(req) + "\n")
    f.flush()
    r = json.loads(f.readline())
    s.close()
    return r


def start_game(display, agent_args=None):
    env = dict(os.environ, DISPLAY=display, LOCK_HOME=HOME)
    if agent_args:
        env["LOCK_AGENT_ARGS"] = agent_args
    mappings = os.path.join(ROOT, ".mc-dev", "1.21.11", "client.txt")
    os.makedirs(os.path.join(HOME, "cache", "mappings"), exist_ok=True)
    shutil.copy(mappings, os.path.join(HOME, "cache", "mappings", "1.21.11-client.txt"))
    log = open(os.path.join(HOME, "game.log"), "w")
    return subprocess.Popen([os.path.join(ROOT, "tools", "dev-run.sh"), "--quickPlaySingleplayer", "Test"], env=env, stdout=log, stderr=subprocess.STDOUT)


def wait_for(pred, timeout, step=2):
    end = time.time() + timeout
    while time.time() < end:
        try:
            v = pred()
            if v:
                return v
        except Exception:
            pass
        time.sleep(step)
    return None


def shot(display, name):
    if SHOTS:
        os.makedirs(SHOTS, exist_ok=True)
        subprocess.run(["import", "-display", display, "-window", "root", os.path.join(SHOTS, name + ".png")])


def main():
    display = ":97"
    xvfb = subprocess.Popen(["Xvfb", display, "-screen", "0", "1280x720x24"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    try:
        print("== Lock enabled")
        game = start_game(display)
        st = wait_for(lambda: (r := ipc("status")) and r.get("clientReady") and r.get("inWorld") and r, 240)
        check(st is not None, "runtime reports READY and in world over IPC")
        if not st:
            return
        check(st["minecraftVersion"] == "1.21.11", "version detected from the running client jar")
        hooks = st["hooks"]
        bad = [h for h in hooks if not h["applied"] and not h["detail"].startswith("pending")]
        check(not bad, f"all {len(hooks)} hooks applied or pending class load ({sum(h['applied'] for h in hooks)} applied) {bad}")
        check(not st["compatibility"] or "Modules without implementation" not in " ".join(st["compatibility"]), "every catalog module has an implementation")
        for m in ["keystrokes", "coordinates", "compass", "crosshair", "toggle_sprint"]:
            check(ipc("setEnabled", module=m, enabled=True)["ok"], f"enable {m}")
        check(ipc("setSetting", module="zoom", key="level", value=7)["ok"], "change zoom level")
        check(not ipc("setSetting", module="zoom", key="nope", value=1)["ok"], "unknown setting rejected")
        time.sleep(4)
        st = ipc("status")
        check(st["modules"]["keystrokes"]["active"], "keystrokes active after IPC toggle")
        check(not any(v["faulted"] for v in st["modules"].values()), "no module faulted while rendering")
        shot(display, "hud")
        cfg = ipc("getConfig")["config"]
        check(cfg["profiles"]["Default"]["modules"]["zoom"]["settings"]["level"] == 7, "setting visible in live config")
        check(ipc("openGui")["ok"], "Click GUI opens")
        time.sleep(3)
        shot(display, "gui")
        check(ipc("openHudEditor")["ok"], "HUD editor opens")
        time.sleep(3)
        shot(display, "editor")
        ipc("closeGui")
        st = ipc("status")
        check(not any(v["faulted"] for v in st["modules"].values()), "GUI screens rendered without module faults")
        time.sleep(4)  # autosave delay
        saved = json.load(open(os.path.join(HOME, "config.json")))
        check(saved["profiles"]["Default"]["modules"]["zoom"]["settings"]["level"] == 7, "configuration persisted to disk")
        game.terminate()
        game.wait(60)
        check(wait_for(lambda: runtime_info() is None, 20, 1) is True, "runtime announcement removed when the game exits")

        print("== Lock disabled by agent argument (safe failure path)")
        game = start_game(display, "disable")
        st = wait_for(lambda: (r := ipc("status")) and r.get("state") == "DISABLED" and r, 120)
        check(st is not None, "agent reports DISABLED and the game keeps running")
        time.sleep(20)
        check(game.poll() is None, "game still running without Lock")
        game.terminate()
        game.wait(60)
    finally:
        xvfb.terminate()
        print("\nLOCK_HOME used:", HOME)
        print("RESULT:", "FAILED (%d)" % len(failures) if failures else "ALL PASSED")
        sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
