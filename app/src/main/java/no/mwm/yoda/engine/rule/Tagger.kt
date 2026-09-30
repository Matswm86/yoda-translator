package no.mwm.yoda.engine.rule

import no.mwm.yoda.engine.Lang

/**
 * Two-pass tagger.
 *
 * Pass 1 is pure lexicon lookup, which is exact for the closed classes.
 * Pass 2 resolves the leftovers positionally: a word directly after a modal
 * is an infinitive, a word directly after a perfect auxiliary is a participle,
 * a word directly after the subject is the finite verb. Positional rules are
 * what keep Norwegian `-er` nouns (biler, hunder) from being read as verbs.
 */
object Tagger {

    fun tag(surfaces: List<String>, lang: Lang): List<Token> {
        val first = pass1(surfaces, lang)
        return pass2(first, lang)
    }

    // ------------------------------------------------------------------ pass 1

    private fun pass1(surfaces: List<String>, lang: Lang): MutableList<Token> {
        val out = mutableListOf<Token>()
        var seenWord = false
        for (s in surfaces) {
            if (Tokenizer.isPunctuation(s)) {
                out.add(Token(s, Tag.PUNCT))
                continue
            }
            val w = s.lowercase()
            val initial = !seenWord
            seenWord = true
            out.add(Token(s, lookup(w, s, lang), initial, Lexicon.isKnownWord(w, lang)))
        }
        return out
    }

    private fun lookup(w: String, surface: String, lang: Lang): Tag {
        // English contractions carry the finiteness of their stem.
        if (lang == Lang.EN && (w.endsWith("n't") || w.endsWith("n’t"))) {
            val stem = w.removeSuffix("n't").removeSuffix("n’t")
            return if (Lexicon.isModal(stem, lang) || stem in setOf("wo", "ca", "sha"))
                Tag.MODAL else Tag.AUX
        }
        return when {
            Lexicon.isInfinitiveMarker(w, lang) -> Tag.INF_MARK
            Lexicon.isNegation(w, lang) -> Tag.NEG
            Lexicon.isModal(w, lang) -> Tag.MODAL
            Lexicon.isAux(w, lang) -> Tag.AUX
            Lexicon.isSubordinator(w, lang) -> Tag.SUBORD
            Lexicon.isCoordinator(w, lang) -> Tag.COORD
            Lexicon.isPronoun(w, lang) -> Tag.PRON
            Lexicon.isDeterminer(w, lang) -> Tag.DET
            Lexicon.isPreposition(w, lang) -> Tag.PREP
            Lexicon.isFiniteVerb(w, lang) -> Tag.VERB_FIN
            Lexicon.isInfinitive(w, lang) -> Tag.VERB_INF
            Lexicon.isAdverb(w, lang) -> Tag.ADV
            w.all { it.isDigit() || it == '.' || it == ',' } -> Tag.NUM
            surface.isNotEmpty() && surface[0].isUpperCase() -> Tag.PROPN
            else -> Tag.UNKNOWN
        }
    }

    // ------------------------------------------------------------------ pass 2

    private fun pass2(tokens: MutableList<Token>, lang: Lang): List<Token> {
        val out = tokens.toMutableList()
        var sawFiniteVerb = false

        for (i in out.indices) {
            val t = out[i]
            if (t.tag == Tag.PUNCT) continue
            val prev = out.getOrNull(i - 1)?.takeIf { it.tag != Tag.PUNCT }
            val w = t.lower

            // A capitalised sentence-initial word is not a proper noun just
            // because it starts the sentence. Re-read it in lower case.
            if (t.tag == Tag.PROPN && t.wasSentenceInitial) {
                val relookup = lookup(w, w, lang)
                if (relookup != Tag.PROPN && relookup != Tag.UNKNOWN) {
                    out[i] = t.copy(tag = relookup)
                }
            }

            val cur = out[i]

            // After a modal or an infinitive marker the verb is non-finite,
            // including auxiliaries: "must have", "vil være". Without this the
            // auxiliary reads as the finite verb and the clause is cut wrong.
            if (prev != null && (prev.tag == Tag.MODAL || prev.tag == Tag.INF_MARK)) {
                if (cur.tag == Tag.VERB_INF) continue
                val couldBeVerb = cur.tag == Tag.UNKNOWN || cur.tag == Tag.VERB_FIN ||
                    cur.tag == Tag.AUX || cur.tag == Tag.MODAL || cur.tag == Tag.PROPN
                if (couldBeVerb && (looksInfinitive(w, lang) || cur.tag != Tag.UNKNOWN)) {
                    out[i] = cur.copy(tag = Tag.VERB_INF)
                    continue
                }
            }

            // After a perfect auxiliary (har/have), the next verb is a participle.
            if (prev != null && prev.tag == Tag.AUX && isPerfectAux(prev.lower, lang)) {
                if ((cur.tag == Tag.UNKNOWN && looksParticiple(w, lang)) ||
                    (cur.tag == Tag.AUX && isParticipleForm(w, lang))
                ) {
                    out[i] = cur.copy(tag = Tag.VERB_PART)
                    continue
                }
            }

            // "must have been", "vil ha vært": a participle auxiliary trailing
            // another non-finite verb is itself non-finite.
            if (prev != null && prev.tag.isNonFinite &&
                cur.tag == Tag.AUX && isParticipleForm(w, lang)
            ) {
                out[i] = cur.copy(tag = Tag.VERB_PART)
                continue
            }

            // First unknown word after a subject-like token is the finite verb.
            if (!sawFiniteVerb && cur.tag == Tag.UNKNOWN && prev != null &&
                prev.tag in setOf(Tag.PRON, Tag.NOUN, Tag.PROPN) &&
                looksFinite(w, lang)
            ) {
                out[i] = cur.copy(tag = Tag.VERB_FIN)
            }

            if (out[i].tag.isFinite) sawFiniteVerb = true

            // Anything still unknown and lower-case is treated as a noun, which
            // makes it a complement the transformer can move as a block.
            if (out[i].tag == Tag.UNKNOWN) {
                out[i] = out[i].copy(tag = Tag.NOUN)
            }
        }
        return out
    }

    private fun isParticipleForm(w: String, lang: Lang) =
        if (lang == Lang.NO) w in setOf("vært", "blitt", "hatt")
        else w in setOf("been", "being", "had", "done", "gone")

    private fun isPerfectAux(w: String, lang: Lang) =
        if (lang == Lang.NO) w in setOf("har", "hadde")
        else w in setOf("have", "has", "had")

    private fun looksInfinitive(w: String, lang: Lang) =
        if (lang == Lang.NO) w.endsWith("e") || w.length <= 3 else true

    private fun looksParticiple(w: String, lang: Lang) =
        if (lang == Lang.NO) w.endsWith("t") || w.endsWith("et") || w.endsWith("dd")
        else w.endsWith("ed") || w.endsWith("en") || w.endsWith("ne")

    /**
     * Norwegian present tense is `-r`; past is `-te`, `-de`, `-et`, `-dde`.
     * English finite forms are covered by the lexicon, so this stays
     * conservative there to avoid reading plural nouns as verbs.
     */
    private fun looksFinite(w: String, lang: Lang) = if (lang == Lang.NO) {
        w.endsWith("r") || w.endsWith("te") || w.endsWith("de") ||
            w.endsWith("dde") || w.endsWith("et")
    } else {
        false
    }
}
