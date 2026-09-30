package no.mwm.yoda

import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.LanguageDetector
import no.mwm.yoda.engine.YodaStyle
import no.mwm.yoda.engine.rule.RuleEngine
import org.junit.Test

/** Not an assertion test: prints a spread of output so the style can be judged by eye. */
class SampleDumpTest {

    private val engine = RuleEngine()

    private val samples = listOf(
        "Du vil lære tålmodighet hvis du trener hardt hver dag.",
        "Du må trene hardt hver dag.",
        "Jeg har lært mye av deg.",
        "Han vil ikke lytte til meg.",
        "Hvis du trener hardt, vil du lære.",
        "Barnets sinn er virkelig fantastisk.",
        "Vi kan ikke vinne denne kampen alene.",
        "Frykt er veien til den mørke siden.",
        "Du skal ikke prøve, du skal gjøre.",
        "You will learn patience if you train hard every day.",
        "The mind of a child is truly wonderful.",
        "I have much to learn from you.",
        "She does not understand the danger.",
        "Fear is the path to the dark side.",
        "You must unlearn what you have learned.",
        "We cannot win this fight alone."
    )

    @Test
    fun dump() {
        for (s in samples) {
            val lang = LanguageDetector.detect(s)
            println("\n[${lang.code}] $s")
            for (style in YodaStyle.entries) {
                val r = engine.translate(s, lang, style)
                val flag = if (r.morphologyPreserved) " " else "!"
                println("  $flag ${style.name.padEnd(15)} ${(r.confidence * 100).toInt()}%  ${r.output}")
                r.notes.forEach { println("      note: $it") }
            }
        }
    }
}
