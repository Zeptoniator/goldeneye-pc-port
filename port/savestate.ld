/*
 * savestate.ld -- gather the game's own globals for port/src/savestate.c.
 *
 * The writable data of the translation units that hold game state go into
 * two output sections bracketed by symbols, so a savestate can copy and
 * restore them as two blocks:
 *   .ge_state_data  (after .data): src/game, the engine files with state,
 *                   libaudio (incl. the generated Android copies of lv.c and
 *                   spectrum.c);
 *   .ge_state_bss   (after .bss, NOLOAD): the same files' zero-initialised
 *                   data.
 * Left out on purpose: init.c and sched.c (threads, queues, the scheduler),
 * joy.c (controller polling state of this process), stacks.c (unused stack
 * arrays), the port layer, fast3d and the assets (constant).
 * Used with -T: a script made of INSERT commands only adds to the linker's
 * default layout (GNU ld and lld).
 */
SECTIONS
{
  .ge_state_data : ALIGN(64)
  {
    __ge_state_data_start = .;
    *src/game/*.o(.data .data.*)
    *port/src/android_lv.c.o(.data .data.*)
    *port/src/android_spectrum.c.o(.data .data.*)
    *src/boss.c.o(.data .data.*)
    *src/mema.c.o(.data .data.*)
    *src/memp.c.o(.data .data.*)
    *src/snd.c.o(.data .data.*)
    *src/music.c.o(.data .data.*)
    *src/audi.c.o(.data .data.*)
    *src/fr.c.o(.data .data.*)
    *src/deb.c.o(.data .data.*)
    *src/debugmenu.c.o(.data .data.*)
    *src/speed_graph.c.o(.data .data.*)
    *src/token.c.o(.data .data.*)
    *src/str.c.o(.data .data.*)
    *src/ramrom.c.o(.data .data.*)
    *src/libultra/audio/*.o(.data .data.*)
    *src/libultrare/audio/*.o(.data .data.*)
    . = ALIGN(64);
    __ge_state_data_end = .;
  }
}
INSERT AFTER .data;

SECTIONS
{
  .ge_state_bss (NOLOAD) : ALIGN(64)
  {
    __ge_state_bss_start = .;
    *src/game/*.o(.bss .bss.* COMMON)
    *port/src/android_lv.c.o(.bss .bss.* COMMON)
    *port/src/android_spectrum.c.o(.bss .bss.* COMMON)
    *src/boss.c.o(.bss .bss.* COMMON)
    *src/mema.c.o(.bss .bss.* COMMON)
    *src/memp.c.o(.bss .bss.* COMMON)
    *src/snd.c.o(.bss .bss.* COMMON)
    *src/music.c.o(.bss .bss.* COMMON)
    *src/audi.c.o(.bss .bss.* COMMON)
    *src/fr.c.o(.bss .bss.* COMMON)
    *src/deb.c.o(.bss .bss.* COMMON)
    *src/debugmenu.c.o(.bss .bss.* COMMON)
    *src/speed_graph.c.o(.bss .bss.* COMMON)
    *src/token.c.o(.bss .bss.* COMMON)
    *src/str.c.o(.bss .bss.* COMMON)
    *src/ramrom.c.o(.bss .bss.* COMMON)
    *src/libultra/audio/*.o(.bss .bss.* COMMON)
    *src/libultrare/audio/*.o(.bss .bss.* COMMON)
    . = ALIGN(64);
    __ge_state_bss_end = .;
  }
}
INSERT AFTER .bss;
