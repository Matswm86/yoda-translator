package no.mwm.yoda.engine.rule

import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.YodaStyle

/**
 * The movement rules.
 *
 * Every rule here is a permutation of the token list produced by [Parser].
 * No rule constructs, inflects, deletes or substitutes a word, which is what
 * makes the morphology guarantee structural rather than aspirational.
 *
 * Given `Du vil lære tålmodighet` cut as S=[Du] F=[vil] V=[lære] C=[tålmodighet]:
 *
 *   GRAMMATICAL     C F S V   ->  Tålmodighet vil du lære      (V2 intact)
 *   CLASSIC         V C S F   ->  Lære tålmodighet du vil      (validated 12B target)
 *   FULL_INVERSION  C S V F   ->  Tålmodighet du lære vil      (V2 deliberately broken)
 */
object YodaTransformer {

    /** A comma we insert ourselves. Punctuation, so the morphology check skips it. */
    private val COMMA = Token(",", Tag.PUNCT)

    fun transform(parse: SentenceParse, lang: Lang, style: YodaStyle): List<Token> {
        val main = parse.main
        val cp = parse.mainParse
        if (main == null || cp == null) {
            return parse.pre.flatMap { renderSegment(it) } + parse.post.flatMap { renderSegment(it) }
        }

        val bent = bendClause(cp, lang, style)
        val out = mutableListOf<Token>()

        // A clause-initial `hvis` / `and` stays put; it scopes the whole clause.
        main.introducer?.let { out.add(it) }

        for (seg in parse.pre) {
            out.addAll(renderSegment(seg))
            out.add(COMMA)
        }

        out.addAll(bent)

        for (seg in parse.post) {
            out.add(COMMA)
            out.addAll(renderSegment(seg))
        }
        return out
    }

    private fun renderSegment(seg: Segment): List<Token> =
        (seg.introducer?.let { listOf(it) } ?: emptyList()) + seg.tokens

    private fun bendClause(cp: ClauseParse, lang: Lang, style: YodaStyle): List<Token> {
        val s = cp.subject
        val f = cp.finiteVerb
        val m = cp.midfield
        val v = cp.verbCluster
        val c = cp.complements

        // Nothing after the finite verb: there is no constituent to front, so
        // leave the clause alone rather than inventing a scramble.
        if (v.isEmpty() && c.isEmpty()) return s + f + m

        // Subject-less clause (a real imperative). Front the complements.
        if (s.isEmpty()) return c + m + v + f

        return when (lang) {
            Lang.NO -> bendNorwegian(s, f, m, v, c, style)
            Lang.EN -> bendEnglish(s, f, m, v, c, style)
        }
    }

    private fun bendNorwegian(
        s: List<Token>, f: List<Token>, m: List<Token>,
        v: List<Token>, c: List<Token>, style: YodaStyle
    ): List<Token> = when (style) {
        // V2 preserved: one constituent fronted, finite verb still second, and
        // the negation lands after the inverted subject where Norwegian wants
        // it -- "Til meg vil han ikke lytte", not "vil ikke han lytte".
        YodaStyle.GRAMMATICAL ->
            if (c.isNotEmpty()) c + f + s + m + v
            else v + f + s + m

        // The shape gemma3:12b produced and the reviewer passed. No comma before the
        // subject, except before a name, where the fronted words would
        // otherwise run into it: "Grådige, Tom og Mary er",
        // "En allianse med Italia, Tyskland stiftet".
        YodaStyle.CLASSIC ->
            if (s.firstOrNull()?.let(::isName) == true) v + c + listOf(COMMA) + s + f + m
            else v + c + s + f + m

        // V2 broken on purpose: finite verb pushed to the very end.
        YodaStyle.FULL_INVERSION ->
            if (c.isNotEmpty()) c + s + m + v + f
            else v + s + m + f
    }

    private fun bendEnglish(
        s: List<Token>, f: List<Token>, m: List<Token>,
        v: List<Token>, c: List<Token>, style: YodaStyle
    ): List<Token> = when (style) {
        // OSV, the "Patience you must have" pattern.
        YodaStyle.GRAMMATICAL ->
            if (c.isNotEmpty()) c + s + f + m + v
            else v + s + f + m

        // "Learn patience, you will." / "Truly wonderful, the mind of a child is."
        // An object-control infinitive fronts only its own verb phrase:
        // "I told you to call me" -> "Call me, I told you to."
        YodaStyle.CLASSIC -> {
            val k = objectControlSplit(f, v, c)
            if (k > 0) c.subList(k + 1, c.size) + listOf(COMMA) + s + f + m + c.subList(0, k + 1)
            else v + c + listOf(COMMA) + s + f + m
        }

        YodaStyle.FULL_INVERSION ->
            if (c.isNotEmpty()) c + s + v + f + m
            else v + s + f + m
    }

    /**
     * A listed name, or a capitalised word that did not open the sentence.
     * Sentence-initial unknown words are tagged PROPN only for their capital,
     * so the tag alone does not mark a name.
     */
    private fun isName(t: Token): Boolean {
        val w = t.lower
        if (Lexicon.isProperNoun(w) || (w.endsWith("s") && Lexicon.isProperNoun(w.dropLast(1)))) return true
        return !t.wasSentenceInitial && t.surface.first().isUpperCase()
    }

    private val OBJECT_NP = setOf(Tag.PRON, Tag.NOUN, Tag.PROPN, Tag.DET)

    /** Verbs whose object is the subject of the following infinitive. */
    private val CONTROL_VERBS = setOf(
        "tell", "tells", "told",
        "ask", "asks", "asked",
        "want", "wants", "wanted",
        "expect", "expects", "expected",
        "need", "needs", "needed",
        "allow", "allows", "allowed",
        "force", "forces", "forced",
        "order", "orders", "ordered",
        "remind", "reminds", "reminded",
        "beg", "begs", "begged",
        "advise", "advises", "advised",
        "encourage", "encourages", "encouraged",
        "persuade", "persuades", "persuaded",
        "invite", "invites", "invited",
        "warn", "warns", "warned",
        "urge", "urges", "urged"
    )

    /**
     * Index of the `to` in "told [you] to [call me]": a control verb, then a
     * short object noun phrase, then `to` and a verb. Returns -1 otherwise,
     * which keeps "the chance to do that" and "going to talk" whole.
     */
    private fun objectControlSplit(f: List<Token>, v: List<Token>, c: List<Token>): Int {
        if (v.isNotEmpty() || f.firstOrNull()?.lower !in CONTROL_VERBS) return -1
        val k = c.indexOfFirst { it.tag == Tag.INF_MARK }
        if (k !in 1..3 || k + 1 >= c.size || c[k + 1].tag != Tag.VERB_INF) return -1
        return if (c.subList(0, k).all { it.tag in OBJECT_NP }) k else -1
    }
}
