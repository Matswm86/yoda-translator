package no.mwm.yoda.engine

import no.mwm.yoda.engine.rule.Lexicon
import no.mwm.yoda.engine.rule.Tokenizer

/** Picks Norwegian or English by counting function words, with a letter tiebreak. */
object LanguageDetector {

    private val NO_ONLY_LETTERS = setOf('æ', 'ø', 'å')

    fun detect(text: String): Lang {
        if (text.isBlank()) return Lang.EN

        if (text.lowercase().any { it in NO_ONLY_LETTERS }) return Lang.NO

        val words = Tokenizer.tokenize(text)
            .filterNot { Tokenizer.isPunctuation(it) }
            .map { it.lowercase() }
        if (words.isEmpty()) return Lang.EN

        // Score only on words that belong to exactly one language's function
        // words. Shared spellings like "i", "for", "man" carry no signal.
        var no = 0
        var en = 0
        for (w in words) {
            val inNo = w in Lexicon.NO_MARKERS
            val inEn = w in Lexicon.EN_MARKERS
            if (inNo && !inEn) no++
            if (inEn && !inNo) en++
        }
        return if (no > en) Lang.NO else Lang.EN
    }
}
