import io.github.likhithsj.kmatch.Fuzz

/** Kotlin-side smoke check, callable from Swift as SampleKt.kotlinSideRatio(). */
fun kotlinSideRatio(): Double = Fuzz.ratio("this is a test", "this is a test!")
