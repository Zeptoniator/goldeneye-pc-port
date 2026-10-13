/*
 * savestate.c — save and load the whole game at any moment (Android port).
 *
 * The game's state is three blocks of memory:
 *
 *   - the N64 DRAM arena (PORT_DRAM_V1_BASE, 8 MB, same address every run:
 *     it sits in the fixed N64 window, portaddr.h);
 *   - the game's globals: the .data/.bss of the game's own translation units,
 *     gathered by port/savestate.ld into .ge_state_data / .ge_state_bss
 *     (src/game, the engine files that hold game state, libaudio);
 *   - nothing else: port, renderer and SDL state are rebuilt or untouched.
 *
 * Capture and restore run on the main game thread inside its gfxFrameMsgQ
 * receive (savestateGameTick, called from libultra.c), the same point where
 * the F10 watch edits are applied: between two game frames. The other game
 * threads are parked or blocked on a queue for the copy
 * (portFreezeGameThreads), and the render worker is idle.
 *
 * Kept from the running process on a load: every OS message queue and its
 * buffer, every thread object and every timer (portForEachKernelObject):
 * they describe this process's threads, which are not restored. Game memory
 * holds native pointers into libmain.so (globals, tables, functions), which
 * Android loads at a random address: 8-byte words that pointed into the
 * saving process's image are moved by the difference of the two bases.
 *
 * A load runs in two steps. (1) The saved stage is loaded the normal way
 * (g_MainStageNum, as the front end does), so everything the port builds per
 * stage (texture cache, viewports, room pool) matches it. (2) At the first
 * frame of that stage the saved memory replaces the fresh one; the texture
 * cache and the frame interpolation are reset, and the Bond files are re-read
 * from the EEPROM so that loading an old state never drops progress saved
 * since. (Frame interpolation needs nothing: it treats the jump as a cut.)
 *
 * Solo missions only (MP is untested). A state only loads in the build that
 * wrote it (fingerprint of the layout).
 */

#include <stdio.h>
#include <string.h>
#include <time.h>
#include <SDL.h>        /* before the N64 headers, as in input.c */

#include <elf.h>

#include <ultra64.h>
#include "realzlib.h"   /* host zlib by absolute path (CMake-generated) */

#include "platform.h"
#include "system.h"
#include "portaddr.h"
#include "versioninfo.h"

extern void *malloc(size_t size);
extern void free(void *ptr);
extern int atoi(const char *s);

/* Game (declared here with plain types: no game headers in this file). */
extern int g_StageNum;                 /* src/boss.c */
extern int g_MainStageNum;             /* src/boss.c */
extern int current_menu;               /* src/game/front.c (MENU enum) */
extern void *g_CurrentPlayer;          /* src/game/player.c */
extern int getPlayerCount(void);
extern int lvlGetSelectedDifficulty(void);
extern void lvlSetSelectedDifficulty(int difficulty);
extern void set_selected_difficulty(int difficulty);
extern void set_solo_and_ptr_briefing(int stage);
extern void fileValidateSaves(void);
extern s32 get_is_ramrom_flag(void);           /* src/game/ramromreplay.c: demo playback */
extern s32 get_recording_ramrom_flag(void);

/* Port. */
extern void videoResetTextureCache(void);
extern int videoStageLoadCount(void);
extern int portFreezeGameThreads(int timeoutMs);
extern void portThawGameThreads(void);
extern void portForEachKernelObject(void (*fn)(const void *p, size_t n, void *ctx), void *ctx);

/* port/savestate.ld */
extern unsigned char __ge_state_data_start[], __ge_state_data_end[];
extern unsigned char __ge_state_bss_start[], __ge_state_bss_end[];
/* The linker: first and one-past-last byte of the loaded image. */
extern unsigned char __ehdr_start[], _end[];

#define LEVELID_TITLE 90
#define LEVELID_SOLO_MAX 57        /* LEVELID_MAX: solo stage ids are below */
#define MENU_INVALID (-1)
#define MENU_RUN_STAGE 11

#define SS_MAGIC "GE7STATE"
#define SS_VERSION 1
#define SS_FREEZE_MS 400

/* On-disk header, little-endian, fixed layout (also read by the Java UI:
 * android/.../SaveStates.java). */
typedef struct SsHeader {
    char magic[8];
    uint32_t version;
    uint32_t headerSize;
    uint64_t fingerprint;
    uint64_t imageBase, imageSize;
    uint64_t dataSize, bssSize, arenaSize;
    uint64_t payloadSize, packedSize;
    uint32_t payloadCrc;
    int32_t stage, players, difficulty;
    int64_t savedAt;               /* unix seconds */
    uint8_t reserved[64];
} SsHeader;

/* -------------------------------------------------------------- regions */

typedef struct Region {
    unsigned char *p;
    size_t n;
} Region;

static void regions(Region r[3])
{
    r[0].p = __ge_state_data_start;
    r[0].n = (size_t)(__ge_state_data_end - __ge_state_data_start);
    r[1].p = __ge_state_bss_start;
    r[1].n = (size_t)(__ge_state_bss_end - __ge_state_bss_start);
    r[2].p = (unsigned char *)(uintptr_t)PORT_DRAM_V1_BASE;
    r[2].n = PORT_DRAM_SIZE;
}

static uintptr_t imageStart(void) { return (uintptr_t)__ehdr_start; }
static uintptr_t imageSize(void) { return (uintptr_t)_end - (uintptr_t)__ehdr_start; }

/* The image's GNU build-id note (lld and GNU ld both emit one here): any
 * change to the code changes it. A layout-only fingerprint let a state from
 * a build with different code load, and its relocated function pointers then
 * landed in the wrong functions. Returns the id length, 0 if none. */
static int buildId(const unsigned char **id)
{
    const Elf64_Ehdr *eh = (const Elf64_Ehdr *)(const void *)__ehdr_start;
    const Elf64_Phdr *ph = (const Elf64_Phdr *)(const void *)((const unsigned char *)eh + eh->e_phoff);
    uintptr_t bias = 0;
    for (int i = 0; i < eh->e_phnum; i++) {
        if (ph[i].p_type == PT_LOAD && ph[i].p_offset == 0) {
            bias = (uintptr_t)__ehdr_start - (uintptr_t)ph[i].p_vaddr;
            break;
        }
    }
    for (int i = 0; i < eh->e_phnum; i++) {
        if (ph[i].p_type != PT_NOTE) continue;
        const unsigned char *n = (const unsigned char *)(bias + (uintptr_t)ph[i].p_vaddr);
        const unsigned char *end = n + ph[i].p_memsz;
        while (n + sizeof(Elf64_Nhdr) <= end) {
            const Elf64_Nhdr *nh = (const Elf64_Nhdr *)(const void *)n;
            const unsigned char *name = n + sizeof(*nh);
            const unsigned char *desc = name + ((nh->n_namesz + 3) & ~3u);
            if (nh->n_type == NT_GNU_BUILD_ID && nh->n_namesz == 4 && memcmp(name, "GNU", 4) == 0) {
                *id = desc;
                return (int)nh->n_descsz;
            }
            n = desc + ((nh->n_descsz + 3) & ~3u);
        }
    }
    return 0;
}

static uint64_t fingerprint(void)
{
    Region r[3];
    uint64_t h = 1469598103934665603ull;
    regions(r);
    const uint64_t v[] = {
        SS_VERSION, (uint64_t)r[0].n, (uint64_t)r[1].n, (uint64_t)r[2].n,
        (uint64_t)((uintptr_t)r[0].p - imageStart()),
        (uint64_t)((uintptr_t)r[1].p - imageStart()),
        (uint64_t)imageSize(),
    };
    for (size_t i = 0; i < sizeof(v) / sizeof(v[0]); i++) {
        for (int b = 0; b < 8; b++) {
            h ^= (v[i] >> (b * 8)) & 0xff;
            h *= 1099511628211ull;
        }
    }
    for (const char *s = GE007_VERSION_HASH; *s; s++) {
        h ^= (unsigned char)*s;
        h *= 1099511628211ull;
    }
    const unsigned char *id = NULL;
    int idLen = buildId(&id);
    for (int i = 0; i < idLen; i++) {
        h ^= id[i];
        h *= 1099511628211ull;
    }
    return h;
}

/* ------------------------------------------------------- kernel objects */

typedef struct KeepList {
    const void *p[256];
    size_t n[256];
    unsigned char *copy[256];
    int count;
} KeepList;

static int inAnyRegion(const void *p, size_t n)
{
    Region r[3];
    regions(r);
    for (int i = 0; i < 3; i++) {
        if ((const unsigned char *)p >= r[i].p && (const unsigned char *)p + n <= r[i].p + r[i].n) {
            return 1;
        }
    }
    return 0;
}

static void keepAdd(const void *p, size_t n, void *ctx)
{
    KeepList *k = (KeepList *)ctx;
    if (k->count >= 256 || !inAnyRegion(p, n)) {
        return;
    }
    k->p[k->count] = p;
    k->n[k->count] = n;
    k->copy[k->count] = (unsigned char *)malloc(n);
    if (k->copy[k->count]) {
        memcpy(k->copy[k->count], p, n);
        k->count++;
    }
}

static void keepRestore(KeepList *k)
{
    for (int i = 0; i < k->count; i++) {
        memcpy((void *)k->p[i], k->copy[i], k->n[i]);
        free(k->copy[i]);
    }
    k->count = 0;
}

/* ------------------------------------------------------------- status */

static SDL_SpinLock s_lock;
static char s_savePath[512], s_loadPath[512], s_autoPath[512];
static int s_saveReq, s_loadReq, s_saveFreezeMs = SS_FREEZE_MS;
static int s_autoPeriodS;              /* 0: no periodic automatic save */
static SDL_atomic_t s_busy;            /* capture pending or writer running */
static SDL_atomic_t s_lastResult;      /* 1 saved, 2 loaded, <0 error code */
static char s_lastMessage[160];

static void setResult(int code, const char *msg)
{
    SDL_AtomicLock(&s_lock);
    snprintf(s_lastMessage, sizeof(s_lastMessage), "%s", msg);
    SDL_AtomicUnlock(&s_lock);
    SDL_AtomicSet(&s_lastResult, code);
    sysLogPrintf(code > 0 ? LOG_INFO : LOG_WARNING, "savestate: %s", msg);
}

int savestateLastResult(char *msg, int msgLen)
{
    if (msg && msgLen > 0) {
        SDL_AtomicLock(&s_lock);
        snprintf(msg, (size_t)msgLen, "%s", s_lastMessage);
        SDL_AtomicUnlock(&s_lock);
    }
    return SDL_AtomicGet(&s_lastResult);
}

/* ------------------------------------------------------------- saving */

typedef struct SaveJob {
    char path[512];
    SsHeader hdr;
    unsigned char *payload;
} SaveJob;

static int canSaveNow(void)
{
    /* Not during the title screen's demos (ramrom replay of a recorded
     * mission): they run a solo stage with a player, and a periodic save
     * there once replaced the player's own resume state with Dam's demo. */
    if (get_is_ramrom_flag() || get_recording_ramrom_flag()) {
        return 0;
    }
    return g_StageNum != LEVELID_TITLE && g_StageNum > 0 && g_StageNum < LEVELID_SOLO_MAX &&
           g_MainStageNum < 0 && g_CurrentPlayer != NULL && getPlayerCount() == 1 &&
           (current_menu == MENU_RUN_STAGE || current_menu == MENU_INVALID);
}

static int writerThread(void *arg)
{
    SaveJob *job = (SaveJob *)arg;
    uLongf packedCap = compressBound((uLong)job->hdr.payloadSize);
    unsigned char *packed = (unsigned char *)malloc(packedCap);
    char tmp[530];
    int ok = 0;

    if (packed && compress2(packed, &packedCap, job->payload, (uLong)job->hdr.payloadSize, 1) == Z_OK) {
        job->hdr.packedSize = packedCap;
        snprintf(tmp, sizeof(tmp), "%s.part", job->path);
        FILE *f = fopen(tmp, "wb");
        if (f) {
            ok = fwrite(&job->hdr, sizeof(job->hdr), 1, f) == 1 &&
                 fwrite(packed, 1, packedCap, f) == packedCap;
            ok = (fclose(f) == 0) && ok;
            ok = ok && rename(tmp, job->path) == 0;
            if (!ok) remove(tmp);
        }
    }
    if (ok) {
        char m[160];
        snprintf(m, sizeof(m), "saved stage %d (%lu KB)", (int)job->hdr.stage,
                 (unsigned long)(job->hdr.packedSize / 1024));
        setResult(1, m);
    } else {
        setResult(-2, "could not write the state file");
    }
    free(packed);
    free(job->payload);
    free(job);
    SDL_AtomicSet(&s_busy, 0);
    return 0;
}

/* Values >= 4 GiB that point into a mapping that is neither the N64 window
 * nor libmain.so would not survive a restart: log them (once per save). */
static void diagnoseForeignPointers(const unsigned char *payload, size_t n)
{
    static int done = 0;
    if (done) return;
    done = 1;
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return;
    enum { MAXM = 4096 };
    static uintptr_t lo[MAXM], hi[MAXM];
    int m = 0;
    char line[512];
    while (m < MAXM && fgets(line, sizeof(line), maps)) {
        unsigned long a, b;
        if (sscanf(line, "%lx-%lx", &a, &b) == 2) {
            lo[m] = a;
            hi[m] = b;
            m++;
        }
    }
    fclose(maps);
    const uintptr_t wlo = (uintptr_t)PORT_ADDR_BASE, whi = wlo + (uintptr_t)PORT_ADDR_WINDOW;
    const uintptr_t ilo = imageStart(), ihi = ilo + imageSize();
    int found = 0;
    for (size_t off = 0; off + 8 <= n; off += 8) {
        uint64_t v;
        memcpy(&v, payload + off, 8);
        if (v < 0x100000000ull || (v >= wlo && v < whi) || (v >= ilo && v < ihi)) continue;
        for (int i = 0; i < m; i++) {
            if (v >= lo[i] && v < hi[i]) {
                if (found < 16) {
                    sysLogPrintf(LOG_WARNING, "savestate: foreign pointer 0x%llx at payload+0x%zx",
                                 (unsigned long long)v, off);
                }
                found++;
                break;
            }
        }
    }
    sysLogPrintf(found ? LOG_WARNING : LOG_INFO, "savestate: %d pointer(s) outside the window and image", found);
}

static void doSave(const char *path, int freezeMs)
{
    Region r[3];
    regions(r);
    size_t total = r[0].n + r[1].n + r[2].n;
    unsigned char *payload = (unsigned char *)malloc(total);
    SaveJob *job = (SaveJob *)malloc(sizeof(SaveJob));
    if (!payload || !job) {
        free(payload);
        free(job);
        setResult(-3, "out of memory");
        SDL_AtomicSet(&s_busy, 0);
        return;
    }
    if (!portFreezeGameThreads(freezeMs)) {
        free(payload);
        free(job);
        setResult(-4, "the game did not pause in time; try again");
        SDL_AtomicSet(&s_busy, 0);
        return;
    }
    size_t off = 0;
    for (int i = 0; i < 3; i++) {
        memcpy(payload + off, r[i].p, r[i].n);
        off += r[i].n;
    }
    memset(&job->hdr, 0, sizeof(job->hdr));
    job->hdr.stage = g_StageNum;
    job->hdr.players = getPlayerCount();
    job->hdr.difficulty = lvlGetSelectedDifficulty();
    portThawGameThreads();

    memcpy(job->hdr.magic, SS_MAGIC, 8);
    job->hdr.version = SS_VERSION;
    job->hdr.headerSize = sizeof(SsHeader);
    job->hdr.fingerprint = fingerprint();
    job->hdr.imageBase = imageStart();
    job->hdr.imageSize = imageSize();
    job->hdr.dataSize = r[0].n;
    job->hdr.bssSize = r[1].n;
    job->hdr.arenaSize = r[2].n;
    job->hdr.payloadSize = total;
    job->hdr.payloadCrc = (uint32_t)crc32(0L, payload, (uInt)total);
    job->hdr.savedAt = (int64_t)time(NULL);
    snprintf(job->path, sizeof(job->path), "%s", path);
    job->payload = payload;
    diagnoseForeignPointers(payload, total);

    SDL_Thread *t = SDL_CreateThread(writerThread, "ge-savestate", job);
    if (t) {
        SDL_DetachThread(t);
    } else {
        writerThread(job);
    }
}

/* ------------------------------------------------------------ loading */

enum { LOAD_IDLE, LOAD_WAIT_STAGE };
static int s_loadStep = LOAD_IDLE;
static unsigned char *s_loadPayload;
static SsHeader s_loadHdr;
static int s_loadStageCount;
static int s_loadWaitTicks;
static int s_revalidateSaves;

static int readState(const char *path, SsHeader *hdr, unsigned char **payloadOut)
{
    FILE *f = fopen(path, "rb");
    if (!f) return -10;
    int rc = -11;
    unsigned char *packed = NULL, *payload = NULL;
    Region r[3];
    regions(r);
    if (fread(hdr, sizeof(*hdr), 1, f) != 1 || memcmp(hdr->magic, SS_MAGIC, 8) != 0 ||
        hdr->version != SS_VERSION || hdr->headerSize != sizeof(SsHeader)) {
        goto out;
    }
    rc = -12;   /* another build */
    if (hdr->fingerprint != fingerprint() || hdr->dataSize != r[0].n || hdr->bssSize != r[1].n ||
        hdr->arenaSize != r[2].n || hdr->payloadSize != r[0].n + r[1].n + r[2].n) {
        goto out;
    }
    rc = -13;
    packed = (unsigned char *)malloc((size_t)hdr->packedSize);
    payload = (unsigned char *)malloc((size_t)hdr->payloadSize);
    if (!packed || !payload || fread(packed, 1, (size_t)hdr->packedSize, f) != hdr->packedSize) {
        goto out;
    }
    uLongf len = (uLongf)hdr->payloadSize;
    if (uncompress(payload, &len, packed, (uLong)hdr->packedSize) != Z_OK || len != hdr->payloadSize ||
        (uint32_t)crc32(0L, payload, (uInt)len) != hdr->payloadCrc) {
        goto out;
    }
    *payloadOut = payload;
    payload = NULL;
    rc = 0;
out:
    fclose(f);
    free(packed);
    free(payload);
    return rc;
}

/* Overwrite game memory with the payload (threads frozen by the caller). */
static void restoreMemory(const SsHeader *hdr, const unsigned char *payload)
{
    static KeepList keep;
    Region r[3];
    regions(r);
    keep.count = 0;
    portForEachKernelObject(keepAdd, &keep);
    for (int i = 0; i < keep.count; i++) {
        sysLogPrintf(LOG_NOTE, "savestate: keep %p (%zu bytes)%s", keep.p[i], keep.n[i],
                     (const unsigned char *)keep.p[i] >= r[2].p ? " [arena]" : "");
    }

    size_t off = 0;
    for (int i = 0; i < 3; i++) {
        memcpy(r[i].p, payload + off, r[i].n);
        off += r[i].n;
    }

    /* Native pointers into the saving process's image -> this image. */
    const uint64_t oldLo = hdr->imageBase, oldHi = hdr->imageBase + hdr->imageSize;
    const int64_t delta = (int64_t)imageStart() - (int64_t)hdr->imageBase;
    int moved = 0;
    if (delta != 0) {
        /* Heuristic: data that looks like an image pointer moves too (the
         * ALEvent padding case, port/android/loader/ge_fixedload.c). Android
         * avoids it by loading libmain.so at a fixed address. */
        sysLogPrintf(LOG_WARNING, "savestate: the image moved since the save, shifting pointers");
        /* Step 4 bytes, not 8: setup records in the arena are packed at
         * 4-byte boundaries, so a pointer field that is 8-aligned within its
         * struct can sit at addr % 8 == 4 (seen: ObjectRecord.embedment).
         * After a hit, skip the pointer's 8 bytes. */
        for (int i = 0; i < 3; i++) {
            unsigned char *p = r[i].p;
            size_t n = r[i].n;
            for (size_t off = 0; off + 8 <= n; ) {
                uint64_t v;
                memcpy(&v, p + off, 8);
                if (v >= oldLo && v < oldHi) {
                    v = (uint64_t)((int64_t)v + delta);
                    memcpy(p + off, &v, 8);
                    moved++;
                    off += 8;
                } else {
                    off += 4;
                }
            }
        }
    }
    keepRestore(&keep);
    sysLogPrintf(LOG_INFO, "savestate: restored %zu KB, %d image pointer(s) moved by %lld",
                 (r[0].n + r[1].n + r[2].n) / 1024, moved, (long long)delta);
}

static void loadFinish(int code, const char *msg)
{
    free(s_loadPayload);
    s_loadPayload = NULL;
    s_loadStep = LOAD_IDLE;
    setResult(code, msg);
    SDL_AtomicSet(&s_busy, 0);
}

static void loadBegin(const char *path)
{
    int rc = readState(path, &s_loadHdr, &s_loadPayload);
    if (rc != 0) {
        loadFinish(rc, rc == -10 ? "no state in this slot" :
                       rc == -12 ? "this state was saved by another version" :
                                   "the state file is damaged");
        return;
    }
    if (s_loadHdr.players != 1 || s_loadHdr.stage <= 0 || s_loadHdr.stage >= LEVELID_SOLO_MAX) {
        loadFinish(-14, "unsupported state");
        return;
    }
    /* Step 1: have the game load the saved stage the normal way (the
     * "-level_NN" boot path does the same three calls). */
    set_selected_difficulty(s_loadHdr.difficulty);
    lvlSetSelectedDifficulty(s_loadHdr.difficulty);
    set_solo_and_ptr_briefing(s_loadHdr.stage);
    s_loadStageCount = videoStageLoadCount();
    s_loadWaitTicks = 0;
    g_MainStageNum = s_loadHdr.stage;
    s_loadStep = LOAD_WAIT_STAGE;
    sysLogPrintf(LOG_INFO, "savestate: loading stage %d for the state", (int)s_loadHdr.stage);
}

static void loadStep2(void)
{
    if (!portFreezeGameThreads(SS_FREEZE_MS)) {
        return;   /* try again next frame */
    }
    restoreMemory(&s_loadHdr, s_loadPayload);
    videoResetTextureCache();   /* same call, same conditions as a stage load */
    portThawGameThreads();
    s_revalidateSaves = 1;
    loadFinish(2, "state loaded");
}

/* --------------------------------------------------------------- API */

/* freezeMs: how long the game may stay frozen waiting for its threads to
 * reach their loop tops (a few ms normally; longer while Android takes the
 * app off screen, when the audio thread can be held up). */
void savestateRequestSave(const char *path, int freezeMs)
{
    SDL_AtomicLock(&s_lock);
    snprintf(s_savePath, sizeof(s_savePath), "%s", path);
    s_saveReq = 1;
    s_saveFreezeMs = freezeMs > 0 ? freezeMs : SS_FREEZE_MS;
    SDL_AtomicUnlock(&s_lock);
    SDL_AtomicSet(&s_busy, 1);
}

/* Periodic automatic save during solo missions (0 s turns it off). */
void savestateSetAuto(const char *path, int periodS)
{
    SDL_AtomicLock(&s_lock);
    snprintf(s_autoPath, sizeof(s_autoPath), "%s", path ? path : "");
    s_autoPeriodS = periodS;
    SDL_AtomicUnlock(&s_lock);
}

void savestateRequestLoad(const char *path)
{
    SDL_AtomicLock(&s_lock);
    snprintf(s_loadPath, sizeof(s_loadPath), "%s", path);
    s_loadReq = 1;
    SDL_AtomicUnlock(&s_lock);
    SDL_AtomicSet(&s_busy, 1);
}

int savestateBusy(void)
{
    return SDL_AtomicGet(&s_busy);
}

int savestateCanSave(void)
{
    return s_loadStep == LOAD_IDLE && canSaveNow();
}

/* Header of a state file, for the slot list: 1 loadable here, 0 written by
 * another build, -1 none / unreadable. */
int savestatePeek(const char *path, int *stage, int *difficulty, long long *savedAt)
{
    SsHeader h;
    FILE *f = fopen(path, "rb");
    if (!f) return -1;
    int ok = fread(&h, sizeof(h), 1, f) == 1 && memcmp(h.magic, SS_MAGIC, 8) == 0;
    fclose(f);
    if (!ok) return -1;
    if (stage) *stage = h.stage;
    if (difficulty) *difficulty = h.difficulty;
    if (savedAt) *savedAt = h.savedAt;
    return (h.version == SS_VERSION && h.fingerprint == fingerprint()) ? 1 : 0;
}

/* Test hooks (headless runs, adb launches): --ss-save <file> saves at the
 * first possible tick after --ss-save-tick (default 900 ticks, ~7 s), and
 * --ss-load <file> loads at --ss-load-tick (default 120). A tick is one
 * gfxFrameMsgQ message (a retrace or a frame-done). */
static void testHooks(void)
{
    static int tick = 0, saveArmed = -1, loadDone = 0;
    tick++;
    if (saveArmed < 0) {
        saveArmed = sysArgGetString("--ss-save") != NULL;
    }
    if (saveArmed == 1) {
        const char *t = sysArgGetString("--ss-save-tick");
        if (tick >= (t ? atoi(t) : 900) && canSaveNow() && s_loadStep == LOAD_IDLE && !savestateBusy()) {
            saveArmed = 2;
            savestateRequestSave(sysArgGetString("--ss-save"), SS_FREEZE_MS);
        }
    }
    if (!loadDone && sysArgGetString("--ss-load")) {
        const char *t = sysArgGetString("--ss-load-tick");
        if (tick >= (t ? atoi(t) : 120)) {
            loadDone = 1;
            savestateRequestLoad(sysArgGetString("--ss-load"));
        }
    }
}

/* Main game thread, once per gfxFrameMsgQ message (libultra.c). */
void savestateGameTick(void)
{
    char path[512];
    int save = 0, load = 0;

    testHooks();

    if (s_revalidateSaves) {
        /* After a load: the Bond files come from the EEPROM, not the state. */
        s_revalidateSaves = 0;
        fileValidateSaves();
    }

    if (s_loadStep == LOAD_WAIT_STAGE) {
        if (videoStageLoadCount() != s_loadStageCount && g_MainStageNum < 0 &&
            g_StageNum == s_loadHdr.stage) {
            loadStep2();
        } else if (++s_loadWaitTicks > 60 * 30) {
            loadFinish(-15, "the stage did not load");
        }
        return;
    }

    /* Periodic automatic save: a short freeze, skipped (and retried a few
     * seconds later) rather than stalling the game when a thread is busy. */
    static uint64_t nextAutoUs = 0;
    int autoSave = 0, freezeMs = SS_FREEZE_MS;
    uint64_t nowUs = sysGetMicroseconds();

    SDL_AtomicLock(&s_lock);
    if (s_loadReq) {
        load = 1;
        s_loadReq = 0;
        snprintf(path, sizeof(path), "%s", s_loadPath);
    } else if (s_saveReq) {
        save = 1;
        s_saveReq = 0;
        freezeMs = s_saveFreezeMs;
        snprintf(path, sizeof(path), "%s", s_savePath);
    } else if (s_autoPeriodS > 0 && s_autoPath[0] && canSaveNow() && !savestateBusy()) {
        if (nextAutoUs == 0) {
            nextAutoUs = nowUs + (uint64_t)s_autoPeriodS * 1000000u;
        } else if (nowUs >= nextAutoUs) {
            autoSave = 1;
            freezeMs = 60;
            snprintf(path, sizeof(path), "%s", s_autoPath);
        }
    }
    SDL_AtomicUnlock(&s_lock);

    if (autoSave) {
        SDL_AtomicSet(&s_busy, 1);
        doSave(path, freezeMs);
        /* Saved: next one a full period later; frozen too long: retry soon. */
        nextAutoUs = nowUs + (SDL_AtomicGet(&s_lastResult) == -4 ? 5000000u
                                                                 : (uint64_t)s_autoPeriodS * 1000000u);
        return;
    }

    if (load) {
        loadBegin(path);
    } else if (save) {
        if (!canSaveNow()) {
            setResult(-1, "nothing to save here (only during a solo mission)");
            SDL_AtomicSet(&s_busy, 0);
            return;
        }
        doSave(path, freezeMs);
    }
}
