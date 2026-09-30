package no.mwm.yoda.engine.rule

import no.mwm.yoda.engine.Lang

/** One clause-sized chunk of a sentence. */
data class Segment(
    val tokens: List<Token>,
    /** Leading `hvis` / `if` / `og` / `and`, held aside so it never gets moved. */
    val introducer: Token?,
    val isDependent: Boolean
) {
    val hasFiniteVerb: Boolean get() = tokens.any { it.tag.isFinite }
}

/**
 * The main clause, cut into the four pieces the movement rules operate on.
 *
 * `Du vil lære tålmodighet` splits as
 *   subject      = [Du]
 *   finiteVerb   = [vil]
 *   verbCluster  = [lære]
 *   complements  = [tålmodighet]
 */
data class ClauseParse(
    val subject: List<Token>,
    val finiteVerb: List<Token>,
    /** Negation and mid-field adverbs: `ikke`, `alltid`, `not`. Placed per style. */
    val midfield: List<Token>,
    val verbCluster: List<Token>,
    val complements: List<Token>
) {
    val isUsable: Boolean get() = finiteVerb.isNotEmpty() && subject.isNotEmpty()
}

data class SentenceParse(
    val pre: List<Segment>,
    val main: Segment?,
    val post: List<Segment>,
    val mainParse: ClauseParse?,
    val terminator: String,
    val confidence: Float,
    val notes: List<String>
)

object Parser {

    /** Adverbs that sit in the Norwegian/English mid-field and belong with the finite verb. */
    private val MIDFIELD = setOf(
        "ikke", "aldri", "alltid", "ofte", "bare", "kun", "også", "nesten",
        "allerede", "fortsatt", "jo", "nok", "vel",
        "not", "never", "always", "often", "just", "only", "also", "almost",
        "already", "still", "ever"
    )

    fun parse(sentence: String, lang: Lang): SentenceParse {
        val notes = mutableListOf<String>()
        val raw = Tokenizer.tokenize(sentence)
        if (raw.isEmpty()) {
            return SentenceParse(emptyList(), null, emptyList(), null, "", 0f, listOf("Empty input."))
        }

        var tokens = Tagger.tag(raw, lang)

        // Peel the sentence terminator off the end so movement never steps on it.
        var terminator = ""
        while (tokens.isNotEmpty() && Tokenizer.isSentenceTerminator(tokens.last().surface)) {
            terminator = tokens.last().surface + terminator
            tokens = tokens.dropLast(1)
        }
        if (tokens.isEmpty()) {
            return SentenceParse(emptyList(), null, emptyList(), null, terminator, 0f, listOf("No words."))
        }

        val segments = splitIntoSegments(tokens)
        val mainIdx = segments.indexOfFirst { !it.isDependent && it.hasFiniteVerb }
            .let { if (it >= 0) it else segments.indexOfFirst { s -> s.hasFiniteVerb } }

        if (mainIdx < 0) {
            notes.add("No finite verb found; sentence left as written.")
            return SentenceParse(segments, null, emptyList(), null, terminator, 0.2f, notes)
        }

        val main = segments[mainIdx]
        val pre = segments.subList(0, mainIdx)
        val post = segments.subList(mainIdx + 1, segments.size)

        val mainParse = cutClause(main.tokens)
        var confidence = 1.0f

        if (mainParse.subject.isEmpty()) {
            confidence -= 0.25f
            notes.add("No subject before the finite verb (imperative or inverted source).")
        }
        if (mainParse.verbCluster.isEmpty() && mainParse.complements.isEmpty()) {
            confidence -= 0.30f
            notes.add("Nothing after the finite verb to move.")
        }
        val words = tokens.count { it.isWord }
        val unknown = tokens.count { it.isWord && !it.known }
        if (words > 0 && unknown.toFloat() / words > 0.45f) {
            confidence -= 0.20f
            notes.add("$unknown of $words words are outside the lexicon; tags are suffix guesses.")
        }
        if (main.introducer != null) {
            confidence -= 0.10f
            notes.add("Main clause is introduced by \"${main.introducer.surface}\".")
        }

        return SentenceParse(pre, main, post, mainParse, terminator, confidence.coerceIn(0f, 1f), notes)
    }

    /**
     * Cuts at commas, subordinators and coordinators. Commas are dropped here;
     * the transformer inserts its own, and the morphology check ignores
     * punctuation, so no word is lost either way.
     */
    private fun splitIntoSegments(tokens: List<Token>): List<Segment> {
        val segments = mutableListOf<Segment>()
        var current = mutableListOf<Token>()
        var introducer: Token? = null

        fun flush() {
            if (current.isNotEmpty() || introducer != null) {
                segments.add(Segment(current.toList(), introducer, introducer?.tag == Tag.SUBORD))
            }
            current = mutableListOf()
            introducer = null
        }

        for ((idx, t) in tokens.withIndex()) {
            val isLast = idx == tokens.lastIndex
            val prevTag = tokens.getOrNull(idx - 1)?.tag
            // "siden", "om", "for", "da" are subordinators only when they open
            // a real clause. Last word in the sentence, or sitting on a
            // determiner or adjective, and they are ordinary nouns instead.
            // A coordinator opens a clause only when a finite verb follows
            // before the next boundary. Otherwise it joins two phrases that
            // move as one block: "grådig og lat", "for you all morning".
            val clauseOpener = (t.tag == Tag.SUBORD || t.tag == Tag.COORD) &&
                !isLast && prevTag != Tag.DET && prevTag != Tag.ADJ &&
                (t.tag == Tag.SUBORD || finiteVerbAhead(tokens, idx + 1) ||
                    isPurposeClause(t, tokens.getOrNull(idx + 1)))

            when {
                t.surface == "," || t.surface == ";" || t.surface == ":" -> flush()

                clauseOpener && current.any { it.tag.isFinite } -> {
                    flush()
                    introducer = t
                }

                clauseOpener && current.isEmpty() && introducer == null -> {
                    introducer = t
                }

                // A fronted subordinate clause with no comma:
                // "Hvis du trener hardt vil du lære" -> cut before the second
                // finite verb, which is where the main clause actually starts.
                // Only inside a subordinate clause; elsewhere a second finite
                // verb is usually a complement clause that reads better whole
                // ("Jeg tror du vinner" -> "Du vinner jeg tror").
                introducer?.tag == Tag.SUBORD && t.tag.isFinite && !isLast &&
                    current.any { it.tag.isFinite } -> {
                    flush()
                    current.add(t)
                }

                t.tag == Tag.PUNCT -> Unit // parentheses, quotes: dropped, not words

                else -> current.add(t)
            }
        }
        flush()
        return segments.filter { it.tokens.isNotEmpty() || it.introducer != null }
    }

    /** Norwegian "for å nå toget": a purpose clause, cut like a subordinate one. */
    private fun isPurposeClause(t: Token, next: Token?): Boolean =
        t.lower == "for" && next?.lower == "å"

    /** True if a finite verb appears from [from] up to the next comma, subordinator or coordinator. */
    private fun finiteVerbAhead(tokens: List<Token>, from: Int): Boolean {
        for (i in from until tokens.size) {
            val t = tokens[i]
            if (t.surface == "," || t.surface == ";" || t.surface == ":") return false
            if (t.tag == Tag.SUBORD || t.tag == Tag.COORD) return false
            if (t.tag.isFinite) return true
            // English past tense the lexicon lacks, close to the coordinator:
            // "and finished the pizza", "and the door slammed".
            val prev = tokens.getOrNull(i - 1)
            if (i - from <= 3 && t.lower.endsWith("ed") && !t.known &&
                (i == from || (prev != null && prev.tag in setOf(Tag.NOUN, Tag.PROPN, Tag.PRON)))
            ) return true
        }
        return false
    }

    /** Splits one clause into subject / finite verb / verb cluster / complements. */
    private fun cutClause(tokens: List<Token>): ClauseParse {
        val fvIdx = tokens.indexOfFirst { it.tag.isFinite }
        if (fvIdx < 0) {
            return ClauseParse(emptyList(), emptyList(), emptyList(), emptyList(), tokens)
        }

        val finiteVerb = listOf(tokens[fvIdx])
        var subject = tokens.subList(0, fvIdx).toList()
        var cursor = fvIdx + 1

        // Norwegian V2 inversion after a fronted adverbial leaves the subject
        // behind the finite verb: "Hvis du trener hardt, VIL DU lære."
        // Without this the clause looks subject-less and gets scrambled.
        if (subject.isEmpty()) {
            val recovered = recoverInvertedSubject(tokens, cursor)
            if (recovered > cursor) {
                subject = tokens.subList(cursor, recovered).toList()
                cursor = recovered
            }
        }

        // Negation and mid-field adverbs travel as their own block.
        var mf = cursor
        while (mf < tokens.size &&
            (tokens[mf].tag == Tag.NEG || tokens[mf].lower in MIDFIELD)
        ) mf++
        val midfield = tokens.subList(cursor, mf).toList()

        // Then a run of non-finite verb material: å / to, infinitives, participles.
        var vc = mf
        while (vc < tokens.size &&
            (tokens[vc].tag.isNonFinite || tokens[vc].tag == Tag.INF_MARK)
        ) vc++
        val verbCluster = tokens.subList(mf, vc).toMutableList()
        val mid = midfield.toMutableList()

        // A verb particle stays with its verb: "found out", "tok frem",
        // "brukt opp". With no non-finite verb it joins the mid-field, which
        // every style places right behind the finite verb.
        if (isParticle(tokens, vc)) {
            if (verbCluster.isEmpty()) mid.add(tokens[vc]) else verbCluster.add(tokens[vc])
            vc++
        }

        val complements = tokens.subList(vc, tokens.size).toList()
        return ClauseParse(subject, finiteVerb, mid, verbCluster, complements)
    }

    private val PARTICLES = setOf(
        "out", "up", "away", "back", "down",
        "frem", "fram", "opp", "ut", "bort", "igjen", "tilbake", "ned"
    )

    /**
     * A particle word not heading a prepositional phrase of its own:
     * "ut av huset", "out of money" and "up to the door" keep the particle
     * with the phrase.
     */
    private fun isParticle(tokens: List<Token>, i: Int): Boolean {
        val t = tokens.getOrNull(i) ?: return false
        if (t.lower !in PARTICLES) return false
        val next = tokens.getOrNull(i + 1)
        return next == null ||
            (next.tag != Tag.PREP && next.tag != Tag.INF_MARK && next.lower != "of")
    }

    /**
     * Returns the index just past an inverted subject sitting after the finite
     * verb, or [from] if there is nothing that looks like one. A pronoun is
     * taken on its own; a full noun phrase only up to three words, to avoid
     * swallowing the object.
     */
    private fun recoverInvertedSubject(tokens: List<Token>, from: Int): Int {
        if (from >= tokens.size) return from
        if (tokens[from].tag == Tag.PRON) return from + 1

        var i = from
        var sawHead = false
        while (i < tokens.size && i - from < 3) {
            when (tokens[i].tag) {
                Tag.DET, Tag.ADJ -> i++
                Tag.NOUN, Tag.PROPN -> { i++; sawHead = true; break }
                else -> break
            }
        }
        // Only an inverted subject if a verb actually follows it.
        val followedByVerb = i < tokens.size &&
            (tokens[i].tag.isNonFinite || tokens[i].tag == Tag.NEG ||
                tokens[i].lower in MIDFIELD)
        return if (sawHead && followedByVerb) i else from
    }
}
