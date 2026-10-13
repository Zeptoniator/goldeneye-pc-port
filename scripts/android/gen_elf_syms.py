#!/usr/bin/env python3
"""Rewrite the port's absolute-symbol GAS files for the Android build.

Android loads the game as a shared library, so the N64 address window is
shifted to PORT_ADDR_BASE (see port/include/portaddr.h and CMakeLists.txt).
This is the ELF counterpart of scripts/gen_macho_syms.py:

  * `--base N` adds N to the first numeric literal of every `.set`
    (romassets_<r>.s carries bare cart addresses, 0x10000000 + offset);
  * C integer suffixes are stripped (`0x70000000UL` -> `0x70000000`), so a
    `cc -E`-expanded dram_syms.S assembles;
  * every `.global X` is followed by `.hidden X`, so lld resolves the GOT
    slots of these SHN_ABS symbols at link time instead of leaving a dynamic
    relocation that bionic would offset by the load bias.

usage: gen_elf_syms.py [--base N] INPUT OUTPUT
"""
import pathlib
import re
import sys

SUFFIX = re.compile(r"\b(0[xX][0-9a-fA-F]+|\d+)(?:[uU][lL]{1,2}|[lL]{1,2}[uU]?|[uU])\b")


def main() -> int:
    argv = sys.argv[1:]
    base = 0
    if argv and argv[0] == "--base":
        if len(argv) < 2:
            print(__doc__, file=sys.stderr)
            return 2
        base = int(argv[1], 0)
        argv = argv[2:]
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    source, output = pathlib.Path(argv[0]), pathlib.Path(argv[1])
    out = []
    n_set = 0
    for line in source.read_text(encoding="utf-8").splitlines():
        if line.startswith("#"):
            continue  # cc -E line markers
        m = re.match(r"^(\s*)\.global\s+([A-Za-z_]\w*)\s*$", line)
        if m:
            out.append(line)
            out.append(f"{m.group(1)}.hidden {m.group(2)}")
            continue
        m = re.match(r"^(\s*)\.set\s+([A-Za-z_]\w*)(\s*,)(.*)$", line)
        if m:
            expr = SUFFIX.sub(r"\1", m.group(4))
            if base:
                expr = re.sub(r"(0[xX][0-9a-fA-F]+|\d+)",
                              lambda v: f"0x{int(v.group(1), 0) + base:X}",
                              expr, count=1)
            out.append(f"{m.group(1)}.set {m.group(2)}{m.group(3)}{expr}")
            n_set += 1
            continue
        out.append(line)
    if n_set == 0:
        print(f"gen_elf_syms: no .set lines in {source}", file=sys.stderr)
        return 1
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(out) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
