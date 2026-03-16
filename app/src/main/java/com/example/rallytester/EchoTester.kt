package com.example.rallytester

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class EchoTester(private val router: UsbAudioRouter) {

    companion object {
        private const val SAMPLE_RATE    = 48_000
        private const val BURST_MS       = 200
        private const val LISTEN_MS      = 2_000
        private const val TONE_FREQ      = 1_000.0
        private const val THRESHOLD      = 0.06f
        private const val WINDOW         = 256
    }

    data class EchoResult(val detected: Boolean, val latencyMs: Int, val message: String)

    suspend fun run(): EchoResult = withContext(Dispatchers.IO) {
        val burstSamples  = SAMPLE_RATE * BURST_MS  / 1000
        val listenSamples = SAMPLE_RATE * LISTEN_MS / 1000

        // Build burst
        val burstBuf = FloatArray(burstSamples * 2)
        for (i in 0 until burstSamples) {
            val env = when {
                i < SAMPLE_RATE * 0.005  -> i / (SAMPLE_RATE * 0.005f)
                i > burstSamples - SAMPLE_RATE * 0.005 -> (burstSamples - i) / (SAMPLE_RATE * 0.005f)
                else -> 1f
            }
            val s = (sin(2.0 * PI * TONE_FREQ * i / SAMPLE_RATE) * 0.75 * env).toFloat()
            burstBuf[i * 2] = s; burstBuf[i * 2 + 1] = s
        }

        // Arm recorder first
        val recBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            recBufSize * 4
        )
        router.routeInput(recorder, router.findRallyAudio())
        recorder.startRecording()

        // Setup playback
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(burstBuf.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        router.routeOutput(track, router.findRallyAudio())
        track.write(burstBuf, 0, burstBuf.size, AudioTrack.WRITE_BLOCKING)
        track.play()

        // Capture
        val capBuf = FloatArray(listenSamples)
        var totalRead = 0
        val readBuf = FloatArray(recBufSize)
        while (totalRead < listenSamples) {
            val n = recorder.read(readBuf, 0, minOf(recBufSize, listenSamples - totalRead), AudioRecord.READ_BLOCKING)
            if (n > 0) { readBuf.copyInto(capBuf, totalRead, 0, n); totalRead += n }
        }

        track.stop(); track.release()
        recorder.stop(); recorder.release()

        // Find echo after burst
        var echoSample = -1
        var i = burstSamples
        while (i + WINDOW < totalRead) {
            var rms = 0.0
            for (j in i until i + WINDOW) rms += capBuf[j].toDouble() * capBuf[j]
            if (sqrt(rms / WINDOW) > THRESHOLD) { echoSample = i; break }
            i += WINDOW / 2
        }

        if (echoSample >= 0) {
            val ms = echoSample * 1000 / SAMPLE_RATE
            EchoResult(true, ms, "✅ Echo gedetecteerd — latentie ≈ ${ms} ms")
        } else {
            EchoResult(false, -1, "❌ Geen echo — controleer speaker & mic")
        }
    }
}
