#!/usr/bin/env python3
"""Compares kmatch's answers with RapidFuzz's. Exit status 1 on any mismatch.

Usage: python3 compare.py <dir> [max-examples]
"""
import sys, collections
d = sys.argv[1]
exp = dict(l.rstrip("\n").split("\t", 1) for l in open(d + "/expected.tsv"))
got = dict(l.rstrip("\n").split("\t", 1) for l in open(d + "/actual.tsv"))
cases = {l.split("\t", 1)[0]: l.rstrip("\n").split("\t") for l in open(d + "/cases.tsv")}
bad = [k for k in exp if got.get(k) != exp[k]]
print(len(exp), "cases,", len(bad), "mismatches; missing:", sum(1 for k in exp if k not in got))
by = collections.Counter((cases[k][1], cases[k][2] if cases[k][1] in ("s","x1","xt","xa") else "") for k in bad)
for k, v in by.most_common(): print("  ", k, v)
def uh(h): return "" if h == "-" else bytes.fromhex(h).decode("utf-16-be", "surrogatepass")
for k in bad[: int(sys.argv[2]) if len(sys.argv) > 2 else 12]:
    c = cases[k]
    if c[1] == "s":
        import struct
        f = lambda b: struct.unpack(">d", bytes.fromhex(b))[0]
        print(k, c[2], repr(uh(c[3])), repr(uh(c[4])), "proc", c[5], "cutoff", None if c[6] == "-" else repr(f(c[6])), "exp", repr(f(exp[k])), "got", got.get(k) if got.get(k, "EXC").startswith("EXC") else repr(f(got[k])))
    else:
        print(k, c[1:3], "exp", exp[k][:200], "| got", got.get(k, "")[:200])
sys.exit(1 if bad else 0)
