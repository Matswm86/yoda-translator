package no.mwm.yoda.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume

/**
 * Yoda saying any sentence, rendered by Fish Audio through the relay at
 * yoda.mwmai.no. The relay holds the API key, so the app carries none.
 * Each rendered sentence is cached on the phone and plays offline afterwards.
 */
class YodaVoice(context: Context) {

    /** Why the voice could not speak, in words for the speech bubble. */
    class VoiceError(message: String) : Exception(message)

    private val cacheDir = File(context.cacheDir, "voice").apply { mkdirs() }
    private var player: MediaPlayer? = null

    private fun cacheFile(text: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return File(cacheDir, hash.joinToString("") { "%02x".format(it) } + ".mp3")
    }

    /** Returns the MP3 for [text], from the phone's cache or the relay. */
    suspend fun fetch(text: String): File = withContext(Dispatchers.IO) {
        val file = cacheFile(text)
        if (file.length() > 0) return@withContext file
        val conn = URL(RELAY_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("text", text).toString().toByteArray()) }
            when (conn.responseCode) {
                200 -> {
                    val tmp = File(cacheDir, file.name + ".part")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.renameTo(file)
                    file
                }
                429 -> throw VoiceError("Rested enough, my voice has not. Try again later, you must.")
                else -> throw VoiceError("Voice unavailable (server said ${conn.responseCode}).")
            }
        } catch (e: IOException) {
            throw VoiceError("Voice unavailable: no connection.")
        } finally {
            conn.disconnect()
        }
    }

    /** Plays [file] and suspends until it finishes. Cancelling stops playback. */
    suspend fun play(file: File) = suspendCancellableCoroutine { cont ->
        stop()
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
        mp.setOnErrorListener { _, _, _ ->
            if (cont.isActive) cont.resume(Unit)
            true
        }
        try {
            mp.setDataSource(file.path)
            mp.prepare()
            mp.start()
        } catch (e: IOException) {
            file.delete()
            if (cont.isActive) cont.resume(Unit)
        }
        cont.invokeOnCancellation { stop() }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    companion object {
        const val RELAY_URL = "https://yoda.mwmai.no/tts"
    }
}
