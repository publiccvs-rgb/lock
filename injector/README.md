# injector

Mapping-driven late-attach agent: finds official Minecraft method/class names in Mojang's own client mapping,
resolves them to the real obfuscated names a vanilla (non-Fabric) client.jar ships with, and patches that
bytecode live in an already-running JVM via the standard Attach API - no mods folder, no -javaagent flag at
launch.

## Status

- `Mapping.java` - parses Mojang's Proguard-format client mapping and builds official -> obfuscated name and
  descriptor resolution. Tested against the real downloaded 1.21.11 mapping (see `MappingTest`); resolves every
  class/method looked up, including a real 10-parameter method with nested type resolution.
- `Transformer.java` / `Agent.java` / `Attacher.java` - a real ASM-based HEAD-injection transformer and the
  Attach API flow (`VirtualMachine.attach` -> `loadAgent` -> `agentmain` -> `retransformClasses`). Compiles
  clean. Not yet run end-to-end: packaging the agent jar was blocked by this session's sandbox permissions.
- Not yet built: wiring real ESP/menu hooks through this path. The existing `lock.Core`/`lock.module`/`lock.render`
  tree is compiled against Mojang-named (deobfuscated) classes via Fabric Loom, which do not exist under those
  names in a vanilla runtime - only Fabric's own remapping classloader makes that work. Reaching the same
  functionality through late-attach needs either (a) a reflection bridge that calls into the obfuscated runtime
  by name (what `MinecraftInjectionAPI` does), or (b) building the ESP/render logic to work directly off raw
  bytecode coordinates without deobfuscated types. Neither is started yet.

## Build & run (needs Java 21, and `tools.jar`'s API is bundled in the JDK since 9 - no extra dependency)

```
cd injector
javac -cp "lib/asm-9.10.1.jar:lib/asm-commons-9.10.1.jar:lib/asm-tree-9.10.1.jar" -d out src/lock/inject/*.java

# self-check the mapping engine against a real client mapping (download client.txt for your version
# from Mojang's piston-meta first)
java -cp out lock.inject.MappingTest path/to/client.txt

# package the agent (classes + unpacked asm) and run it
mkdir build && cp -r out/* build/
cd build && for j in ../lib/*.jar; do jar xf "$j"; done
cat > MANIFEST.MF <<MF
Manifest-Version: 1.0
Agent-Class: lock.inject.Agent
Can-Retransform-Classes: true
Can-Redefine-Classes: true
MF
jar cfm ../agent.jar MANIFEST.MF lock org
cd ..

# attach to a running JVM by name match or PID, with "internalClass;method;descriptor" as the hook target
java -cp out lock.inject.Attacher <pid-or-name> agent.jar "pkg/Game;x;()V"
```

Minecraft launched through the stock Mojang launcher runs under Mojang's own obfuscated names (not Fabric's
intermediary names, which only exist inside a Fabric-remapped runtime) - resolve the real target coordinates
for your version with `Mapping` against that version's own `client.txt` before attaching.

JDK note: dynamic agent loading (late attach, as opposed to `-javaagent` at launch) will require
`-XX:+EnableDynamicAgentLoading` on the *target* JVM in a future JDK release (JEP 451). A JVM started by the
stock launcher does not pass that flag, so this may eventually require launching the target with it set, or an
`-javaagent` at startup instead of late attach.
