package no.mwm.yoda.engine.rule

import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.TranslationEngine
import no.mwm.yoda.engine.TranslationResult
import no.mwm.yoda.engine.YodaStyle

/**
 * Offline constituent-movement engine.
 *
 * It tags, cuts the sentence into constituents and reorders them. It never
 * generates a word, so tense, agreement, definiteness and modal choice come
 * out exactly as they went in. [verifyMorphology] asserts that after the fact:
 * if the bag of words changed at all, the translation is rejected and the
 * original sentence is returned rather than shipping a corrupted form.
 */
class RuleEngine : TranslationEngine {

    override val id = "rule-v2"
    override val displayName = "Offline rule engine"

    override fun isAvailable() = true
    override fun unavailableReason(): String? = null

    override fun translate(input: String, lang: Lang, style: YodaStyle): TranslationResult {
        if (input.isBlank()) {
            return TranslationResult("", lang, style, id, true, 1f)
        }

        val outputs = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var worstConfidence = 1f
        var allPreserved = true

        for (sentence in Tokenizer.sentences(input)) {
            val parse = Parser.parse(sentence, lang)
            notes.addAll(parse.notes)

            val moved = YodaTransformer.transform(parse, lang, style)
            val rendered = Renderer.render(moved, lang, parse.terminator)

            val preserved = verifyMorphology(sentence, rendered)
            if (preserved) {
                outputs.add(rendered)
                worstConfidence = minOf(worstConfidence, parse.confidence)
            } else {
                // Should be unreachable: every rule is a permutation. If it
                // ever fires, the original wins.
                allPreserved = false
                outputs.add(sentence)
                notes.add("Word set changed during movement; original kept for this sentence.")
                worstConfidence = 0f
            }
        }

        return TranslationResult(
            output = outputs.joinToString(" "),
            lang = lang,
            style = style,
            engineId = id,
            morphologyPreserved = allPreserved,
            confidence = worstConfidence,
            notes = notes.distinct()
        )
    }

    /**
     * The morphology rule, checked mechanically: the multiset of word forms in
     * the output must equal the multiset in the input. Case may change, because
     * moving a word changes which one starts the sentence. Punctuation is
     * ignored, because the transformer inserts and removes commas by design.
     */
    private fun verifyMorphology(original: String, produced: String): Boolean {
        fun bag(s: String) = Tokenizer.tokenize(s)
            .filterNot { Tokenizer.isPunctuation(it) }
            .map { it.lowercase() }
            .sorted()
        return bag(original) == bag(produced)
    }
}
