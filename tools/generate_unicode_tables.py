#!/usr/bin/env python3
"""Generates UnicodeTables.kt by probing the installed RapidFuzz backend.

kmatch's parity target is RapidFuzz's default (C++) backend -- the same backend
that generates the golden vectors. Its Unicode classification differs from both
Python's `re` module and Java's Character class (e.g. underscore is non-word,
lowercasing is simple per-code-point mapping, NBSP is not a token separator),
so instead of trusting any host runtime's tables we derive three tables from
the backend's observable behavior, one code point at a time:

  * word(cp):   default_process(chr(cp)) != ""   (kept vs. replaced by space)
  * lower(cp):  default_process(chr(cp))         (for word code points)
  * space(cp):  token_sort_ratio("b"+chr(cp)+"a", "a b") == 100
                (only a token separator can make those equal)

Pin: must run against the exact RapidFuzz version in PIN below.
Usage: python3 tools/generate_unicode_tables.py
"""

from __future__ import annotations

import sys
from pathlib import Path

PIN = "3.14.5"
OUT = Path(__file__).resolve().parent.parent / (
    "src/commonMain/kotlin/io/github/likhithsj/kmatch/UnicodeTables.kt"
)
MAX_CP = 0x110000


def main() -> None:
    import rapidfuzz
    from rapidfuzz import fuzz, utils

    if rapidfuzz.__version__ != PIN:
        sys.exit(
            f"rapidfuzz {rapidfuzz.__version__} installed but {PIN} required; "
            f"run: pip install rapidfuzz=={PIN}"
        )

    word_ranges: list[list[int]] = []  # inclusive [start, end] runs of word cps
    lower_keys: list[int] = []  # word cps whose lowercase differs from itself
    lower_vals: list[int] = []
    spaces: list[int] = []

    default_process = utils.default_process
    token_sort_ratio = fuzz.token_sort_ratio

    for cp in range(MAX_CP):
        ch = chr(cp)
        try:
            processed = default_process(ch)
        except Exception:
            processed = ""
        if processed:
            cps = [ord(c) for c in processed]
            assert len(cps) == 1, (
                f"U+{cp:04X}: multi-code-point lowercase {cps}; "
                "table format assumes simple per-code-point mapping"
            )
            if word_ranges and word_ranges[-1][1] == cp - 1:
                word_ranges[-1][1] = cp
            else:
                word_ranges.append([cp, cp])
            if cps[0] != cp:
                lower_keys.append(cp)
                lower_vals.append(cps[0])
        else:
            try:
                if token_sort_ratio(f"b{ch}a", "a b") == 100.0:
                    spaces.append(cp)
            except Exception:
                pass

    flat_ranges = [v for r in word_ranges for v in r]
    print(
        f"word ranges: {len(word_ranges)}, lower deltas: {len(lower_keys)}, "
        f"whitespace: {len(spaces)}"
    )

    OUT.write_text(render(flat_ranges, lower_keys, lower_vals, spaces))
    print(f"wrote {OUT}")


def chunked_int_array(name: str, values: list[int], chunk: int = 1000) -> str:
    """Emits `name` as an IntArray filled by chunked functions, keeping every
    generated JVM method under the 64KB bytecode limit."""
    parts = [
        f"private fun {name}Chunk{i}(a: IntArray, o: Int) {{\n"
        + "".join(
            f"    a[o + {j}] = {v}\n" for j, v in enumerate(values[i : i + chunk])
        )
        + "}\n"
        for i in range(0, len(values), chunk)
    ]
    fills = "".join(
        f"    {name}Chunk{i}(a, {i})\n" for i in range(0, len(values), chunk)
    )
    return (
        "".join(parts)
        + f"internal val {name}: IntArray = IntArray({len(values)}).also {{ a ->\n{fills}}}\n"
    )


def render(
    flat_ranges: list[int],
    lower_keys: list[int],
    lower_vals: list[int],
    spaces: list[int],
) -> str:
    header = f"""\
// GENERATED FILE -- DO NOT EDIT.
// Produced by tools/generate_unicode_tables.py against rapidfuzz=={PIN}.
// Encodes the Unicode classification of RapidFuzz's C++ backend:
//   WORD_RANGES: inclusive [start, end] pairs of "word" code points (kept by
//                defaultProcess); everything else is replaced by a space.
//   LOWER_KEYS/LOWER_VALS: simple per-code-point lowercase mapping, delta-only.
//   WHITESPACE: code points the tokenizer treats as token separators.
package io.github.likhithsj.kmatch

"""
    body = (
        chunked_int_array("WORD_RANGES", flat_ranges)
        + "\n"
        + chunked_int_array("LOWER_KEYS", lower_keys)
        + "\n"
        + chunked_int_array("LOWER_VALS", lower_vals)
        + "\n"
        + "internal val WHITESPACE: IntArray = intArrayOf(\n    "
        + ", ".join(str(v) for v in spaces)
        + "\n)\n"
    )
    footer = """
/** True if RapidFuzz's defaultProcess keeps this code point. */
internal fun isWordCodePoint(cp: Int): Boolean {
    var lo = 0
    var hi = WORD_RANGES.size / 2 - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        when {
            cp < WORD_RANGES[2 * mid] -> hi = mid - 1
            cp > WORD_RANGES[2 * mid + 1] -> lo = mid + 1
            else -> return true
        }
    }
    return false
}

/** Simple per-code-point lowercase mapping used by defaultProcess. */
internal fun lowerCodePoint(cp: Int): Int {
    var lo = 0
    var hi = LOWER_KEYS.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        when {
            cp < LOWER_KEYS[mid] -> hi = mid - 1
            cp > LOWER_KEYS[mid] -> lo = mid + 1
            else -> return LOWER_VALS[mid]
        }
    }
    return cp
}

/** True if the tokenizer treats this code point as a token separator. */
internal fun isSpaceCodePoint(cp: Int): Boolean {
    var lo = 0
    var hi = WHITESPACE.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        when {
            cp < WHITESPACE[mid] -> hi = mid - 1
            cp > WHITESPACE[mid] -> lo = mid + 1
            else -> return true
        }
    }
    return false
}
"""
    return header + body + footer


if __name__ == "__main__":
    main()
