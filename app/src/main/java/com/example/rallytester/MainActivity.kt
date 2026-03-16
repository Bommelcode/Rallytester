package com.example.rallytester

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
    private lateinit var toneGen: ToneGenerator
    private lateinit var echoTester: EchoTester

    private var selectedFreqHz = 1_000.0
    private var toneIsPlaying  = false

    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val PERM_REQUEST = 42
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        router     = UsbAudioRouter(this)
        toneGen    = ToneGenerator(router)
        echoTester = EchoTester(router)
        audioMeter = AudioMeter(router) { lRms, rRms, peakDb ->
            runOnUiThread {
                binding.vuMeterLeft.setLevel(lRms * 5f)
                binding.vuMeterRight.setLevel(rRms * 5f)
                binding.tvPeakDb.text = "Peak: ${"%.1f".format(peakDb)} dBFS"
            }
        }

        setupButtons()
        startPeakDecay()

        if (allPermissionsGranted()) onPermissionsGranted()
        else ActivityCompat.requestPermissions(this, PERMISSIONS, PERM_REQUEST)
    }

    override fun onResume() {
        super.onResume()
        refreshUsbStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioMeter.stop()
        toneGen.stop()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == PERM_REQUEST && results.all { it == PackageManager.PERMISSION_GRANTED })
            onPermissionsGranted()
        else
            binding.tvStatus.text = "⚠️ Permissies vereist"
    }

    private fun onPermissionsGranted() {
        startCamera()
        audioMeter.start()
        refreshUsbStatus()
    }

    // ── USB status ─────────────────────────────────────────────────────────────

    private fun refreshUsbStatus() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val rally = usbManager.deviceList.values.firstOrNull { it.vendorId == 0x046D }
        if (rally != null) {
            binding.tvStatus.text = "⬤  Rally verbonden"
            binding.tvStatus.setTextColor(0xFF4CAF50.toInt())
            binding.tvDeviceInfo.text = rally.productName ?: "Logitech Rally"
        } else {
            binding.tvStatus.text = "⬤  Geen Rally op USB"
            binding.tvStatus.setTextColor(0xFFFF9800.toInt())
            binding.tvDeviceInfo.text = "Gebruikt ingebouwde audio/camera"
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val dev = router.findRallyAudio()
            if (dev.found) withContext(Dispatchers.Main) { binding.tvDeviceInfo.text = dev.toString() }
        }
    }

    // ── Camera ─────────────────────────────────────────────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val selector = when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)  -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> return@addListener
            }
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.cameraPreview.surfaceProvider
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview)
            }.onFailure {
                binding.tvNoCameraOverlay.visibility = android.view.View.VISIBLE
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Buttons ────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btn80.setOnClickListener   { selectFreq(80.0,    0) }
        binding.btn440.setOnClickListener  { selectFreq(440.0,   1) }
        binding.btn1000.setOnClickListener { selectFreq(1_000.0, 2) }
        selectFreq(1_000.0, 2)

        binding.btnPlayTone.setOnClickListener {
            if (toneIsPlaying) stopTone() else startTone()
        }
        binding.btnEchoTest.setOnClickListener { runEchoTest() }
    }

    private fun selectFreq(hz: Double, idx: Int) {
        selectedFreqHz = hz
        listOf(binding.btn80, binding.btn440, binding.btn1000)
            .forEachIndexed { i, btn -> btn.alpha = if (i == idx) 1f else 0.4f }
        if (toneIsPlaying) { toneGen.stop(); toneGen.play(selectedFreqHz) { lv -> runOnUiThread { binding.vuMeterOutput.setLevel(lv) } } }
    }

    private fun startTone() {
        toneIsPlaying = true
        binding.btnPlayTone.text = "■  Stop testtoon"
        toneGen.play(selectedFreqHz) { lv -> runOnUiThread { binding.vuMeterOutput.setLevel(lv) } }
    }

    private fun stopTone() {
        toneIsPlaying = false
        binding.btnPlayTone.text = "▶  Testtoon afspelen"
        toneGen.stop()
        binding.vuMeterOutput.setLevel(0f)
    }

    private fun runEchoTest() {
        if (toneIsPlaying) stopTone()
        binding.btnEchoTest.isEnabled = false
        binding.tvEchoResult.text = "⏳ Meting bezig..."
        lifecycleScope.launch {
            val result = echoTester.run()
            withContext(Dispatchers.Main) {
                binding.tvEchoResult.text     = result.message
                binding.btnEchoTest.isEnabled = true
            }
        }
    }

    // ── Peak decay ──────────────────────────────────────────────────────────────

    private fun startPeakDecay() {
        lifecycleScope.launch {
            while (true) {
                delay(80)
                binding.vuMeterLeft.decayPeak()
                binding.vuMeterRight.decayPeak()
            }
        }
    }

    private fun allPermissionsGranted() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
