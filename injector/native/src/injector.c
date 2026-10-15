/*
 * injector.exe - finds a running process (by executable name, e.g. "javaw.exe") and loads a DLL into it via
 * the classic CreateRemoteThread + LoadLibraryA technique: the same mechanism used by debuggers, profilers
 * and every external game-overlay tool. Requires running as the same user as the target process (or elevated,
 * for a process running as a different/elevated user).
 *
 * Usage: injector.exe <path-to-dll> [process-name] [pid]
 *   process-name defaults to "javaw.exe" (what the stock Minecraft launcher runs under); pass "java.exe" for
 *   a manually-started client, or a specific pid as a third argument to skip the name search entirely.
 */
#include <windows.h>
#include <tlhelp32.h>
#include <stdio.h>

static DWORD findProcessByName(const char* exeName) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return 0;
    PROCESSENTRY32 pe;
    pe.dwSize = sizeof(pe);
    DWORD found = 0;
    if (Process32First(snap, &pe)) {
        do {
            if (_stricmp(pe.szExeFile, exeName) == 0) { found = pe.th32ProcessID; break; }
        } while (Process32Next(snap, &pe));
    }
    CloseHandle(snap);
    return found;
}

static int injectDll(DWORD pid, const char* dllPath) {
    HANDLE proc = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION | PROCESS_VM_OPERATION |
                               PROCESS_VM_WRITE | PROCESS_VM_READ, FALSE, pid);
    if (!proc) { printf("OpenProcess failed (pid %lu): error %lu\n", pid, GetLastError()); return 1; }

    size_t pathLen = strlen(dllPath) + 1;
    LPVOID remoteMem = VirtualAllocEx(proc, NULL, pathLen, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remoteMem) { printf("VirtualAllocEx failed: error %lu\n", GetLastError()); CloseHandle(proc); return 1; }

    if (!WriteProcessMemory(proc, remoteMem, dllPath, pathLen, NULL)) {
        printf("WriteProcessMemory failed: error %lu\n", GetLastError());
        VirtualFreeEx(proc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(proc);
        return 1;
    }

    HMODULE kernel32 = GetModuleHandleA("kernel32.dll");
    LPTHREAD_START_ROUTINE loadLibraryAddr = (LPTHREAD_START_ROUTINE) GetProcAddress(kernel32, "LoadLibraryA");
    if (!loadLibraryAddr) { printf("could not resolve LoadLibraryA\n"); CloseHandle(proc); return 1; }

    HANDLE thread = CreateRemoteThread(proc, NULL, 0, loadLibraryAddr, remoteMem, 0, NULL);
    if (!thread) { printf("CreateRemoteThread failed: error %lu\n", GetLastError()); CloseHandle(proc); return 1; }

    WaitForSingleObject(thread, INFINITE);
    DWORD exitCode = 0;
    GetExitCodeThread(thread, &exitCode);
    CloseHandle(thread);
    VirtualFreeEx(proc, remoteMem, 0, MEM_RELEASE);
    CloseHandle(proc);

    if (exitCode == 0) {
        printf("LoadLibraryA returned NULL in the target process - the DLL failed to load there "
               "(check lock_inject.log next to the DLL; usually a missing dependency or a bitness mismatch)\n");
        return 1;
    }
    printf("injected: module base 0x%08lX in pid %lu\n", exitCode, pid);
    return 0;
}

int main(int argc, char** argv) {
    if (argc < 2) {
        printf("usage: injector.exe <path-to-lockhook.dll> [process-name=javaw.exe] [pid]\n");
        return 1;
    }
    char dllPath[MAX_PATH];
    if (!GetFullPathNameA(argv[1], MAX_PATH, dllPath, NULL)) { printf("bad dll path\n"); return 1; }
    if (GetFileAttributesA(dllPath) == INVALID_FILE_ATTRIBUTES) { printf("dll not found: %s\n", dllPath); return 1; }

    DWORD pid;
    if (argc >= 4) {
        pid = (DWORD) atoi(argv[3]);
    } else {
        const char* name = argc >= 3 ? argv[2] : "javaw.exe";
        pid = findProcessByName(name);
        if (pid == 0 && argc < 3) pid = findProcessByName("java.exe");
        if (pid == 0) { printf("no running process matched\n"); return 1; }
        printf("found pid %lu\n", pid);
    }

    printf("injecting %s into pid %lu\n", dllPath, pid);
    return injectDll(pid, dllPath);
}
