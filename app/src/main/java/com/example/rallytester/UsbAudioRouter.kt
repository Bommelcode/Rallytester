package com.example.rallytester

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log

class UsbAudioRouter(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    data class UsbAudioDevice(
        val inputDevice: AudioDeviceInfo?,
        val outputDevice: AudioDeviceInfo?
    ) {
        val found get() = inputDevice != null || outputDevice != null
        override fun toString(): String {
            val i = inputDevice?.productName  ?: "—"
            val o = outputDevice?.productName ?: "—"
            return "USB in=$i  out=$o"
        }
    }

    fun findRallyAudio(): UsbAudioDevice {
        val all = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        val usbTypes = setOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY
        )
        fun isRally(d: AudioDeviceInfo) =
            d.productName?.toString()?.contains("Rally", ignoreCase = true) == true ||
            d.productName?.toString()?.contains("Logitech", ignoreCase = true) == true

        val input = all.filter { it.isSource && it.type in usbTypes }
            .let { c -> c.firstOrNull { isRally(it) } ?: c.firstOrNull() }
        val output = all.filter { it.isSink && it.type in usbTypes }
            .let { c -> c.firstOrNull { isRally(it) } ?: c.firstOrNull() }

        Log.d("UsbAudioRouter", "input=${input?.productName} output=${output?.productName}")
        return UsbAudioDevice(input, output)
    }

    fun routeInput(record: AudioRecord, device: UsbAudioDevice): Boolean =
        device.inputDevice?.let { record.setPreferredDevice(it) } ?: false

    fun routeOutput(track: AudioTrack, device: UsbAudioDevice): Boolean =
        device.outputDevice?.let { track.setPreferredDevice(it) } ?: false

    fun describeDevices(): String =
        audioManager.getDevices(AudioManager.GET_DEVICES_ALL).joinToString("\n") { d ->
            val dir = if (d.isSource) "IN " else "OUT"
            "$dir [${d.type}] ${d.productName}"
        }
}
