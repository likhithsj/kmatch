#!/usr/bin/env python3
"""Generates differential-fuzz cases and RapidFuzz's answers for them.

Random string pairs (dense small alphabets, shuffled/mutated token lists with
every separator the tokenizer knows, strings crossing the 64- and 128-code-
point block boundaries, needles cut out of haystacks, equal-length pairs,
mixed-script soup) are scored by every scorer, with and without
default_process, at several cutoffs each -- always including the pair's own
exact score and the doubles one ulp either side of it. Also emitted:
default_process over every Unicode code point, alignment checks, and
extractOne/extract runs. The fixed golden vectors pin known cases; this hunts
for unknown ones.

Usage: python3 generate_cases.py <pairs> <seed> <out-dir>
Writes <out-dir>/cases.tsv and <out-dir>/expected.tsv (strings as UTF-16BE
hex, doubles as raw IEEE-754 bits, so comparison is exact).
"""
import math
import random
import struct
import sys

import rapidfuzz
from rapidfuzz import fuzz, process, utils
from rapidfuzz.distance import LCSseq

PIN = "3.14.5"
if rapidfuzz.__version__ != PIN:
    sys.exit(f"rapidfuzz {rapidfuzz.__version__} installed but {PIN} required; run: pip install rapidfuzz=={PIN}")
# The parity target is the C++ backend; the pure-Python fallback differs
# (tokenizer whitespace, cutoff gating) and must not be used as the oracle.
if not fuzz.ratio.__module__.startswith("rapidfuzz.fuzz_cpp"):
    sys.exit(f"RapidFuzz is running its {fuzz.ratio.__module__} backend; the C++ backend is required")
if len(sys.argv) != 4:
    sys.exit(__doc__)

N = int(sys.argv[1])
rnd = random.Random(int(sys.argv[2]))
OUT_DIR = sys.argv[3]
SC = {n: getattr(fuzz, n) for n in ["ratio","partial_ratio","token_sort_ratio","token_set_ratio","token_ratio",
      "partial_token_sort_ratio","partial_token_set_ratio","partial_token_ratio","WRatio","QRatio"]}

def hx(s): return "-" if s == "" else s.encode("utf-16-be").hex()
def bits(x): return struct.pack(">d", float(x)).hex()

WS = [" ", " ", " ", "  ", "\t", "\n", "\r", "\x0b", "\x0c", "\x1c", "\x1d", "\x1e", "\x1f", "\x85", "\xa0", " ",
      " ", " ", " ", " ", " ", " ", " ", "　", "​", "﻿", "᠎"]
VOCAB = ["new","york","jets","giants","the","a","of","Caffè","latte","LATTE","mocha","straße","Ünïcödé","naïve","日本","東京",
         "😀","👍🏽","x","xy","abc","abd","café","cafe","123","42","foo_bar","hello","world","hello!","İstanbul","ǅ","ß","²","Ⅷ","ﬁn"]
POOLS = ["ab", "abc", "abcdefgh", "abcdefghijklmnopqrstuvwxyz", "aàáâãäå", "αβγδε", "абвгд", "日本語東京", "😀😁😂🤣😃",
         "a_b-c.d,e;f!", "aA bB", "́̈e", "0123456789"]

def rstr(pool, lo, hi):
    return "".join(rnd.choice(pool) for _ in range(rnd.randint(lo, hi)))
def mutate(s, k):
    s = list(s)
    for _ in range(k):
        op = rnd.random(); pool = rnd.choice(POOLS)
        if op < 0.3 and s: del s[rnd.randrange(len(s))]
        elif op < 0.6: s.insert(rnd.randint(0, len(s)), rnd.choice(pool))
        elif op < 0.85 and s: s[rnd.randrange(len(s))] = rnd.choice(pool)
        elif len(s) > 1:
            i = rnd.randrange(len(s) - 1); s[i], s[i+1] = s[i+1], s[i]
    return "".join(s)
def words():
    n = rnd.randint(0, 7)
    toks = [rnd.choice(VOCAB) for _ in range(n)]
    s = rnd.choice(["", "", " ", "\t"]) + "".join(t + rnd.choice(WS) for t in toks)
    return s if rnd.random() < 0.5 else s.rstrip()
def pair():
    k = rnd.random()
    if k < 0.15:
        p = rnd.choice(POOLS[:3]); return rstr(p, 0, 12), rstr(p, 0, 12)
    if k < 0.40:
        a = words(); b = words() if rnd.random() < 0.4 else None
        if b is None:
            toks = a.split(); rnd.shuffle(toks)
            if toks and rnd.random() < 0.5: toks = toks[: rnd.randint(1, len(toks))]
            if rnd.random() < 0.5: toks += [rnd.choice(VOCAB)]
            b = rnd.choice(WS).join(toks)
            if rnd.random() < 0.3: b = mutate(b, rnd.randint(1, 3))
        return a, b
    if k < 0.60:  # long, crossing 64/128 boundaries
        p = rnd.choice(POOLS[:8]); a = rstr(p, 55, 210)
        b = mutate(a, rnd.randint(0, 25)) if rnd.random() < 0.6 else rstr(p, 1, 210)
        return (a, b) if rnd.random() < 0.5 else (b, a)
    if k < 0.72:  # needle in haystack (partial ratio)
        p = rnd.choice(POOLS); h = rstr(p, 5, 150)
        i = rnd.randrange(len(h)); j = min(len(h), i + rnd.randint(1, 70))
        n = mutate(h[i:j], rnd.randint(0, 3))
        return (n, h) if rnd.random() < 0.5 else (h, n)
    if k < 0.82:  # equal length
        p = rnd.choice(POOLS); L = rnd.choice([1,2,3,5,8,13,63,64,65,127,128,129])
        a = rstr(p, L, L); b = "".join(rnd.choice(p) if rnd.random() < 0.3 else c for c in a)
        return a, b
    if k < 0.92:  # unicode soup
        p = "".join(POOLS) + "".join(WS)
        return rstr(p, 0, 40), rstr(p, 0, 40)
    return rnd.choice([("", ""), ("", "a"), ("a", ""), (" ", " "), ("a", "a"), ("  \t", "x"), ("_", "-"), ("ab", "ba"),
                       ("a b", "b a"), ("a a", "a"), ("\xa0a\xa0", "a"), ("A", "a"), ("İ", "i̇"), ("ǅ", "ǆ")])

cases = open(OUT_DIR + "/cases.tsv", "w", encoding="ascii")
exp = open(OUT_DIR + "/expected.tsv", "w", encoding="ascii")
cid = 0
def emit(fields, expected):
    global cid
    cid += 1
    cases.write(str(cid) + "\t" + "\t".join(fields) + "\n"); exp.write(str(cid) + "\t" + expected + "\n")

def cutoffs(base):
    cs = [None]
    opts = [rnd.uniform(0, 100), base, math.nextafter(base, math.inf), math.nextafter(base, -math.inf), 100.0, 0.0,
            float(round(base)), 100.5, rnd.choice([50.0, 70.0, 80.0, 90.0])]
    cs += rnd.sample(opts, 3)
    return cs

pairs = [pair() for _ in range(N)]
for a, b in pairs:
    for name, fn in SC.items():
        for proc in (0, 1):
            P = utils.default_process if proc else None
            base = fn(a, b, processor=P)
            for c in cutoffs(base):
                e = fn(a, b, processor=P, score_cutoff=c)
                emit(["s", name, hx(a), hx(b), str(proc), "-" if c is None else bits(c)], bits(e))
    emit(["mb", hx(a), hx(b)], f"{int(LCSseq.similarity(a, b))};1")

# default_process over every code point (surrogates excluded), in context
for start in range(0, 0x110000, 2048):
    cps = [c for c in range(start, min(start + 2048, 0x110000)) if not (0xD800 <= c <= 0xDFFF)]
    if not cps: continue
    s = "q" + "q".join(chr(c) for c in cps) + "q"
    emit(["dp", hx(s)], hx(utils.default_process(s)))
for a, b in pairs[:1500]:
    emit(["dp", hx(a)], hx(utils.default_process(a)))

# extraction
names = list(SC)
for _ in range(max(200, N // 3)):
    name = rnd.choice(names); fn = SC[name]
    base = [pair() for _ in range(rnd.randint(0, 25))]
    choices = [x for pr in base for x in pr][: rnd.randint(0, 40)]
    if choices and rnd.random() < 0.5: choices += rnd.sample(choices, min(3, len(choices)))  # duplicates -> ties
    q = rnd.choice(choices) if choices and rnd.random() < 0.5 else pair()[0]
    if rnd.random() < 0.5: q = mutate(q, 2)
    proc = rnd.randint(0, 1); P = utils.default_process if proc else None
    for c in [None, rnd.uniform(0, 100), rnd.choice([0.0, 50.0, 85.5, 90.0, 100.0])]:
        cb = "-" if c is None else bits(c)
        ch = ",".join(hx(x) for x in choices)
        r = process.extractOne(q, choices, scorer=fn, processor=P, score_cutoff=c)
        emit(["x1", name, hx(q), ch, str(proc), cb, "0"], "none" if r is None else f"{r[2]}:{bits(r[1])}")
        lim = rnd.choice([1, 2, 5, 100])
        r = process.extract(q, choices, scorer=fn, processor=P, score_cutoff=c, limit=lim)
        emit(["xt", name, hx(q), ch, str(proc), cb, str(lim)], ",".join(f"{x[2]}:{bits(x[1])}" for x in r))
        r = process.extract(q, choices, scorer=fn, processor=P, score_cutoff=c, limit=None)
        r = sorted(r, key=lambda x: x[2])
        emit(["xa", name, hx(q), ch, str(proc), cb, "0"], ",".join(f"{x[2]}:{bits(x[1])}" for x in r))
print(cid, "cases")
