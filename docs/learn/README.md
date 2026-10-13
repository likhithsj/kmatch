# Learn kmatch, one step at a time

A guided tour of this codebase for someone who has never seen it before.
Read the files in order — each one builds on the previous:

| File | What you'll understand after reading it |
|---|---|
| [01-start-here.md](01-start-here.md) | What the library does, the one mental model behind everything, and where code "starts" |
| [02-journey-of-a-call.md](02-journey-of-a-call.md) | A single search call traced through every file it touches, start to finish |
| [03-the-scoring-math.md](03-the-scoring-math.md) | How two strings become a number from 0 to 100 — with worked examples |
| [04-the-bit-parallel-trick.md](04-the-bit-parallel-trick.md) | Why it's fast: the 64-lightbulbs-in-one-number trick |
| [05-map-of-every-file.md](05-map-of-every-file.md) | What every file in the repo is for |
| [06-from-push-to-your-phone.md](06-from-push-to-your-phone.md) | How a `git push` here ends up as a library inside someone's app |

**About the 📍 code links:** throughout the guides, 📍 lines link to the
exact file and line on GitHub. They're pinned to one commit, so they stay
accurate forever even as the code evolves. To jump locally instead: open
the file in your IDE and use Cmd/Ctrl+G (go to line).

No prior knowledge needed beyond basic programming (functions, loops,
strings). Kotlin specifics are explained where they matter.
