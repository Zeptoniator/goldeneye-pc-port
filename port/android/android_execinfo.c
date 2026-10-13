/*
 * android_execinfo.c — backtrace()/backtrace_symbols()/backtrace_symbols_fd()
 * for the Android build.
 *
 * bionic only gained <execinfo.h> at API 33; the APK's floor is lower, and
 * port/src/crash.c and port/src/port_addr.c (PORT_ADDR_STRICT) call these on
 * every PLATFORM_LINUX target. Same contract as glibc's: frames from the
 * unwinder, names from dladdr(). libmain.so keeps every symbol local
 * (port/android/libmain.map), so these never shadow bionic's.
 */
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <unwind.h>

struct BtState {
    void **buf;
    int size;
    int count;
};

static _Unwind_Reason_Code btStep(struct _Unwind_Context *ctx, void *arg)
{
    struct BtState *st = (struct BtState *)arg;
    uintptr_t pc = _Unwind_GetIP(ctx);
    if (pc == 0) {
        return _URC_END_OF_STACK;
    }
    if (st->count >= st->size) {
        return _URC_END_OF_STACK;
    }
    st->buf[st->count++] = (void *)pc;
    return _URC_NO_REASON;
}

int backtrace(void **buffer, int size)
{
    struct BtState st = { buffer, size, 0 };
    if (!buffer || size <= 0) {
        return 0;
    }
    _Unwind_Backtrace(btStep, &st);
    return st.count;
}

static int btFormat(void *pc, char *out, size_t outlen)
{
    Dl_info info;
    if (dladdr(pc, &info) && info.dli_fname) {
        const char *lib = strrchr(info.dli_fname, '/');
        lib = lib ? lib + 1 : info.dli_fname;
        if (info.dli_sname) {
            return snprintf(out, outlen, "%s(%s+0x%lx) [%p]", lib, info.dli_sname,
                            (unsigned long)((uintptr_t)pc - (uintptr_t)info.dli_saddr), pc);
        }
        return snprintf(out, outlen, "%s(+0x%lx) [%p]", lib,
                        (unsigned long)((uintptr_t)pc - (uintptr_t)info.dli_fbase), pc);
    }
    return snprintf(out, outlen, "[%p]", pc);
}

char **backtrace_symbols(void *const *buffer, int size)
{
    enum { LINE = 256 };
    if (!buffer || size <= 0) {
        return NULL;
    }
    /* One block: the pointer array followed by the strings (caller frees it). */
    char **out = (char **)malloc((size_t)size * (sizeof(char *) + LINE));
    if (!out) {
        return NULL;
    }
    char *text = (char *)(out + size);
    for (int i = 0; i < size; i++) {
        out[i] = text + (size_t)i * LINE;
        btFormat(buffer[i], out[i], LINE);
    }
    return out;
}

void backtrace_symbols_fd(void *const *buffer, int size, int fd)
{
    char line[256];
    for (int i = 0; buffer && i < size; i++) {
        int n = btFormat(buffer[i], line, sizeof(line) - 1);
        if (n < 0) {
            continue;
        }
        if (n > (int)sizeof(line) - 2) {
            n = (int)sizeof(line) - 2;
        }
        line[n++] = '\n';
        (void)!write(fd, line, (size_t)n);
    }
}
