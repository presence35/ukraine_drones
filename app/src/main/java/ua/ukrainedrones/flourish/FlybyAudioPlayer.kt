package ua.ukrainedrones

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Manages the looping jet-engine sound for the MiG-31K flyby.
 * Uses [MediaPlayer] with USAGE_MEDIA so it respects device volume and DND.
 */
class FlybyAudioPlayer private constructor(
    private val context: Context,
    private val mediaPlayer: MediaPlayer
) {

    /** Starts (or restarts) the looping engine sound. */
    fun start() {
        mediaPlayer.seekTo(0)
        mediaPlayer.start()
    }

    /** Stops the sound and releases resources. */
    fun stop() {
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        mediaPlayer.release()
    }

    /** Factory: prepares a looping [MediaPlayer] for the engine sound. */
    companion object {
        private val RES_ID = R.raw.mig_engine

        fun create(context: Context): FlybyAudioPlayer {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val mp = MediaPlayer.create(context, RES_ID).apply {
                isLooping = true
                setAudioAttributes(attrs)
            }
            return FlybyAudioPlayer(context, mp)
        }
    }
}