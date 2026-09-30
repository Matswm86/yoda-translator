package no.mwm.yoda

import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.YodaStyle
import no.mwm.yoda.engine.rule.RuleEngine
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Not a test: a batch entry point so the corpus pipeline can run the *shipped*
 * engine over a file of sentences instead of reimplementing it in Python.
 *
 * Skipped unless -Dyoda.batch.in is set, so a normal `testDebugUnitTest` run is
 * unaffected.
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*RuleEngineBatchTool*' \
 *       -Dyoda.batch.in=pool.jsonl -Dyoda.batch.out=screened.tsv
 *
 * Input:  JSONL with "lang" and "text" keys.
 * Output: TSV lang, confidence, morphologyPreserved, source, classicOutput.
 */
class RuleEngineBatchTool {

    private val engine = RuleEngine()

    @Test
    fun batch() {
        val inPath = System.getProperty("yoda.batch.in")
        Assume.assumeTrue("yoda.batch.in not set; batch tool skipped", inPath != null)
        val outPath = System.getProperty("yoda.batch.out")
            ?: error("yoda.batch.out must be set alongside yoda.batch.in")

        val out = File(outPath).bufferedWriter()
        out.use { w ->
            w.write("lang\tconfidence\tpreserved\tsource\tclassic\n")
            File(inPath).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val lang = if (jsonString(line, "lang") == "en") Lang.EN else Lang.NO
                val text = jsonString(line, "text") ?: return@forEachLine
                val r = engine.translate(text, lang, YodaStyle.CLASSIC)
                w.write(
                    listOf(
                        lang.code,
                        "%.3f".format(java.util.Locale.ROOT, r.confidence),
                        r.morphologyPreserved.toString(),
                        text.replace('\t', ' '),
                        r.output.replace('\t', ' ')
                    ).joinToString("\t") + "\n"
                )
            }
        }
        println("batch: wrote $outPath")
    }

    /** Minimal string-field reader; the pool is machine-written, one object per line. */
    private fun jsonString(line: String, key: String): String? {
        val at = line.indexOf("\"$key\"")
        if (at < 0) return null
        var i = line.indexOf(':', at) + 1
        while (i < line.length && line[i].isWhitespace()) i++
        if (i >= line.length || line[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < line.length && line[i] != '"') {
            if (line[i] == '\\' && i + 1 < line.length) {
                i++
                when (val e = line[i]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'u' -> { sb.append(line.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                    else -> sb.append(e)
                }
            } else sb.append(line[i])
            i++
        }
        return sb.toString()
    }
}
