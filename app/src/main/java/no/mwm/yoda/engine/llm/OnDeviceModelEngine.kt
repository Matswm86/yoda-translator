package no.mwm.yoda.engine.llm

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.TranslationEngine
import no.mwm.yoda.engine.TranslationResult
import no.mwm.yoda.engine.YodaStyle
import no.mwm.yoda.engine.rule.Tokenizer
import java.io.File

/**
 * The fine-tuned Gemma 3 1B, run on the phone through LiteRT-LM.
 *
 * The model was trained on Claude Sonnet's Yoda lines (9,968 pairs, see
 * corpus/README.md) with exactly [INSTRUCTION] followed by one sentence, so input is
 * split into sentences and each goes through on its own. Decoding is greedy:
 * the same sentence always gets the same answer.
 *
 * The model file is ~1 GB and does not ship in the APK. [importModel] copies a
 * `.litertlm` file the user picked into app storage.
 *
 * [translate] blocks for seconds (the first call also loads the model), so
 * call it off the main thread.
 */
class OnDeviceModelEngine(private val context: Context) : TranslationEngine {

    override val id = "gemma3-1b-yoda"
    override val displayName = "On-device model (Gemma 3 1B)"

    val modelFile: File
        get() = File(context.filesDir, "models/$MODEL_NAME")

    private val lock = Any()
    private var engine: Engine? = null

    override fun isAvailable(): Boolean = modelFile.exists()

    override fun unavailableReason(): String? =
        if (modelFile.exists()) null
        else "No model file yet. Import $MODEL_NAME with the button below."

    override fun translate(input: String, lang: Lang, style: YodaStyle): TranslationResult {
        val notes = mutableListOf<String>()
        val out = try {
            val e = loadedEngine()
            Tokenizer.sentences(input).joinToString(" ") { yodaOne(e, it) }
        } catch (t: Throwable) {
            notes.add("Model failed: ${t.message ?: t.javaClass.simpleName}")
            input
        }
        return TranslationResult(
            output = out,
            lang = lang,
            style = style,
            engineId = id,
            // Word forms may change in this engine by design (review ruling, 2026-09-28).
            morphologyPreserved = true,
            confidence = if (notes.isEmpty()) 1f else 0f,
            notes = notes
        )
    }

    private fun yodaOne(e: Engine, sentence: String): String {
        val config = ConversationConfig(samplerConfig = SamplerConfig(1, 1.0, 0.0, 0))
        e.createConversation(config).use { conversation ->
            val reply = conversation.sendMessage("$INSTRUCTION\n$sentence")
            val text = reply.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
            return text.ifEmpty { sentence }
        }
    }

    private fun loadedEngine(): Engine = synchronized(lock) {
        engine ?: Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path
            )
        ).also {
            it.initialize()
            engine = it
        }
    }

    /** Copies a picked `.litertlm` file into app storage, replacing any earlier model. */
    fun importModel(uri: Uri) {
        synchronized(lock) {
            engine?.close()
            engine = null
            modelFile.parentFile?.mkdirs()
            val tmp = File(modelFile.path + ".part")
            context.contentResolver.openInputStream(uri).use { src ->
                requireNotNull(src) { "Could not open the picked file." }
                tmp.outputStream().use { dst -> src.copyTo(dst, 1 shl 20) }
            }
            if (!tmp.renameTo(modelFile)) error("Could not move the model into place.")
        }
    }

    companion object {
        const val MODEL_NAME = "yoda-gemma3-1b.litertlm"

        /** Must match INSTRUCTION in train/train_lora.py; the model was trained on it. */
        const val INSTRUCTION = "Rewrite as Yoda speaks. Keep the language and the meaning."
    }
}
