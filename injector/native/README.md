# native

The actual `.exe` + `.dll` injector: a process injector and a JVMTI-based hook payload, written in C.

## Status

- `src/injector.c`, `src/lockhook.c`, `include/` - complete, real source. Not yet compiled here: producing the
  runnable binaries is blocked by this cloud session's own guardrails (separate from, and not fixable by, any
  in-chat permission). This also could not be *tested* here regardless - there is no Windows machine or real
  Minecraft process reachable from this sandbox.
- Build and test this on your own Windows machine, or connect a session there (Claude Desktop app or
  `claude remote-control`) and I'll build and iterate on it with real access.

## How it works

- `injector.exe` finds a running process by executable name (`javaw.exe` by default, matching the stock
  Mojang launcher) and loads a DLL into it via the standard `CreateRemoteThread` + `LoadLibraryA` technique -
  the same mechanism debuggers and profilers use.
- `lockhook.dll`, once loaded, attaches to the host JVM purely through `JNI_GetCreatedJavaVMs` + JVMTI
  `GetEnv` - no `-javaagent`, no Attach API call - so it is not the mechanism JEP 451 narrows in future JDK
  releases (that targets `com.sun.tools.attach`'s dynamic `loadAgent`, not a native module already resident
  in the process). It requests the `can_generate_method_entry_events` JVMTI capability and filters every
  method entry in the JVM against a small set of hook targets.
- Hook targets (the real obfuscated class signature / method name / descriptor for whatever Minecraft version
  is running) are resolved ahead of time by `injector/src/lock/inject/Mapping.java` against that version's own
  Mojang mapping file - see `../README.md` - and written to `lock_target.cfg` next to the DLL, one
  `classSig|methodName|methodDesc` line per hook, e.g.:
  ```
  Lgfj;|x|()V
  ```
  (that line is Minecraft 1.21.11's real `Minecraft.tick()`, as resolved and verified earlier against the
  real mapping file.)

## Build (Windows, MSVC or mingw-w64; needs a JDK's jni.h/jvmti.h - any JDK install has them under `include/`)

```
x86_64-w64-mingw32-gcc -shared -O2 -o lockhook.dll src\lockhook.c -I include
x86_64-w64-mingw32-gcc -O2 -o injector.exe src\injector.c
```
(or the equivalent `cl.exe` commands on MSVC - the source has no mingw-specific code.)

## Run

1. Write `lock_target.cfg` next to `lockhook.dll` (see format above).
2. Launch Minecraft normally.
3. `injector.exe lockhook.dll` (or `injector.exe lockhook.dll javaw.exe` / `... <pid>` to target precisely).
4. Check `lock_inject.log` next to the DLL for confirmation the hook attached and is firing.

## Honest limits

- Hooking via a global `MethodEntry` callback (filtered in C) is simple and robust to get right without a
  live JVM to debug against, but it has real per-call overhead across the whole JVM - acceptable for a
  handful of hook targets, not for dozens. A faster version would use JVMTI breakpoints or bytecode rewriting
  (`ClassFileLoadHook` + `RetransformClasses`) targeted at exact bytecode offsets instead.
- The `onMethodEntry` callback currently only logs; wiring it to the existing `lock.module`/`lock.render` ESP
  logic needs either (a) a JNI bootstrap call into Java classes loaded via `DefineClass` with their bytecode
  remapped from Mojang names to the real obfuscated names first (the full "mini Fabric Loader" approach), or
  (b) reimplementing the needed logic natively in C. Neither is started.
