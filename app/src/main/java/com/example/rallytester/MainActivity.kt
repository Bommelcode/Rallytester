package com.example.rallytester

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.rallytester.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var router: UsbAudioRouter
    private lateinit var audioMeter: AudioMeter
    private lateinit var toneGenerator: ToneGenerator
    private lateinit var echoTester: EchoTester
    private lateinit var uvcCamera: UvcCameraHelper

    private var selectedFreqHz = 1_000.0
    private var toneIsPlaying  = false

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val PERM_REQUEST = 42
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        router        = UsbAudioRouter(this)
        toneGenerator = ToneGenerator(router)
        echoTester    = EchoTester(router)
        audioMeter    = AudioMeter(router) { lRms, rRms, peakDb ->
            runOnUiThread {
                // Scale RMS for meter visibility (×5 so normal speech fills ~70%)
                binding.vuMeterLeft.setLevel(lRms * 5f)
                binding.vuMeterRight.setLevel(rRms * 5f)
                binding.tvPeakDb.text = "Peak: ${"%.1f".format(peakDb)} dBFS"
            }
        }

        uvcCamera = UvcCameraHelper(this, binding.surfaceCamera) { statusMsg ->
            runOnUiThread {
                binding.tvStatus.text = statusMsg
                // Hide "no camera" overlay once camera is active
                if (statusMsg.contains("actief")) {
                    binding.tvNoCameraOverlay.visibility = android.view.View.GONE
                }
            }
        }

        setupButtonListeners()
        startPeakDecay()

        if (allPermissionsGranted()) {
            onPermissionsGranted()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERM_REQUEST)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUsbStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioMeter.stop()
        toneGenerator.stop()
        uvcCamera.closeCamera()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onPermissionsGranted()
        } else {
            binding.tvStatus.text = "⚠️ Permissies geweigerd"
        }
    }

    // ── Init after permissions ─────────────────────────────────────────────────

    private fun onPermissionsGranted() {
        uvcCamera.openCamera()
        audioMeter.start()
        refreshUsbStatus()
    }

    // ── USB status ─────────────────────────────────────────────────────────────

    private fun refreshUsbStatus() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val rallyUsb = usbManager.deviceList.values
            .firstOrNull { it.vendorId == 0x046D }

        if (rallyUsb != null) {
            binding.tvStatus.text    = "⬤  Rally verbonden"
            binding.tvStatus.setTextColor(0xFF4CAF50.toInt())
            binding.tvDeviceInfo.text = "USB: ${rallyUsb.productName ?: "Logitech 0x046D"}"
        } else {
            binding.tvStatus.text    = "⬤  Geen Rally op USB"
            binding.tvStatus.setTextColor(0xFFFF9800.toInt())
            binding.tvDeviceInfo.text = "Gebruikt ingebouwde audio/camera"
        }

        // Also show audio device list in device info
        lifecycleScope.launch(Dispatchers.IO) {
            val usbAudio = router.findRallyAudio()
            withContext(Dispatchers.Main) {
                if (usbAudio.found) {
                    binding.tvDeviceInfo.text = usbAudio.toString()
                }
            }
        }
    }

    // ── Buttons ────────────────────────────────────────────────────────────────

    private fun setupButtonListeners() {
        // Frequency selection
        binding.btn80.setOnClickListener   { selectFrequency(80.0,   0) }
        binding.btn440.setOnClickListener  { selectFrequency(440.0,  1) }
        binding.btn1000.setOnClickListener { selectFrequency(1_000.0, 2) }
        selectFrequency(1_000.0, 2) // default

        // Play / stop tone
        binding.btnPlayTone.setOnClickListener {
            if (toneIsPlaying) stopTone() else startTone()
        }

        // Echo loopback test
        binding.btnEchoTest.setOnClickListener {
            runEchoTest()
        }
    }

    private fun selectFrequency(hz: Double, buttonIndex: Int) {
        selectedFreqHz = hz
        val buttons = listOf(binding.btn80, binding.btn440, binding.btn1000)
        buttons.forEachIndexed { i, btn -> btn.alpha = if (i == buttonIndex) 1f else 0.4f }

        // Restart tone at new frequency if already playing
        if (toneIsPlaying) {
            toneGenerator.stop()
            toneGenerator.play(selectedFreqHz) { level ->
                runOnUiThread { binding.vuMeterOutput.setLevel(level) }
            }
        }
    }

    private fun startTone() {
        toneIsPlaying = true
        binding.btnPlayTone.text = "■  Stop testtoon"
        toneGenerator.play(selectedFreqHz) { level ->
            runOnUiThread { binding.vuMeterOutput.setLevel(level) }
        }
    }

    private fun stopTone() {
        toneIsPlaying = false
        binding.btnPlayTone.text = "▶  Testtoon afspelen"
        toneGenerator.stop()
        binding.vuMeterOutput.setLevel(0f)
    }

    private fun runEchoTest() {
        // Stop tone first to avoid self-interference
        if (toneIsPlaying) stopTone()

        binding.btnEchoTest.isEnabled = false
        binding.tvEchoResult.text = "⏳ Meting bezig..."

        lifecycleScope.launch {
            val result = echoTester.run()
            withContext(Dispatchers.Main) {
                binding.tvEchoResult.text    = result.message
                binding.btnEchoTest.isEnabled = true
            }
        }
    }

    // ── Peak hold decay ────────────────────────────────────────────────────────

    private fun startPeakDecay() {
        lifecycleScope.launch {
            while (true) {
                delay(80)
                binding.vuMeterLeft.decayPeak()
                binding.vuMeterRight.decayPeak()
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
