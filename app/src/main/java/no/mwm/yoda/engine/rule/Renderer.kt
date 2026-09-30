package no.mwm.yoda.engine.rule

import no.mwm.yoda.engine.Lang

/** Turns a moved token list back into text, fixing only spacing and case. */
object Renderer {

    private val NO_SPACE_BEFORE = setOf(",", ".", "!", "?", ";", ":", ")", "]", "}", "…", "%")
    private val NO_SPACE_AFTER = setOf("(", "[", "{")

    fun render(tokens: List<Token>, lang: Lang, terminator: String): String {
        if (tokens.isEmpty()) return terminator

        val cleaned = dropLeadingAndDoubledCommas(tokens)
        val firstWord = cleaned.indexOfFirst { it.isWord }
        val sb = StringBuilder()

        cleaned.forEachIndexed { i, t ->
            var text = t.surface

            // A word that was only capitalised for being first goes back to
            // lower case once something else leads, unless it is a name.
            // A capitalised word right behind it means a multi-word name
            // ("Thor Heyerdahl", "Mette Marits"), so the capital stays.
            val nextIsCapitalised = cleaned.getOrNull(i + 1)
                ?.let { it.isWord && it.surface.first().isUpperCase() } ?: false
            if (t.wasSentenceInitial && i != firstWord && !nextIsCapitalised &&
                shouldLowercase(t, lang)
            ) {
                text = text.replaceFirstChar { it.lowercase() }
            }
            // English "I" is capital wherever it lands.
            if (lang == Lang.EN && t.isWord && isEnglishI(text)) {
                text = text.replaceFirstChar { it.uppercaseChar() }
            }
            if (i == firstWord) {
                text = text.replaceFirstChar { it.uppercaseChar() }
            }

            val prev = cleaned.getOrNull(i - 1)
            val needsSpace = sb.isNotEmpty() &&
                text !in NO_SPACE_BEFORE &&
                (prev == null || prev.surface !in NO_SPACE_AFTER)
            if (needsSpace) sb.append(' ')
            sb.append(text)
        }

        return sb.toString() + terminator
    }

    /**
     * Whether a word that used to start the sentence loses its capital when it
     * moves inward. Anything in the gazetteer, or the genitive of it
     * (`Toms`, `Tom's`), never does. Known lexicon words always do.
     *
     * Unknown words split by language. Norwegian writes common nouns and
     * adverbs in lower case, so an unknown Norwegian word that is not a listed
     * name loses its capital (`Kjernekraft`, `Vennligst`). English sentence
     * openers without a determiner are mostly names in practice (`Yanni`,
     * `Mennad`), so an unknown English word keeps its capital unless it is a
     * contraction of a known word (`We've`, `Don't`).
     */
    private fun shouldLowercase(t: Token, lang: Lang): Boolean {
        val w = t.lower
        if (Lexicon.isProperNoun(w) || Lexicon.isProperNoun(genitiveStem(w))) return false
        if (t.surface.drop(1).any { it.isUpperCase() }) return false
        if (t.known) return true
        if (Lexicon.isKnownStem(w, lang)) return true
        return when (lang) {
            Lang.NO -> true
            Lang.EN -> contractionStem(w)?.let { Lexicon.isKnownWord(it, lang) } ?: false
        }
    }

    /** `i`, `i'm`, `i've`, `i'll`, `i'd`. */
    private fun isEnglishI(text: String): Boolean {
        val w = text.lowercase()
        return w == "i" || w.startsWith("i'") || w.startsWith("i’")
    }

    private fun genitiveStem(w: String): String = when {
        w.endsWith("'s") || w.endsWith("’s") -> w.dropLast(2)
        w.endsWith("s") -> w.dropLast(1)
        else -> w
    }

    /** `we've` -> `we`, `don't` -> `do`, `let's` -> `let`; null if no apostrophe. */
    private fun contractionStem(w: String): String? {
        val nt = listOf("n't", "n’t").firstOrNull { w.endsWith(it) }
        if (nt != null) return w.removeSuffix(nt)
        val cut = w.indexOfFirst { it == '\'' || it == '’' }
        return if (cut > 0) w.substring(0, cut) else null
    }

    /** Movement can leave a comma at the front or two in a row. Tidy those. */
    private fun dropLeadingAndDoubledCommas(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        for (t in tokens) {
            if (t.surface == ",") {
                if (out.isEmpty()) continue
                if (out.last().surface == ",") continue
                if (out.none { it.isWord }) continue
            }
            out.add(t)
        }
        while (out.isNotEmpty() && out.last().surface == ",") out.removeAt(out.size - 1)
        return out
    }
}
