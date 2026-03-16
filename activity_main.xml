package com.example.rallytester

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log

/**
 * Finds the Logitech Rally USB audio device (input + output) and
 * provides helper methods to route AudioRecord/AudioTrack to it.
 *
 * Android 14+ allows explicit device routing via
 *   AudioRecord.setPreferredDevice(AudioDeviceInfo)
 *   AudioTrack.setPreferredDevice(AudioDeviceInfo)
 */
class UsbAudioRouter(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    data class UsbAudioDevice(
        val inputDevice: AudioDeviceInfo?,
        val outputDevice: AudioDeviceInfo?
    ) {
        val hasInput  get() = inputDevice  != null
        val hasOutput get() = outputDevice != null
        val found     get() = hasInput || hasOutput

        override fun toString(): String {
            val i = inputDevice?.productName  ?: "—"
            val o = outputDevice?.productName ?: "—"
            return "USB Audio: in=$i  out=$o"
        }
    }

    /**
     * Scans connected audio devices and returns the first USB audio device
     * that looks like a Logitech Rally (product name contains "Rally" or
     * type == TYPE_USB_DEVICE / TYPE_USB_HEADSET).
     * Falls back to any USB audio device if no Rally-named device found.
     */
    fun findRallyAudio(): UsbAudioDevice {
        val allDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)

        val usbInputTypes  = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
        val usbOutputTypes = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)

        fun isRally(d: AudioDeviceInfo) =
            d.productName?.toString()?.contains("Rally", ignoreCase = true) == true ||
            d.productName?.toString()?.contains("Logitech", ignoreCase = true) == true

        // Prefer devices named "Rally", fallback to any USB audio
        val inputDevice = allDevices
            .filter { it.isSource && it.type in usbInputTypes }
            .let { candidates -> candidates.firstOrNull { isRally(it) } ?: candidates.firstOrNull() }

        val outputDevice = allDevices
            .filter { it.isSink && it.type in usbOutputTypes }
            .let { candidates -> candidates.firstOrNull { isRally(it) } ?: candidates.firstOrNull() }

        Log.d("UsbAudioRouter", "Found: input=${ inputDevice?.productName } output=${ outputDevice?.productName }")
        return UsbAudioDevice(inputDevice, outputDevice)
    }

    /**
     * Routes an AudioRecord to the Rally USB mic (call after startRecording()).
     * Returns true if routing was applied.
     */
    fun routeInput(record: AudioRecord, device: UsbAudioDevice): Boolean {
        if (device.inputDevice == null) return false
        return record.setPreferredDevice(device.inputDevice)
    }

    /**
     * Routes an AudioTrack to the Rally USB speaker (call before play()).
     * Returns true if routing was applied.
     */
    fun routeOutput(track: AudioTrack, device: UsbAudioDevice): Boolean {
        if (device.outputDevice == null) return false
        return track.setPreferredDevice(device.outputDevice)
    }

    /** Human-readable list of all connected audio devices (for debug UI). */
    fun listAllDevices(): String {
        return audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
            .joinToString("\n") { d ->
                val dir  = if (d.isSource) "IN " else "OUT"
                val type = deviceTypeName(d.type)
                "$dir [$type] ${d.productName}"
            }
    }

    private fun deviceTypeName(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE    -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET   -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
        AudioDeviceInfo.TYPE_BUILTIN_MIC   -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPK"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        AudioDeviceInfo.TYPE_WIRED_HEADSET  -> "WIRED_HS"
        else -> "TYPE_$type"
    }
}
