package no.mwm.yoda.engine

/** Source language of the text being bent into Yoda-speak. */
enum class Lang(val code: String, val label: String) {
    EN("en", "English"),
    NO("no", "Norsk")
}

/**
 * How far to bend the syntax.
 *
 * The rule engine defaults to CLASSIC, the shape a native-speaker reviewer
 * passed on 2026-09-10. The other two stay for comparison. The on-device model
 * does not take a style: it writes the way its teacher did.
 */
enum class YodaStyle(val label: String, val blurb: String) {
    CLASSIC(
        "Classic Yoda",
        "Front the whole verb phrase, subject and finite verb trail behind. The chosen style."
    ),
    GRAMMATICAL(
        "Grammatical",
        "Front the object, keep the sentence legal. Norwegian keeps V2."
    ),
    FULL_INVERSION(
        "Full inversion",
        "Push the finite verb to the very end. Norwegian V2 is deliberately broken."
    )
}

/** Where a piece of output came from, so the UI can be honest about it. */
data class TranslationResult(
    val output: String,
    val lang: Lang,
    val style: YodaStyle,
    val engineId: String,
    /** True when every word form in the output also occurs in the input. */
    val morphologyPreserved: Boolean,
    /** Parser confidence, 0..1. Low means the movement rules had to guess. */
    val confidence: Float,
    /** Human-readable remarks: parse fallbacks, unrecognised structure, etc. */
    val notes: List<String> = emptyList()
)

interface TranslationEngine {
    val id: String
    val displayName: String

    /** False when the engine's backing resources are missing (e.g. no model file). */
    fun isAvailable(): Boolean

    /** Reason the engine is unavailable, or null when it is ready. */
    fun unavailableReason(): String?

    fun translate(input: String, lang: Lang, style: YodaStyle): TranslationResult
}
