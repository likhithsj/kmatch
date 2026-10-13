#!/usr/bin/env python3
"""Generates FoldTables.kt: the table behind stripAccents / searchFold.

Unlike UnicodeTables.kt this is NOT a RapidFuzz-parity table -- RapidFuzz
never folds accents. It encodes kmatch's own opt-in search fold:

  fold(cp) = NFKD(cp), dropping marks from scripts where marks are optional
             vocalization, then mapping non-decomposable Latin letters.

Dropped mark ranges (kept conservative on purpose -- Indic and Southeast
Asian marks ARE vowels and are left intact):
  U+0300..U+036F  combining diacritical marks (Latin/Greek/Cyrillic accents)
  U+0591..U+05C7  Hebrew points and cantillation
  U+064B..U+065F, U+0670  Arabic harakat

Non-decomposable Latin letters folded explicitly: o/O for ø/Ø, ae/AE, oe/OE,
ss for ß, l/L for ł/Ł, d/D for đ/Đ and ð/Ð, th/TH for þ/Þ, h/H, t/T, i for ı.

Hangul syllables (U+AC00..U+D7A3) are deliberately NOT decomposed to jamo:
the fold targets accent/width/ligature noise, not script decomposition.

Pin: Python 3.12 (Unicode 15.0.0), the Python version CI uses. The generator
aborts on a different unicodedata version so the committed file stays
reproducible.
Usage: python3.12 tools/generate_fold_tables.py
"""

from __future__ import annotations

import sys
import unicodedata
from pathlib import Path

UNIDATA_PIN = "15.0.0"
OUT = Path(__file__).resolve().parent.parent / (
    "src/commonMain/kotlin/io/github/likhithsj/kmatch/FoldTables.kt"
)
MAX_CP = 0x110000

STRIP = (
    set(range(0x0300, 0x0370))
    | set(range(0x0591, 0x05C8))
    | set(range(0x064B, 0x0660))
    | {0x0670}
)

EXTRA = {
    "ø": "o", "Ø": "O",            # ø Ø
    "æ": "ae", "Æ": "AE",          # æ Æ
    "œ": "oe", "Œ": "OE",          # œ Œ
    "ß": "ss",                           # ß
    "ł": "l", "Ł": "L",            # ł Ł
    "đ": "d", "Đ": "D",            # đ Đ
    "ð": "d", "Ð": "D",            # ð Ð
    "þ": "th", "Þ": "TH",          # þ Þ
    "ħ": "h", "Ħ": "H",            # ħ Ħ
    "ŧ": "t", "Ŧ": "T",            # ŧ Ŧ
    "ı": "i",                            # ı
}


def fold(cp: int) -> str:
    s = chr(cp)
    out: list[str] = []
    for ch in unicodedata.normalize("NFKD", s):
        if ord(ch) in STRIP:
            continue
        out.append(EXTRA.get(ch, ch))
    return "".join(out)


def kotlin_escape(s: str) -> str:
    parts: list[str] = []
    for ch in s:
        o = ord(ch)
        if o >= 0x10000:
            o -= 0x10000
            parts.append("\\u%04X\\u%04X" % (0xD800 + (o >> 10), 0xDC00 + (o & 0x3FF)))
        elif 0x20 <= o < 0x7F and ch not in ('"', "\\", "$"):
            parts.append(ch)
        else:
            parts.append("\\u%04X" % o)
    return "".join(parts)


def main() -> None:
    if unicodedata.unidata_version != UNIDATA_PIN:
        sys.exit(
            f"unicodedata {unicodedata.unidata_version} but {UNIDATA_PIN} required "
            f"(run with Python 3.12)"
        )

    entries: list[tuple[int, str]] = []
    for cp in range(MAX_CP):
        if 0xD800 <= cp <= 0xDFFF:          # surrogates
            continue
        if 0xAC00 <= cp <= 0xD7A3:          # Hangul syllables: kept composed
            continue
        f = fold(cp)
        if f != chr(cp):
            entries.append((cp, f))

    n = len(entries)
    data = "".join(f for _, f in entries)

    # Offsets index into a Kotlin String, i.e. UTF-16 units, not code points:
    # astral fold values occupy two units each.
    def utf16len(s: str) -> int:
        return sum(2 if ord(ch) > 0xFFFF else 1 for ch in s)

    offsets = [0]
    for _, f in entries:
        offsets.append(offsets[-1] + utf16len(f))
    data_utf16 = offsets[-1]

    print(f"{n} entries, data length {data_utf16} UTF-16 units", file=sys.stderr)

    w: list[str] = []
    w.append("// GENERATED FILE -- DO NOT EDIT.")
    w.append(f"// Produced by tools/generate_fold_tables.py (Unicode {UNIDATA_PIN}).")
    w.append("// kmatch's own search fold -- NOT a RapidFuzz-parity table; see the")
    w.append("// generator header for the exact definition.")
    w.append("//   FOLD_KEYS:    sorted code points with a non-identity fold.")
    w.append("//   FOLD_OFFSETS: FOLD_DATA[off[i], off[i+1]) is the fold of FOLD_KEYS[i].")
    w.append("package io.github.likhithsj.kmatch")
    w.append("")

    CHUNK = 300
    key_chunks = (n + CHUNK - 1) // CHUNK
    for c in range(key_chunks):
        w.append(f"private fun foldKeysChunk{c}(a: IntArray, o: Int) {{")
        for j, (cp, _) in enumerate(entries[c * CHUNK:(c + 1) * CHUNK]):
            w.append(f"    a[o + {j}] = {cp}")
        w.append("}")
        w.append("")

    off_chunks = (n + 1 + CHUNK - 1) // CHUNK
    for c in range(off_chunks):
        w.append(f"private fun foldOffsetsChunk{c}(a: IntArray, o: Int) {{")
        for j, v in enumerate(offsets[c * CHUNK:(c + 1) * CHUNK]):
            w.append(f"    a[o + {j}] = {v}")
        w.append("}")
        w.append("")

    LIT = 800  # chars of source data per string literal, escapes keep it bounded
    data_chunks = [data[i:i + LIT] for i in range(0, len(data), LIT)] or [""]
    PER_FUN = 40
    data_funs = (len(data_chunks) + PER_FUN - 1) // PER_FUN
    for c in range(data_funs):
        w.append(f"private fun foldDataChunk{c}(sb: StringBuilder) {{")
        for piece in data_chunks[c * PER_FUN:(c + 1) * PER_FUN]:
            w.append(f'    sb.append("{kotlin_escape(piece)}")')
        w.append("}")
        w.append("")

    w.append(f"internal val FOLD_KEYS: IntArray = IntArray({n}).also {{ a ->")
    for c in range(key_chunks):
        w.append(f"    foldKeysChunk{c}(a, {c * CHUNK})")
    w.append("}")
    w.append("")
    w.append(f"internal val FOLD_OFFSETS: IntArray = IntArray({n + 1}).also {{ a ->")
    for c in range(off_chunks):
        w.append(f"    foldOffsetsChunk{c}(a, {c * CHUNK})")
    w.append("}")
    w.append("")
    w.append(f"internal val FOLD_DATA: String = StringBuilder({data_utf16}).also {{ sb ->")
    for c in range(data_funs):
        w.append(f"    foldDataChunk{c}(sb)")
    w.append("}.toString()")
    w.append("")

    OUT.write_text("\n".join(w) + "\n", encoding="utf-8")
    print(f"wrote {OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
