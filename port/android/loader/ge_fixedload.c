/*
 * ge_fixedload.c — libgeload.so: put libmain.so at the same address in every
 * process (Android only).
 *
 * Android loads an app's libraries at random addresses. Savestates
 * (port/src/savestate.c) hold native pointers into the game's image --
 * function pointers, the audio driver's lists -- so a state written by
 * another process had to be shifted: every word that looked like an image
 * pointer moved by the load difference. That scan cannot tell a pointer from
 * data that merely looks like one. Seen: the padding after ALEvent.type held
 * the upper bytes of a stale image pointer, the shift rewrote the event type,
 * a music sequence player lost its self-perpetuating event and spun forever
 * in __CSPVoiceHandler (and no further state could be saved). At a fixed
 * address there is nothing to shift.
 *
 * GoldenEyeActivity.loadLibraries() calls this (for the region's
 * libmain*.so) before SDL's own System.loadLibrary, which then finds it loaded
 * (same file, same namespace: this library's) and registers it with ART for
 * the JNI lookups. On failure the normal random-address load follows and
 * savestate.c falls back to the shift.
 */
#include <android/dlext.h>
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <sys/mman.h>

#ifndef MAP_FIXED_NOREPLACE
#define MAP_FIXED_NOREPLACE 0x100000
#endif

/* 8 GiB above the N64 window's base (PORT_ADDR_BASE = 256 GiB, 4 GiB long,
 * port/include/portaddr.h), inside a 39-bit user address space. The
 * reservation only costs address space; the image is a few MB. */
#define GE_IMAGE_BASE    0x4200000000ULL
#define GE_IMAGE_RESERVE (256ULL << 20)

#define LOG_TAG "ge007"

static jlong loadFixed(const char *soname)
{
    Dl_info info;
    void *h = dlopen(soname, RTLD_NOW | RTLD_NOLOAD);
    if (h) {
        /* Already in this process (the activity was re-created). */
        void *sym = dlsym(h, "SDL_main");
        jlong base = (sym && dladdr(sym, &info)) ? (jlong)(uintptr_t)info.dli_fbase : 0;
        dlclose(h);
        return base == (jlong)GE_IMAGE_BASE ? base : 0;
    }

    void *want = (void *)(uintptr_t)GE_IMAGE_BASE;
    void *r = mmap(want, GE_IMAGE_RESERVE, PROT_NONE,
                   MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE | MAP_FIXED_NOREPLACE, -1, 0);
    if (r != want) {
        /* Taken, or a pre-4.17 kernel that read the flag as a hint. */
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "%s: %p is not free (%s)", soname, want,
                            r == MAP_FAILED ? strerror(errno) : "mapped elsewhere");
        if (r != MAP_FAILED) {
            munmap(r, GE_IMAGE_RESERVE);
        }
        return 0;
    }

    android_dlextinfo ext;
    memset(&ext, 0, sizeof(ext));
    ext.flags = ANDROID_DLEXT_RESERVED_ADDRESS;
    ext.reserved_addr = r;
    ext.reserved_size = GE_IMAGE_RESERVE;
    h = android_dlopen_ext(soname, RTLD_NOW | RTLD_GLOBAL, &ext);
    if (!h) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "%s: fixed load failed: %s", soname, dlerror());
        munmap(r, GE_IMAGE_RESERVE);
        return 0;
    }
    void *sym = dlsym(h, "SDL_main");
    jlong base = (sym && dladdr(sym, &info)) ? (jlong)(uintptr_t)info.dli_fbase : 0;
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s loaded at %p", soname, (void *)(uintptr_t)base);
    return base;
}

/* soname: the region's game library (libmain.so, libmain_pal.so, ...).
 * Returns its load base, 0 if it could not go to GE_IMAGE_BASE. */
JNIEXPORT jlong JNICALL
Java_io_github_zeptoniator_ge007_GoldenEyeActivity_nativeLoadMainFixed(JNIEnv *env, jclass cls, jstring jname)
{
    (void)cls;
    const char *soname = (*env)->GetStringUTFChars(env, jname, NULL);
    if (!soname) {
        return 0;
    }
    jlong base = loadFixed(soname);
    (*env)->ReleaseStringUTFChars(env, jname, soname);
    return base;
}
