package lock.inject;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.util.List;

/**
 * Finds a running JVM and loads the agent into it via the standard Attach API, the same mechanism `jstack`,
 * `jcmd` and every Java profiler use to reach into a process that was not started with -javaagent.
 */
public final class Attacher {
    public static List<VirtualMachineDescriptor> list() { return VirtualMachine.list(); }

    /** nameFilter matches against the target's display name (usually its main class + args), case-insensitive. */
    public static VirtualMachineDescriptor find(String nameFilter) {
        for (VirtualMachineDescriptor d : list()) {
            if (d.displayName() != null && d.displayName().toLowerCase().contains(nameFilter.toLowerCase())) return d;
        }
        return null;
    }

    public static void attach(String pid, String agentJarPath, String agentArgs) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentJarPath, agentArgs);
        } finally {
            vm.detach();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: Attacher <nameFilter|pid> <agentJar> [agentArgs]");
            System.out.println("running JVMs:");
            for (VirtualMachineDescriptor d : list()) System.out.println("  " + d.id() + "  " + d.displayName());
            return;
        }
        String target = args[0], jar = args[1], agentArgs = args.length > 2 ? args[2] : "";
        String pid = target.chars().allMatch(Character::isDigit) ? target : null;
        if (pid == null) {
            VirtualMachineDescriptor d = find(target);
            if (d == null) throw new IllegalStateException("no running JVM matches: " + target);
            pid = d.id();
            System.out.println("matched PID " + pid + " (" + d.displayName() + ")");
        }
        attach(pid, jar, agentArgs);
        System.out.println("agent loaded into PID " + pid);
    }
}
