package com.example.rallytester

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class ToneGenerator(private val router: UsbAudioRouter) {

    private var track: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var isPlaying = false
        private set

    fun play(frequencyHz: Double = 1_000.0, onLevel: (Float) -> Unit = {}) {
        stop()
        val bufSize = AudioTrack.getMinBufferSize(
            48_000,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(2048)

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(48_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val usbDev = router.findRallyAudio()
        router.routeOutput(t, usbDev)
        t.play()
        track = t
        isPlaying = true

        job = scope.launch {
            val buf = FloatArray(bufSize)
            var phase = 0.0
            val dPhase = 2.0 * PI * frequencyHz / 48_000
            while (isActive) {
                var rmsSum = 0.0
                for (i in buf.indices step 2) {
                    val s = (sin(phase) * 0.65f).toFloat()
                    buf[i] = s; buf[i + 1] = s
                    rmsSum += s * s
                    phase += dPhase
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                onLevel(sqrt(rmsSum / buf.size).toFloat())
            }
        }
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        runCatching { track?.pause(); track?.flush() }
        track?.release()
        track = null
    }
}
