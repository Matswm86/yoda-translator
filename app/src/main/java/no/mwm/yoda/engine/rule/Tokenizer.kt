package no.mwm.yoda.engine.rule

/** Splits raw text into sentences, and sentences into surface tokens. */
object Tokenizer {

    private val TOKEN = Regex(
        """[\p{L}][\p{L}\p{M}]*(?:['’\-][\p{L}\p{M}]+)*|\d+(?:[.,:]\d+)*|\S"""
    )

    private val SENTENCE_END = setOf(".", "!", "?", "…")

    /**
     * Splits on sentence-final punctuation, keeping the punctuation attached to
     * the sentence it closes. Trailing text with no final punctuation still
     * comes back as a sentence.
     */
    fun sentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            buf.append(c)
            if (c.toString() in SENTENCE_END) {
                // Absorb a run of terminators, e.g. "?!" or "...".
                while (i + 1 < text.length && text[i + 1].toString() in SENTENCE_END) {
                    i++
                    buf.append(text[i])
                }
                out.add(buf.toString())
                buf.clear()
            }
            i++
        }
        if (buf.isNotBlank()) out.add(buf.toString())
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Tokenises one sentence. Tags are filled in later by [Tagger]. */
    fun tokenize(sentence: String): List<String> =
        TOKEN.findAll(sentence).map { it.value }.toList()

    fun isPunctuation(s: String): Boolean =
        s.isNotEmpty() && s.none { it.isLetterOrDigit() }

    fun isSentenceTerminator(s: String): Boolean = s in SENTENCE_END
}
