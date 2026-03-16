package com.example.rallytester

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Continuously captures audio from the USB mic (Logitech Rally).
 * Emits stereo RMS levels + peak dB via [onLevel] callback.
 *
 * @param router  UsbAudioRouter used to steer input to the Rally mic
 * @param onLevel (leftRms 0..1, rightRms 0..1, peakDb float) called on IO thread
 */
class AudioMeter(
    private val router: UsbAudioRouter,
    private val onLevel: (leftRms: Float, rightRms: Float, peakDb: Float) -> Unit
) {
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val SAMPLE_RATE   = 48_000
        private const val CHANNEL_CFG   = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FMT     = AudioFormat.ENCODING_PCM_16BIT
    }

    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CFG, AUDIO_FMT)
            .coerceAtLeast(4096)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CFG, AUDIO_FMT,
            minBuf * 4
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }

        // Route to Rally USB mic if available
        val usbDev = router.findRallyAudio()
        router.routeInput(rec, usbDev)

        rec.startRecording()
        recorder = rec

        job = scope.launch {
            val buf = ShortArray(minBuf)
            while (isActive) {
                val read = rec.read(buf, 0, buf.size)
                if (read <= 0) continue

                var sumL = 0.0; var sumR = 0.0
                var peak = 0f
                var nL = 0; var nR = 0

                for (i in 0 until read) {
                    val s = buf[i].toFloat() / Short.MAX_VALUE
                    val a = if (s < 0) -s else s
                    if (a > peak) peak = a
                    if (i % 2 == 0) { sumL += s * s; nL++ }
                    else            { sumR += s * s; nR++ }
                }

                val rmsL  = if (nL > 0) sqrt(sumL / nL).toFloat() else 0f
                val rmsR  = if (nR > 0) sqrt(sumR / nR).toFloat() else 0f
                val peakDb = if (peak > 0) 20f * log10(peak) else -96f

                onLevel(rmsL, rmsR, peakDb)
            }
        }
    }

    fun stop() {
        job?.cancel()
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}
