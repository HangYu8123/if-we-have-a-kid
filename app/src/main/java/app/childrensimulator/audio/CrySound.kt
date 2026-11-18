package app.childrensimulator.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import app.childrensimulator.R
import app.childrensimulator.sim.engine.DistressLevel

/**
 * Optional in-app crying audio (docs/12 Phase 1 "optional in-app sound"). Loops a synthesized clip that matches
 * the visible distress. Only plays while the app is open; text cues always carry the same information.
 */
class CrySound(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private var playingSound = 0
    private var stream = 0
    private var lastVolume = 0.8f
    private val fuss = pool.load(context, R.raw.cry_fuss, 1)
    private val cry = pool.load(context, R.raw.cry_cry, 1)
    private val hard = pool.load(context, R.raw.cry_hard, 1)

    init {
        // A clip requested before it finished loading starts as soon as it is ready.
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(this) {
                if (status == 0 && sampleId == playingSound && stream == 0) {
                    stream = pool.play(sampleId, lastVolume, lastVolume, 1, -1, 1f)
                }
            }
        }
    }

    @Synchronized
    fun update(level: DistressLevel?, enabled: Boolean, quiet: Boolean) {
        val target = if (!enabled || level == null) 0 else when (level) {
            DistressLevel.FUSSING -> fuss
            DistressLevel.CRYING -> cry
            DistressLevel.HARD_CRYING -> hard
            else -> 0
        }
        val volume = if (quiet) 0.35f else 0.8f
        lastVolume = volume
        if (target == playingSound && (stream != 0 || target == 0)) {
            if (stream != 0) pool.setVolume(stream, volume, volume)
            return
        }
        if (stream != 0) pool.stop(stream)
        stream = 0
        playingSound = target
        if (target != 0) stream = pool.play(target, volume, volume, 1, -1, 1f)
    }

    @Synchronized
    fun stop() {
        if (stream != 0) pool.stop(stream)
        stream = 0
        playingSound = 0
    }
}
