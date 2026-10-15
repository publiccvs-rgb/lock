/*
 * lockhook.dll - the payload injected into a running Minecraft (javaw.exe) process.
 *
 * Mechanism: once loaded via LoadLibraryA (see injector.c), it attaches to the host JVM purely through the
 * standard JNI/JVMTI C API that every JVM exposes to code already running inside its process - no
 * java.lang.instrument, no -javaagent, no Attach API call. That matters for two reasons:
 *   1. It works against a JVM that was never told it would be instrumented (ordinary vanilla launch).
 *   2. It is not the mechanism JEP 451 ("Disallow the Dynamic Loading of Agents by Default") narrows in future
 *      JDK releases - that JEP targets com.sun.tools.attach's loadAgent() path specifically, not a native
 *      module already resident in the process calling JNI_GetCreatedJavaVMs + JVMTI GetEnv directly.
 *
 * Hook technique: JVMTI's MethodEntry event (global, filtered in the callback by exact class signature +
 * method name + descriptor) rather than bytecode rewriting. This avoids needing a hand-rolled class-file
 * editor in C; the cost is that the callback fires for every method call in the whole JVM and must filter
 * cheaply, which is fine for a small number of hook targets.
 *
 * Target coordinates (the real obfuscated class signature / method name / descriptor for whatever Minecraft
 * version is actually running) are resolved ahead of time by the Java-side Mapping tool against that version's
 * own Mojang mapping file, then written to a small config file placed next to this DLL - see
 * injector/README.md. This DLL has no built-in knowledge of any specific Minecraft version.
 */
#include <windows.h>
#include <stdio.h>
#include "jni.h"
#include "jvmti.h"

#define MAX_TARGETS 8
#define LOG_PATH_MAX 512

typedef struct {
    char classSig[128];
    char name[64];
    char desc[128];
} HookTarget;

static HookTarget g_targets[MAX_TARGETS];
static int g_targetCount = 0;
static FILE* g_log = NULL;
static volatile LONG g_hits[MAX_TARGETS];

static void logf(const char* fmt, ...) {
    if (!g_log) return;
    va_list args;
    va_start(args, fmt);
    vfprintf(g_log, fmt, args);
    va_end(args);
    fflush(g_log);
}

/* Reads "<dll-dir>\lock_target.cfg": one "classSig|methodName|methodDesc" hook per line, '#' comments allowed. */
static void loadTargets(const char* dllDir) {
    char path[LOG_PATH_MAX];
    snprintf(path, sizeof(path), "%s\\lock_target.cfg", dllDir);
    FILE* f = fopen(path, "r");
    if (!f) { logf("[lock] no config at %s - nothing to hook\n", path); return; }
    char line[512];
    while (fgets(line, sizeof(line), f) && g_targetCount < MAX_TARGETS) {
        if (line[0] == '#' || line[0] == '\r' || line[0] == '\n') continue;
        char* sig = strtok(line, "|");
        char* name = strtok(NULL, "|");
        char* desc = strtok(NULL, "|\r\n");
        if (!sig || !name || !desc) continue;
        HookTarget* t = &g_targets[g_targetCount++];
        snprintf(t->classSig, sizeof(t->classSig), "%s", sig);
        snprintf(t->name, sizeof(t->name), "%s", name);
        snprintf(t->desc, sizeof(t->desc), "%s", desc);
        logf("[lock] target %d: %s %s%s\n", g_targetCount - 1, t->classSig, t->name, t->desc);
    }
    fclose(f);
}

static void JNICALL onMethodEntry(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread, jmethodID method) {
    jclass declaring = NULL;
    if ((*jvmti)->GetMethodDeclaringClass(jvmti, method, &declaring) != JVMTI_ERROR_NONE) return;
    char* classSig = NULL;
    if ((*jvmti)->GetClassSignature(jvmti, declaring, &classSig, NULL) != JVMTI_ERROR_NONE) return;
    char* methodName = NULL;
    char* methodSig = NULL;
    if ((*jvmti)->GetMethodName(jvmti, method, &methodName, &methodSig, NULL) != JVMTI_ERROR_NONE) {
        (*jvmti)->Deallocate(jvmti, (unsigned char*) classSig);
        return;
    }

    for (int i = 0; i < g_targetCount; i++) {
        HookTarget* t = &g_targets[i];
        if (strcmp(classSig, t->classSig) == 0 && strcmp(methodName, t->name) == 0 && strcmp(methodSig, t->desc) == 0) {
            LONG n = InterlockedIncrement(&g_hits[i]);
            if (n <= 5 || n % 200 == 0) logf("[lock] hook fired: %s.%s%s (#%ld)\n", classSig, methodName, methodSig, n);
            /* Extension point: with jni/jvmti/thread in hand here, this is where per-hook native logic
               (reading live object state, calling back into a bootstrap Java class via JNI, etc.) attaches. */
            break;
        }
    }

    (*jvmti)->Deallocate(jvmti, (unsigned char*) classSig);
    (*jvmti)->Deallocate(jvmti, (unsigned char*) methodName);
    (*jvmti)->Deallocate(jvmti, (unsigned char*) methodSig);
}

static DWORD WINAPI agentThread(LPVOID param) {
    char dllDir[LOG_PATH_MAX] = {0};
    HMODULE self = (HMODULE) param;
    DWORD n = GetModuleFileNameA(self, dllDir, sizeof(dllDir));
    if (n > 0) { char* slash = strrchr(dllDir, '\\'); if (slash) *slash = 0; }

    char logPath[LOG_PATH_MAX];
    snprintf(logPath, sizeof(logPath), "%s\\lock_inject.log", dllDir);
    g_log = fopen(logPath, "a");
    logf("\n[lock] lockhook attached, dll dir = %s\n", dllDir);

    loadTargets(dllDir);
    if (g_targetCount == 0) { logf("[lock] nothing configured to hook, idling\n"); return 0; }

    HMODULE jvmDll = GetModuleHandleA("jvm.dll");
    if (!jvmDll) { logf("[lock] jvm.dll not found in this process - not a JVM host?\n"); return 1; }

    typedef jint (JNICALL *GetCreatedJavaVMs_t)(JavaVM**, jsize, jsize*);
    GetCreatedJavaVMs_t getVms = (GetCreatedJavaVMs_t) GetProcAddress(jvmDll, "JNI_GetCreatedJavaVMs");
    if (!getVms) { logf("[lock] JNI_GetCreatedJavaVMs not exported\n"); return 1; }

    JavaVM* vms[1];
    jsize found = 0;
    if (getVms(vms, 1, &found) != JNI_OK || found == 0) { logf("[lock] no running JVM found\n"); return 1; }
    JavaVM* vm = vms[0];

    jvmtiEnv* jvmti = NULL;
    if ((*vm)->GetEnv(vm, (void**) &jvmti, JVMTI_VERSION_1_2) != JNI_OK || !jvmti) {
        logf("[lock] GetEnv(JVMTI) failed - this JVM may not support live JVMTI attach\n");
        return 1;
    }

    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof(caps));
    caps.can_generate_method_entry_events = 1;
    jvmtiError err = (*jvmti)->AddCapabilities(jvmti, &caps);
    if (err != JVMTI_ERROR_NONE) { logf("[lock] AddCapabilities failed: %d\n", err); return 1; }

    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.MethodEntry = &onMethodEntry;
    err = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) { logf("[lock] SetEventCallbacks failed: %d\n", err); return 1; }

    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, NULL);
    if (err != JVMTI_ERROR_NONE) { logf("[lock] SetEventNotificationMode failed: %d\n", err); return 1; }

    logf("[lock] JVMTI live, watching %d target(s)\n", g_targetCount);
    return 0;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD reason, LPVOID reserved) {
    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(hModule);
        /* Never do real work on the loader-lock thread (DllMain itself) - hand off immediately. */
        CreateThread(NULL, 0, agentThread, (LPVOID) hModule, 0, NULL);
    }
    return TRUE;
}
