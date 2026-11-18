# Differential fuzz against RapidFuzz

The golden vectors (`tools/generate_vectors.py`) pin a fixed set of known
cases. This tool hunts for unknown ones: it generates random inputs, asks the
pinned RapidFuzz (C++ backend) for the answers, runs the same inputs through
kmatch, and compares **bit for bit**.

```bash
pip install rapidfuzz==3.14.5
tools/differential-fuzz/run.sh                 # 3,000 pairs, seed 1, this working tree
tools/differential-fuzz/run.sh 9000 777        # more pairs, another seed
KMATCH_VERSION=1.0.0 tools/differential-fuzz/run.sh   # a published artifact instead
```

One pair expands to roughly 80 checks: 10 scorers × {raw, `default_process`}
× 4 cutoffs, plus an alignment check. On top of the pairs, every run verifies
`defaultProcess` for every Unicode code point and a few hundred
`extractOne` / `extractTop` / `extractAll` calls against
`rapidfuzz.process`. 3,000 pairs is about 254,000 comparisons; 9,000 is about
758,000.

What the generator leans on, because that is where ports go wrong:

- **Cutoffs at the boundary.** Every (pair, scorer) is run with the cutoff set
  to its own exact score and to the doubles one ulp above and below it.
- **Every token separator**, including the ones whose behavior depends on the
  string (NBSP and NEL separate only in strings holding a code point above
  U+00FF) and look-alikes that are not separators (ZWSP, BOM).
- **Block boundaries**: lengths around 64 and 128 code points, where the
  bit-parallel core switches from one machine word to several.
- **Shapes**: needle-in-haystack, equal lengths (partial ratio runs both
  directions), shuffled and subsetted token lists, empty and whitespace-only
  strings, astral-plane characters, combining marks.

## History

Its first run, against the published 1.0.0, found two parity bugs that 3,260
fixed vectors had not: `partialRatio` (and everything built on it) returning 0
when the cutoff was exactly the true score, and token scorers not splitting on
NBSP in strings with non-Latin-1 characters. Both are fixed, both now have
golden vectors, and `KMATCH_VERSION=1.0.0 ./run.sh` still reproduces them.

The oracle must be RapidFuzz's **C++** backend (the generator refuses to run
on the pure-Python fallback): the two backends disagree with each other in
exactly these corners, and the C++ one is what `pip install rapidfuzz` users
observe.
