#!/usr/bin/env python3
"""Copy a decomp C file, turning `#pragma weak ALIAS = TARGET` into a macro.

clang implements `#pragma weak A = B` by cloning B's declaration under the
name A at file scope. When the file already declared `extern T A;` (lv.c,
spectrum.c), every later use of A becomes "ambiguous" -- a hard error. GCC
merges the two declarations instead. Both aliases are only used inside the
file that declares them, so the copy replaces each pragma with

    #define A (*(__typeof__(A) *)&(B))

which names the same storage with A's declared type (the pre-existing
`extern` declaration of A is what `__typeof__` sees: a macro does not expand
inside its own definition). Game logic is unchanged; the original file is
untouched (the Android build compiles the copy, like the Mach-O build's
scripts/strip_weak_pragmas.py).

usage: weak_pragmas_to_macros.py INPUT OUTPUT
"""
import pathlib
import re
import sys

PRAGMA = re.compile(r"^[ \t]*#pragma[ \t]+weak[ \t]+(\w+)[ \t]*=[ \t]*(\w+)[ \t]*$", re.M)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    src, dst = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    text = src.read_text(encoding="utf-8")
    text, n = PRAGMA.subn(lambda m: f"#define {m.group(1)} (*(__typeof__({m.group(1)}) *)&({m.group(2)}))", text)
    if n == 0:
        print(f"weak_pragmas_to_macros: no '#pragma weak A = B' in {src}", file=sys.stderr)
        return 1
    # Keep diagnostics and __FILE__ pointing at the real source.
    text = f'#line 1 "{src.as_posix()}"\n' + text
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
