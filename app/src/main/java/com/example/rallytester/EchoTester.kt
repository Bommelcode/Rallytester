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

/**
 * Loopback echo test:
 *  1. Arms recorder first (so no audio is missed)
 *  2. Plays a 200ms 1kHz sine burst through the USB speaker
 *  3. Records for 2s total
 *  4. Finds the first sample above [THRESHOLD] that arrives AFTER
 *     the burst has finished playing
 *  5. Returns latency in ms, or "niet gedetecteerd"
 *
 * @param router UsbAudioRouter for explicit USB device selection
 */
class EchoTester(private val router: UsbAudioRouter) {

    companion object {
        private const val SAMPLE_RATE     = 48_000
        private const val BURST_MS        = 200
        private const val LISTEN_MS       = 2_000
        private const val TONE_FREQ       = 1_000.0
        private const val THRESHOLD       = 0.06f   // RMS threshold for echo detection
        private const val WINDOW_SAMPLES  = 256     // RMS window
    }

    data class EchoResult(
        val detected: Boolean,
        val latencyMs: Int,
        val message: String
    )

    suspend fun run(): EchoResult = withContext(Dispatchers.IO) {

        val burstSamples  = SAMPLE_RATE * BURST_MS  / 1000
        val listenSamples = SAMPLE_RATE * LISTEN_MS / 1000

        // ── Build stereo burst buffer ────────────────────────────────────────
        val burstBuf = FloatArray(burstSamples * 2)
        for (i in 0 until burstSamples) {
            // Apply 5ms fade-in and fade-out to avoid clicks
            val env = when {
                i < SAMPLE_RATE * 0.005 -> i.toFloat() / (SAMPLE_RATE * 0.005f)
                i > burstSamples - SAMPLE_RATE * 0.005 ->
                    (burstSamples - i).toFloat() / (SAMPLE_RATE * 0.005f)
                else -> 1f
            }
            val s = (sin(2.0 * PI * TONE_FREQ * i / SAMPLE_RATE) * 0.75 * env).toFloat()
            burstBuf[i * 2]     = s
            burstBuf[i * 2 + 1] = s
        }

        // ── Set up recorder (arm BEFORE playing so we capture the burst) ────
        val recMinBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            recMinBuf * 4
        )
        val usbDev = router.findRallyAudio()
        router.routeInput(recorder, usbDev)
        recorder.startRecording()

        // ── Set up AudioTrack (static mode for precise timing) ──────────────
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

        router.routeOutput(track, usbDev)
        track.write(burstBuf, 0, burstBuf.size, AudioTrack.WRITE_BLOCKING)

        // ── Play & record simultaneously ─────────────────────────────────────
        track.play()
        val captureBuffer = FloatArray(listenSamples)
        var totalRead = 0
        val readBuf = FloatArray(recMinBuf)

        while (totalRead < listenSamples) {
            val toRead = minOf(recMinBuf, listenSamples - totalRead)
            val n = recorder.read(readBuf, 0, toRead, AudioRecord.READ_BLOCKING)
            if (n > 0) {
                readBuf.copyInto(captureBuffer, totalRead, 0, n)
                totalRead += n
            }
        }

        track.stop(); track.release()
        recorder.stop(); recorder.release()

        // ── Find echo: look for RMS spike AFTER the burst ───────────────────
        // The burst occupies the first ~burstSamples of the capture.
        // We search from burstSamples onward with a rolling RMS window.
        var echoSample = -1
        var i = burstSamples
        while (i + WINDOW_SAMPLES < totalRead) {
            var rms = 0.0
            for (j in i until i + WINDOW_SAMPLES) rms += captureBuffer[j].toDouble() * captureBuffer[j]
            rms = kotlin.math.sqrt(rms / WINDOW_SAMPLES)
            if (rms > THRESHOLD) { echoSample = i; break }
            i += WINDOW_SAMPLES / 2   // 50% overlap
        }

        return@withContext if (echoSample >= 0) {
            val latencyMs = echoSample * 1000 / SAMPLE_RATE
            EchoResult(true, latencyMs, "✅ Echo gedetecteerd  —  latentie ≈ ${latencyMs} ms")
        } else {
            EchoResult(false, -1, "❌ Geen echo gedetecteerd — zet volume omhoog of controleer mic")
        }
    }
}
