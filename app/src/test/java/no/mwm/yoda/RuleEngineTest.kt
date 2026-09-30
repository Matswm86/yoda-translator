package no.mwm.yoda

import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.LanguageDetector
import no.mwm.yoda.engine.YodaStyle
import no.mwm.yoda.engine.rule.RuleEngine
import no.mwm.yoda.engine.rule.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    private fun bag(s: String) = Tokenizer.tokenize(s)
        .filterNot { Tokenizer.isPunctuation(it) }
        .map { it.lowercase() }
        .sorted()

    // ------------------------------------------------ the reference test sentence

    private val NO_SENTENCE = "Du vil lære tålmodighet hvis du trener hardt hver dag."

    @Test
    fun `classic norwegian matches the validated 12B output`() {
        val r = engine.translate(NO_SENTENCE, Lang.NO, YodaStyle.CLASSIC)
        assertEquals("Lære tålmodighet du vil, hvis du trener hardt hver dag.", r.output)
    }

    @Test
    fun `grammatical norwegian keeps V2`() {
        val r = engine.translate(NO_SENTENCE, Lang.NO, YodaStyle.GRAMMATICAL)
        assertEquals("Tålmodighet vil du lære, hvis du trener hardt hver dag.", r.output)
    }

    @Test
    fun `full inversion norwegian breaks V2`() {
        val r = engine.translate(NO_SENTENCE, Lang.NO, YodaStyle.FULL_INVERSION)
        assertEquals("Tålmodighet du lære vil, hvis du trener hardt hver dag.", r.output)
    }

    // ------------------------------------------------------- morphology contract

    @Test
    fun `every style preserves the exact word set`() {
        val samples = listOf(
            NO_SENTENCE to Lang.NO,
            "Du må trene hardt hver dag." to Lang.NO,
            "Jeg har lært mye av deg." to Lang.NO,
            "Han vil ikke lytte til meg." to Lang.NO,
            "You will learn patience if you train hard every day." to Lang.EN,
            "The mind of a child is truly wonderful." to Lang.EN,
            "I have much to learn from you." to Lang.EN,
            "She does not understand the danger." to Lang.EN
        )
        for ((text, lang) in samples) {
            for (style in YodaStyle.entries) {
                val r = engine.translate(text, lang, style)
                assertTrue("morphology flag for [$style] $text", r.morphologyPreserved)
                assertEquals("word set for [$style] $text", bag(text), bag(r.output))
            }
        }
    }

    @Test
    fun `modal is never swapped - the gemma3 4b failure`() {
        // The 4B model turned "vil" into "må". Movement cannot do that.
        for (style in YodaStyle.entries) {
            val out = engine.translate(NO_SENTENCE, Lang.NO, style).output.lowercase()
            assertTrue("vil must survive in $style", out.contains("vil"))
            assertTrue("må must not appear in $style", !out.contains("må"))
        }
    }

    @Test
    fun `subordinator never dangles at the end - the round 2 4b failure`() {
        for (style in YodaStyle.entries) {
            val out = engine.translate(NO_SENTENCE, Lang.NO, style).output.trimEnd('.', ' ')
            assertTrue("hvis dangles in $style: $out", !out.endsWith("hvis"))
        }
    }

    @Test
    fun `present tense inside the subordinate clause is kept`() {
        for (style in YodaStyle.entries) {
            val out = engine.translate(NO_SENTENCE, Lang.NO, style).output
            assertTrue("trener kept in $style", out.contains("trener"))
        }
    }

    // ------------------------------------------------------------------ English

    @Test
    fun `classic english fronts the verb phrase`() {
        val r = engine.translate(
            "You will learn patience if you train hard every day.", Lang.EN, YodaStyle.CLASSIC
        )
        assertEquals("Learn patience, you will, if you train hard every day.", r.output)
    }

    @Test
    fun `classic english fronts a copular predicate`() {
        val r = engine.translate(
            "The mind of a child is truly wonderful.", Lang.EN, YodaStyle.CLASSIC
        )
        assertEquals("Truly wonderful, the mind of a child is.", r.output)
    }

    @Test
    fun `grammatical english gives object subject verb`() {
        val r = engine.translate("You must have patience.", Lang.EN, YodaStyle.GRAMMATICAL)
        assertEquals("Patience you must have.", r.output)
    }

    // ------------------------------------------------------------------- casing

    @Test
    fun `proper nouns keep their capital when they stop being first`() {
        val r = engine.translate("Luke will learn patience.", Lang.EN, YodaStyle.CLASSIC)
        assertTrue("Luke stays capitalised: ${r.output}", r.output.contains("Luke"))
    }

    @Test
    fun `english I stays capital wherever it lands`() {
        val r = engine.translate("I will find the answer.", Lang.EN, YodaStyle.CLASSIC)
        assertTrue("I stays capital: ${r.output}", Regex("\\bI\\b").containsMatchIn(r.output))
    }

    // -------------------------------------------------------------- robustness

    @Test
    fun `sentences with no finite verb are returned untouched`() {
        val r = engine.translate("Tålmodighet.", Lang.NO, YodaStyle.CLASSIC)
        assertEquals(bag("Tålmodighet."), bag(r.output))
    }

    @Test
    fun `multiple sentences are each transformed`() {
        val r = engine.translate(
            "Du vil lære. Han vil trene.", Lang.NO, YodaStyle.CLASSIC
        )
        assertEquals(bag("Du vil lære. Han vil trene."), bag(r.output))
        assertTrue(r.output.count { it == '.' } == 2)
    }

    @Test
    fun `blank input does not crash`() {
        assertEquals("", engine.translate("   ", Lang.NO, YodaStyle.CLASSIC).output)
    }

    @Test
    fun `fronted conditional without a comma still parses`() {
        val r = engine.translate(
            "Hvis du trener hardt vil du lære.", Lang.NO, YodaStyle.CLASSIC
        )
        assertEquals(bag("Hvis du trener hardt vil du lære."), bag(r.output))
    }

    // ------------------------------------- regressions from the first sample run

    @Test
    fun `norwegian negation lands after the inverted subject`() {
        val r = engine.translate("Han vil ikke lytte til meg.", Lang.NO, YodaStyle.GRAMMATICAL)
        assertEquals("Til meg vil han ikke lytte.", r.output)
    }

    @Test
    fun `siden as a noun is not read as a subordinator`() {
        val r = engine.translate("Frykt er veien til den mørke siden.", Lang.NO, YodaStyle.GRAMMATICAL)
        assertEquals("Veien til den mørke siden er frykt.", r.output)
    }

    @Test
    fun `V2 inverted main clause after a fronted conditional keeps its subject`() {
        val r = engine.translate("Hvis du trener hardt, vil du lære.", Lang.NO, YodaStyle.CLASSIC)
        assertEquals("Hvis du trener hardt, lære du vil.", r.output)
    }

    @Test
    fun `cannot is recognised as a modal`() {
        val r = engine.translate("We cannot win this fight alone.", Lang.EN, YodaStyle.CLASSIC)
        assertEquals("Win this fight alone, we cannot.", r.output)
    }

    @Test
    fun `sentence-initial common nouns lose their capital when moved`() {
        // Genitive inflection, and a bare stem shared with a listed verb.
        assertTrue(
            engine.translate("Barnets sinn er virkelig fantastisk.", Lang.NO, YodaStyle.GRAMMATICAL)
                .output.contains("barnets")
        )
        assertTrue(
            engine.translate("Frykt er veien til siden.", Lang.NO, YodaStyle.GRAMMATICAL)
                .output.contains("frykt")
        )
    }

    @Test
    fun `modal plus auxiliary is treated as non-finite`() {
        val r = engine.translate("You must have patience.", Lang.EN, YodaStyle.CLASSIC)
        assertEquals("Have patience, you must.", r.output)
    }

    // ------------------------------------------------ pilot review, 2026-09
    // Each case is a pair where the reviewer rejected the engine's output. The expected
    // line is the teacher output the reviewer picked, or the nearest shape the engine can
    // reach by moving words only.

    private fun classic(text: String, lang: Lang) =
        engine.translate(text, lang, YodaStyle.CLASSIC).output

    @Test
    fun `pilot 3 - a verb particle stays with its verb`() {
        assertEquals("Who Mary was, Tom found out.", classic("Tom found out who Mary was.", Lang.EN))
    }

    @Test
    fun `pilot 19 - a norwegian particle stays with its verb`() {
        assertEquals("Pennen sin han tok frem.", classic("Han tok frem pennen sin.", Lang.NO))
    }

    @Test
    fun `pilot 8 - object control fronts only the infinitive phrase`() {
        assertEquals("Call me, I told you to.", classic("I told you to call me.", Lang.EN))
    }

    @Test
    fun `pilot 12 and 35 - comma before a name subject`() {
        assertEquals("Grådige, Tom og Mary er.", classic("Tom og Mary er grådige.", Lang.NO))
        assertEquals(
            "En allianse med Italia, Tyskland stiftet.",
            classic("Tyskland stiftet en allianse med Italia.", Lang.NO)
        )
    }

    @Test
    fun `pilot 23 and 30 - a moved norwegian opener loses its capital`() {
        assertEquals(
            "Senest klokken åtte vennligst kom.",
            classic("Vennligst kom senest klokken åtte.", Lang.NO)
        )
        assertEquals("Dyrt og farlig kjernekraft er.", classic("Kjernekraft er dyrt og farlig.", Lang.NO))
    }

    @Test
    fun `pilot 29 - a coordinated predicate moves as one block`() {
        assertEquals("Grådig og lat han er.", classic("Han er grådig og lat.", Lang.NO))
    }

    @Test
    fun `pilot 24 and 40 - for-phrases are not clauses`() {
        assertEquals(
            "Been waiting for you all morning, Tom has.",
            classic("Tom has been waiting for you all morning.", Lang.EN)
        )
        assertEquals(
            "Waiting for this to happen, we've been.",
            classic("We've been waiting for this to happen.", Lang.EN)
        )
    }

    @Test
    fun `pilot 28 - a purpose clause keeps the comma the reviewer approved`() {
        assertEquals(
            "Skynde meg til stasjonen jeg må, for å nå det siste toget.",
            classic("Jeg må skynde meg til stasjonen for å nå det siste toget.", Lang.NO)
        )
    }

    @Test
    fun `names keep their capital when they move`() {
        assertTrue(classic("Tom er en god manager.", Lang.NO).contains("Tom"))
        assertTrue(classic("Thor Heyerdahl fikk verdensberømmelse.", Lang.NO).contains("Thor Heyerdahl"))
        assertTrue(classic("I'm sure that I can handle it.", Lang.EN).contains("I'm"))
    }

    // ------------------------------------------------------- language detection

    @Test
    fun `language detection separates the two test sentences`() {
        assertEquals(Lang.NO, LanguageDetector.detect(NO_SENTENCE))
        assertEquals(
            Lang.EN,
            LanguageDetector.detect("You will learn patience if you train hard every day.")
        )
    }
}
