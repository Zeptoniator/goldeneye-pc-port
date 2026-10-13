/*
 * android_savestate.c — JNI for port/src/savestate.c (SaveStates.java).
 * The calls only post requests or read status; the game thread does the work.
 */
#include <jni.h>
#include <string.h>

#include "system.h"

void savestateRequestSave(const char *path);
void savestateRequestLoad(const char *path);
int savestateBusy(void);
int savestateCanSave(void);
int savestateLastResult(char *msg, int msgLen);
int savestatePeek(const char *path, int *stage, int *difficulty, long long *savedAt);

static void withPath(JNIEnv *env, jstring jpath, void (*fn)(const char *))
{
    const char *p = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (p) {
        fn(p);
        (*env)->ReleaseStringUTFChars(env, jpath, p);
    }
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_SaveStates_nativeSave(JNIEnv *env, jclass cls, jstring path)
{
    (void)cls;
    withPath(env, path, savestateRequestSave);
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_SaveStates_nativeLoad(JNIEnv *env, jclass cls, jstring path)
{
    (void)cls;
    withPath(env, path, savestateRequestLoad);
}

JNIEXPORT jboolean JNICALL
Java_io_github_zeptoniator_ge007_SaveStates_nativeBusy(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    return savestateBusy() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_zeptoniator_ge007_SaveStates_nativeCanSave(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    return savestateCanSave() ? JNI_TRUE : JNI_FALSE;
}

/* 1 saved, 2 loaded, 0 nothing yet, < 0 error. */
JNIEXPORT jint JNICALL
Java_io_github_zeptoniator_ge007_SaveStates_nativeLastResult(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    return savestateLastResult(NULL, 0);
}

JNIEXPORT jstring JNICALL
Java_io_github_zeptoniator_ge007_SaveStates_nativeLastMessage(JNIEnv *env, jclass cls)
{
    (void)cls;
    char msg[160];
    savestateLastResult(msg, sizeof(msg));
    return (*env)->NewStringUTF(env, msg);
}

/* {status (1 loadable, 0 other build, -1 none), stage, difficulty, savedAt} */
JNIEXPORT jlongArray JNICALL
Java_io_github_zeptoniator_ge007_SaveStates_nativePeek(JNIEnv *env, jclass cls, jstring jpath)
{
    (void)cls;
    jlong out[4] = { -1, 0, 0, 0 };
    const char *p = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (p) {
        int stage = 0, diff = 0;
        long long at = 0;
        out[0] = savestatePeek(p, &stage, &diff, &at);
        out[1] = stage;
        out[2] = diff;
        out[3] = at;
        (*env)->ReleaseStringUTFChars(env, jpath, p);
    }
    jlongArray arr = (*env)->NewLongArray(env, 4);
    if (arr) {
        (*env)->SetLongArrayRegion(env, arr, 0, 4, out);
    }
    return arr;
}
