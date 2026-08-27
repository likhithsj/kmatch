// Swift-side verification of the kmatch API through the SKIE-enhanced
// framework. Asserts the same golden values the Kotlin test suite pins, so a
// pass here means Swift callers get bit-exact RapidFuzz scores too.
import Foundation
import KmatchKit

var failures = 0

func check(_ name: String, _ actual: Double, _ expected: Double) {
    if actual == expected {
        print("PASS  \(name) = \(actual)")
    } else {
        print("FAIL  \(name): expected \(expected), got \(actual)")
        failures += 1
    }
}

// 1. Pairwise scorers on the Fuzz object (Kotlin object -> .shared).
check("ratio", Fuzz.shared.ratio(s1: "this is a test", s2: "this is a test!", processor: nil, scoreCutoff: nil), 96.55172413793103)
check("partialRatio", Fuzz.shared.partialRatio(s1: "this is a test", s2: "this is a test!", processor: nil, scoreCutoff: nil), 100.0)
check("tokenSortRatio", Fuzz.shared.tokenSortRatio(s1: "fuzzy wuzzy was a bear", s2: "wuzzy fuzzy was a bear", processor: nil, scoreCutoff: nil), 100.0)

// 2. Kotlin default preprocessing passed as a Swift closure.
check("ratio+defaultProcess", Fuzz.shared.ratio(s1: "Test String!", s2: "test string", processor: { s in ProcessKt.defaultProcess(s: s) }, scoreCutoff: nil), 100.0)

// 3. Extraction over a Swift array; result is a Kotlin data class.
let choices = ["Atlanta Falcons", "New York Jets", "New York Giants", "Dallas Cowboys"]
if let best = ExtractKt.extractOne(query: "new york jets", choices: choices, scorer: Scorers.shared.WeightedRatio, processor: { s in ProcessKt.defaultProcess(s: s) }, scoreCutoff: nil) {
    check("extractOne.score", best.score, 100.0)
    if best.choice == "New York Jets" && best.index == 1 {
        print("PASS  extractOne -> \(best.choice) @\(best.index)")
    } else {
        print("FAIL  extractOne -> \(best.choice) @\(best.index)")
        failures += 1
    }
} else {
    print("FAIL  extractOne returned nil")
    failures += 1
}

// 4. A custom Scorer supplied as a Swift closure (fun interface via SKIE).
let exact = Scorer { a, b, _ in a == b ? 100.0 : 0.0 }
if let hit = ExtractKt.extractOne(query: "New York Jets", choices: choices, scorer: exact, processor: nil, scoreCutoff: nil) {
    check("customScorer.score", hit.score, 100.0)
} else {
    print("FAIL  custom scorer returned nil")
    failures += 1
}

// 5. Kotlin helper compiled into this framework.
check("kotlinSideRatio", SampleKt.kotlinSideRatio(), 96.55172413793103)

if failures > 0 {
    print("\n\(failures) FAILURE(S)")
    exit(1)
}
print("\nALL SWIFT-SIDE CHECKS PASSED — kmatch is SKIE/Swift compatible")
