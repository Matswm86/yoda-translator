package no.mwm.yoda.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import no.mwm.yoda.R
import kotlin.random.Random

/** One of Yoda's film recordings. [text] is shown while a quote plays; murmurs have none. */
class YodaClip(@RawRes val res: Int, val millis: Long, val text: String? = null)

/**
 * Yoda's own voice, cut from the films: murmurs and a laugh for idle moments,
 * and whole lines he says when you tap him. Everything is preloaded into a
 * SoundPool, so a clip starts without delay.
 */
class YodaSounds(context: Context) {

    val murmurs = listOf(
        YodaClip(R.raw.yoda_hm_1, 2150),
        YodaClip(R.raw.yoda_hm_2, 1620),
    )
    val laughs = listOf(
        YodaClip(R.raw.yoda_laugh_1, 960),
        YodaClip(R.raw.yoda_laugh_2, 2400),
        YodaClip(R.raw.yoda_laugh_3, 1790),
        YodaClip(R.raw.yoda_laugh_4, 3500),
        YodaClip(R.raw.yoda_laugh_5, 6500),
        YodaClip(R.raw.yoda_laugh_6, 2260),
    )
    private val idle = murmurs + laughs

    /** An idle sound: a hum half the time, a laugh the other half. */
    fun nextIdle(): YodaClip = if (Random.nextBoolean()) murmurs.random() else laughs.random()
    val quotes = listOf(
        YodaClip(R.raw.yoda_q_900, 3870, "When nine hundred years old you reach, look as good you will not."),
        YodaClip(R.raw.yoda_q_size, 5570, "Size matters not. Look at me. Judge me by my size, do you?"),
        YodaClip(R.raw.yoda_q_luminous, 3190, "Luminous beings are we, not this crude matter."),
        YodaClip(R.raw.yoda_q_forever, 2710, "Forever will it dominate your destiny."),
        YodaClip(R.raw.yoda_q_grave, 4570, "Nevertheless, grave danger I fear in his training."),
        YodaClip(R.raw.yoda_q_notraining, 2070, "No more training you require."),
        YodaClip(R.raw.yoda_q_craves, 2750, "A Jedi craves not these things."),
    )

    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()
    private val ids: Map<Int, Int> =
        (idle + quotes).associate { it.res to pool.load(context, it.res, 1) }

    private val prefs = context.getSharedPreferences("yoda", Context.MODE_PRIVATE)
    var muted: Boolean = prefs.getBoolean("muted", false)
        set(value) {
            field = value
            prefs.edit().putBoolean("muted", value).apply()
            if (value) pool.autoPause()
        }

    /** Plays [clip] over whatever is playing. Returns false when muted. */
    fun play(clip: YodaClip): Boolean {
        if (muted) return false
        val id = ids[clip.res] ?: return false
        pool.autoPause()
        pool.play(id, 1f, 1f, 1, 0, 1f)
        return true
    }

    fun stop() = pool.autoPause()

    fun release() = pool.release()
}
