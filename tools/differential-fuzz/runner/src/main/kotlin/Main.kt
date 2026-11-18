import io.github.likhithsj.kmatch.*
import java.io.File

fun unhex(h: String): String {
    if (h == "-") return ""
    val sb = StringBuilder(h.length / 4)
    var i = 0
    while (i < h.length) { sb.append(h.substring(i, i + 4).toInt(16).toChar()); i += 4 }
    return sb.toString()
}
fun hex(s: String): String = if (s.isEmpty()) "-" else buildString { for (c in s) append("%04x".format(c.code)) }
fun bits(d: Double): String = "%016x".format(java.lang.Double.doubleToRawLongBits(d))
fun cut(s: String): Double? = if (s == "-") null else java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(s, 16))

val fuzzFns: Map<String, (String, String, ((String) -> String)?, Double?) -> Double> = mapOf(
    "ratio" to Fuzz::ratio, "partial_ratio" to Fuzz::partialRatio,
    "token_sort_ratio" to Fuzz::tokenSortRatio, "token_set_ratio" to Fuzz::tokenSetRatio,
    "token_ratio" to Fuzz::tokenRatio, "partial_token_sort_ratio" to Fuzz::partialTokenSortRatio,
    "partial_token_set_ratio" to Fuzz::partialTokenSetRatio, "partial_token_ratio" to Fuzz::partialTokenRatio,
    "WRatio" to Fuzz::weightedRatio, "QRatio" to Fuzz::quickRatio,
)
val scorers: Map<String, Scorer> = mapOf(
    "ratio" to Scorers.Ratio, "partial_ratio" to Scorers.PartialRatio,
    "token_sort_ratio" to Scorers.TokenSortRatio, "token_set_ratio" to Scorers.TokenSetRatio,
    "token_ratio" to Scorers.TokenRatio, "partial_token_sort_ratio" to Scorers.PartialTokenSortRatio,
    "partial_token_set_ratio" to Scorers.PartialTokenSetRatio, "partial_token_ratio" to Scorers.PartialTokenRatio,
    "WRatio" to Scorers.WeightedRatio, "QRatio" to Scorers.QuickRatio,
)

fun cpCount(s: String) = s.codePointCount(0, s.length)

fun main(args: Array<String>) {
    val out = File(args[1]).bufferedWriter()
    File(args[0]).useLines { lines ->
        for (line in lines) {
            val p = line.split('\t')
            val id = p[0]
            val res: String = try {
                when (p[1]) {
                    "s" -> { // id s scorer s1 s2 proc cutoff
                        val proc: ((String) -> String)? = if (p[5] == "1") ::defaultProcess else null
                        bits(fuzzFns.getValue(p[2])(unhex(p[3]), unhex(p[4]), proc, cut(p[6])))
                    }
                    "dp" -> hex(defaultProcess(unhex(p[2])))
                    "sa" -> hex(stripAccents(unhex(p[2])))
                    "mb" -> {
                        val a = unhex(p[2]); val b = unhex(p[3])
                        val blocks = matchingBlocks(a, b)
                        var ok = true; var n = 0; var pa = -1; var pb = -1
                        for (blk in blocks) {
                            if (blk.length <= 0 || blk.aStart < 0 || blk.bStart < 0 || blk.aStart + blk.length > a.length || blk.bStart + blk.length > b.length) { ok = false; break }
                            val sa = a.substring(blk.aStart, blk.aStart + blk.length)
                            if (sa != b.substring(blk.bStart, blk.bStart + blk.length)) ok = false
                            if (blk.aStart < pa || blk.bStart < pb) ok = false          // ascending, non-overlapping
                            if (blk.aStart == pa && blk.bStart == pb) ok = false        // maximal (not mergeable)
                            pa = blk.aStart + blk.length; pb = blk.bStart + blk.length
                            n += cpCount(sa)
                        }
                        val r = matchingRanges(a, b)
                        if (r.size != blocks.size || r.zip(blocks).any { (x, y) -> x.first != y.bStart || x.last + 1 != y.bStart + y.length }) ok = false
                        "$n;${if (ok) 1 else 0}"
                    }
                    "x1", "xt", "xa" -> { // id op scorer query choices proc cutoff limit
                        val proc: ((String) -> String)? = if (p[5] == "1") ::defaultProcess else null
                        val choices = if (p[4] == "") emptyList() else p[4].split(',').map(::unhex)
                        val sc = scorers.getValue(p[2]); val q = unhex(p[3]); val c = cut(p[6])
                        when (p[1]) {
                            "x1" -> extractOne(q, choices, sc, proc, c)?.let { "${it.index}:${bits(it.score)}" } ?: "none"
                            "xt" -> extractTop(q, choices, p[7].toInt(), sc, proc, c).joinToString(",") { "${it.index}:${bits(it.score)}" }
                            else -> extractAll(q, choices, sc, proc, c).joinToString(",") { "${it.index}:${bits(it.score)}" }
                        }
                    }
                    else -> "?"
                }
            } catch (e: Throwable) { "EXC:" + e::class.simpleName + ":" + (e.message ?: "").take(80).replace('\t', ' ').replace('\n', ' ') }
            out.write(id); out.write("\t"); out.write(res); out.write("\n")
        }
    }
    out.close()
}
