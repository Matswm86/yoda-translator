package no.mwm.yoda.engine.rule

/**
 * Coarse word classes. Only as fine-grained as the movement rules need:
 * the transformer has to find a subject, a finite verb, a non-finite verb
 * cluster and everything else. It never needs to know case or gender,
 * because it never rewrites a word form.
 */
enum class Tag {
    PRON,
    PROPN,
    NOUN,
    DET,
    ADJ,
    ADV,
    NEG,
    MODAL,
    AUX,
    VERB_FIN,
    VERB_INF,
    VERB_PART,
    INF_MARK,
    PREP,
    SUBORD,
    COORD,
    NUM,
    PUNCT,
    UNKNOWN;

    val isVerbal: Boolean
        get() = this == MODAL || this == AUX || this == VERB_FIN ||
                this == VERB_INF || this == VERB_PART

    val isFinite: Boolean
        get() = this == MODAL || this == AUX || this == VERB_FIN

    val isNonFinite: Boolean
        get() = this == VERB_INF || this == VERB_PART
}

/**
 * A single token. [surface] is the byte-exact original substring and is never
 * rewritten by any rule; only its position in the sentence changes. That is
 * the whole mechanism behind the morphology guarantee.
 */
data class Token(
    val surface: String,
    val tag: Tag,
    /** True if this token opened the original sentence (drives recapitalisation). */
    val wasSentenceInitial: Boolean = false,
    /** False when no lexicon entry matched and the tag came from a suffix rule. */
    val known: Boolean = true
) {
    val lower: String get() = surface.lowercase()
    val isWord: Boolean get() = tag != Tag.PUNCT
}
