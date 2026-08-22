#!/usr/bin/env python3
"""Generates GoldenVectors.kt from a pinned RapidFuzz version.

Every (s1, s2) pair is scored by every Layer-1 scorer, both without a
processor and with utils.default_process, and the exact float64 results are
emitted as a Kotlin source file in commonTest. Generating source rather than a
JSON resource sidesteps resource-loading differences across Native targets.
The file is committed; CI regenerates it and fails on drift.

Usage: python3 tools/generate_vectors.py
"""

from __future__ import annotations

import sys
from pathlib import Path

PIN = "3.14.5"
OUT = Path(__file__).resolve().parent.parent / (
    "src/commonTest/kotlin/io/github/likhithsj/kmatch/GoldenVectors.kt"
)

# Coverage classes from the design doc: plain ASCII; accented Latin; astral
# plane; empty/single-character; > 64 code points (exercises the future block
# path); token edge cases; non-Latin scripts; length ratios that hit every
# WRatio branch (< 1.5, 1.5-8, > 8); equal lengths (partial_ratio second pass).
PAIRS: list[tuple[str, str]] = [
    # plain ASCII / fuzzywuzzy canon
    ("this is a test", "this is a test!"),
    ("fuzzy wuzzy was a bear", "wuzzy fuzzy was a bear"),
    ("fuzzy was a bear", "fuzzy fuzzy was a bear"),
    ("fuzzy was a bear but not a dog", "fuzzy was a bear"),
    ("fuzzy was a bear but not a dog", "fuzzy was a bear but not a cat"),
    ("myself", "me"),
    ("New York Mets", "New York Meats"),
    ("mariners vs angels", "los angeles angels of anaheim vs seattle mariners"),
    ("Sirhan, Sirhan", "Sirhan"),
    ("HELLO WORLD", "hello world"),
    ("a certain string", "cetain"),
    ("lewenstein", "levenshtein"),
    ("abcd", "dcba"),
    ("kitten", "sitting"),
    # empty / single character
    ("", ""),
    ("", "a"),
    ("a", ""),
    ("a", "a"),
    ("a", "b"),
    (" ", " "),
    (" ", "a"),
    ("!", "?"),
    # equal lengths (partial_ratio tries both directions)
    ("abcdefg", "gfedcba"),
    ("square", "sqrare"),
    ("stress", "tresss"),
    # accented Latin
    ("café", "cafe"),
    ("Café Crème", "cafe creme"),
    ("Müller Straße", "Mueller Strasse"),
    ("naïve café münchen", "naive cafe munchen"),
    ("père Noël", "pere noel"),
    # astral plane: emoji, math alphanumerics, rare CJK
    ("😀😃😄", "😀😄😃"),
    ("hello 😀", "hello 😃"),
    ("🚀 launch", "launch 🚀"),
    ("𝕳𝖊𝖑𝖑𝖔 world", "hello world"),
    ("𠜎𠜱𠝹", "𠜎𠝹"),
    ("👨‍👩‍👧‍👦 family", "family 👨‍👩‍👧‍👦"),
    # > 64 code points
    (
        "the quick brown fox jumps over the lazy dog while the cat watches from the warm windowsill",
        "the quick brown fox jumped over the lazy dogs while a cat watched from the windowsill",
    ),
    (
        "pack my box with five dozen liquor jugs and then pack another box with six dozen wine bottles",
        "pack my box with five dozen liquor jugs",
    ),
    ("ab" * 40, "ba" * 40),
    ("x" * 70, "x" * 65 + "y" * 5),
    # token edge cases
    ("fuzzy fuzzy fuzzy", "fuzzy"),
    ("a---b!!! c???", "a b c"),
    ("a  b   c", "a b c"),
    ("a\tb\nc", "a b c"),
    ("  leading and trailing  ", "leading and trailing"),
    ("snake_case_name", "snake case name"),
    ("word", "word word word word"),
    ("a b", "b a"),
    ("a b c", "a b c"),  # NBSP is not a separator
    ("a b c", "a b c"),  # figure space is a separator
    ("a　b", "a b"),  # ideographic space
    # non-Latin scripts
    ("Москва Россия", "москва россия"),
    ("ΑΘΗΝΑ", "αθηνα"),
    ("ΟΔΥΣΣΕΥΣ", "οδυσσευς"),
    ("東京都渋谷区", "東京都渋谷"),
    ("こんにちは世界", "こんにちは"),
    ("안녕하세요 세계", "안녕하세요"),
    ("مرحبا بالعالم", "مرحبا"),
    ("שלום עולם", "שלום"),
    ("नमस्ते दुनिया", "नमस्ते"),
    ("İstanbul", "istanbul"),
    ("ısparta ISPARTA", "isparta"),
    ("straße", "STRASSE"),
    ("groß", "GROẞ"),
    # numeric / symbol word characters
    ("x²+y³", "x2 y3"),
    ("Ⅻ chapter", "xii chapter"),
    ("½ cup", "1/2 cup"),
    # WRatio length-ratio branches: 1.5 <= ratio <= 8 and ratio > 8
    ("cat", "the cat sat on the mat"),
    ("hello", "hello world this is a somewhat longer sentence"),
    ("ab", "the quick brown fox jumps over the lazy dog and keeps running"),
    ("x", "yyyyyyyyy"),
    # mixed script and emoji soup
    ("naïve 東京 😀 test", "naive 東京 😀 test!"),
    ("Grüße aus München 🍺", "gruesse aus muenchen"),
]

# Strings whose defaultProcess output is verified directly.
PROCESS_INPUTS: list[str] = [
    "",
    "   ",
    "Hello, World!",
    "  spaces  everywhere  ",
    "snake_case_name",
    "ΑΣ ΟΔΥΣΣΕΥΣ Σ",
    "İstanbul ISPARTA ı",
    "GROẞ straße",
    "x²+y³=z⁴",
    "Ⅻ Ⅶ ⅲ",
    "a b c　d",
    "😀 emoji! 🚀",
    "𝕳𝖊𝖑𝖑𝖔 𝕎𝕠𝕣𝕝𝕕",
    "東京都渋谷区1丁目",
    "Москва, Россия!",
    "مرحبا بالعالم",
    "a\tb\nc\rd",
    "١٢٣ ٤٥٦",  # Arabic-Indic digits
    "'quotes' \"and\" $dollars$ 100%",
]

# Cutoff coverage: scorers must honor score_cutoff through every early-exit
# and internal-chaining path. Includes cutoffs just under and over raw
# scores, cutoffs in (95, 100] that WRatio's /0.95 chaining pushes above 100
# internally, and outright > 100 cutoffs where the C++ backend returns 0
# no matter the raw score. This class of case is what catches a cutoff
# bypass in an early `return 100` branch.
CUTOFF_PAIRS: list[tuple[str, str]] = [
    ("hello world", "hello world xy"),  # WRatio 95.0 via token-subset path
    ("a b", "a b c"),  # token_set subset early-return 100
    ("this is a test", "this is a test!"),
    ("", ""),
    ("", "abc"),
    ("cat", "the cat sat on the mat"),  # 1.5 <= len ratio <= 8
    ("ab", "the quick brown fox jumps over the lazy dog and keeps running"),  # > 8
    ("fuzzy wuzzy was a bear", "wuzzy fuzzy was a bear"),
    ("\U0001F600\U0001F603\U0001F604", "\U0001F600\U0001F604\U0001F603"),
    ("stra\u00dfe", "STRASSE"),
]
CUTOFFS = [0.0, 30.0, 70.0, 90.0, 95.5, 96.0, 99.0, 100.0, 150.0]

SCORERS = [
    "ratio",
    "partial_ratio",
    "token_sort_ratio",
    "token_set_ratio",
    "token_ratio",
    "partial_token_sort_ratio",
    "partial_token_set_ratio",
    "partial_token_ratio",
    "WRatio",
    "QRatio",
]


def kt_escape(s: str) -> str:
    """Escapes a Python string as a Kotlin string literal body (ASCII-only,
    astral characters as surrogate pairs)."""
    out = []
    for ch in s:
        cp = ord(ch)
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "$":
            out.append("\\$")
        elif 0x20 <= cp < 0x7F:
            out.append(ch)
        elif cp > 0xFFFF:
            v = cp - 0x10000
            out.append(f"\\u{0xD800 + (v >> 10):04X}\\u{0xDC00 + (v & 0x3FF):04X}")
        else:
            out.append(f"\\u{cp:04X}")
    return "".join(out)


def main() -> None:
    import rapidfuzz
    from rapidfuzz import fuzz, utils

    if rapidfuzz.__version__ != PIN:
        sys.exit(
            f"rapidfuzz {rapidfuzz.__version__} installed but {PIN} required; "
            f"run: pip install rapidfuzz=={PIN}"
        )

    scorer_fns = {name: getattr(fuzz, name) for name in SCORERS}

    cases: list[str] = []
    for s1, s2 in PAIRS:
        for name in SCORERS:
            fn = scorer_fns[name]
            for processed in (False, True):
                processor = utils.default_process if processed else None
                expected = fn(s1, s2, processor=processor)
                cases.append(
                    f'GoldenCase("{kt_escape(s1)}", "{kt_escape(s2)}", '
                    f'"{name}", {str(processed).lower()}, {expected!r})'
                )

    cutoff_cases: list[str] = []
    for s1, s2 in CUTOFF_PAIRS:
        for name in SCORERS:
            fn = scorer_fns[name]
            for processed in (False, True):
                processor = utils.default_process if processed else None
                for cutoff in CUTOFFS:
                    expected = fn(s1, s2, processor=processor, score_cutoff=cutoff)
                    cutoff_cases.append(
                        f'CutoffCase("{kt_escape(s1)}", "{kt_escape(s2)}", '
                        f'"{name}", {str(processed).lower()}, {cutoff!r}, {expected!r})'
                    )

    process_cases = [
        f'ProcessCase("{kt_escape(s)}", "{kt_escape(utils.default_process(s))}")'
        for s in PROCESS_INPUTS
    ]

    chunk = 200
    chunk_fns = []
    add_alls = []
    for i in range(0, len(cases), chunk):
        n = i // chunk
        body = ",\n    ".join(cases[i : i + chunk])
        chunk_fns.append(
            f"private fun goldenCases{n}(): List<GoldenCase> = listOf(\n    {body},\n)\n"
        )
        add_alls.append(f"    addAll(goldenCases{n}())")

    cutoff_chunk_fns = []
    cutoff_add_alls = []
    for i in range(0, len(cutoff_cases), chunk):
        n = i // chunk
        body = ",\n    ".join(cutoff_cases[i : i + chunk])
        cutoff_chunk_fns.append(
            f"private fun cutoffCases{n}(): List<CutoffCase> = listOf(\n    {body},\n)\n"
        )
        cutoff_add_alls.append(f"    addAll(cutoffCases{n}())")

    process_body = ",\n    ".join(process_cases)

    OUT.write_text(
        f"""\
// GENERATED FILE -- DO NOT EDIT.
// Produced by tools/generate_vectors.py against rapidfuzz=={PIN}.
// {len(cases)} scorer cases over {len(PAIRS)} string pairs,
// {len(cutoff_cases)} score_cutoff cases over {len(CUTOFF_PAIRS)} pairs, plus
// {len(process_cases)} defaultProcess cases. CI regenerates this file and
// fails on drift.
package io.github.likhithsj.kmatch

internal class GoldenCase(
    val s1: String,
    val s2: String,
    val scorer: String,
    val processed: Boolean,
    val expected: Double,
)

internal class CutoffCase(
    val s1: String,
    val s2: String,
    val scorer: String,
    val processed: Boolean,
    val cutoff: Double,
    val expected: Double,
)

internal class ProcessCase(val input: String, val expected: String)

{"".join(chunk_fns)}
internal val GOLDEN_CASES: List<GoldenCase> = buildList {{
{chr(10).join(add_alls)}
}}

{"".join(cutoff_chunk_fns)}
internal val CUTOFF_CASES: List<CutoffCase> = buildList {{
{chr(10).join(cutoff_add_alls)}
}}

internal val PROCESS_CASES: List<ProcessCase> = listOf(
    {process_body},
)
"""
    )
    print(f"wrote {OUT} ({len(cases)} cases, {len(cutoff_cases)} cutoff cases, {len(process_cases)} process cases)")


if __name__ == "__main__":
    main()
