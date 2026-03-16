package com.example.rallytester

import android.content.Context
import android.hardware.usb.UsbDevice
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.utils.Logger

/**
 * Wraps the AndroidUSBCamera library (com.github.jiangdongguo:AndroidUSBCamera)
 * to open and display the Logitech Rally UVC camera stream.
 *
 * Usage:
 *   val helper = UvcCameraHelper(context, surfaceView, onStatus)
 *   helper.openCamera()       // call when SurfaceView is ready
 *   helper.closeCamera()      // call in onDestroy
 */
class UvcCameraHelper(
    private val context: Context,
    private val previewSurface: SurfaceView,
    private val onStatus: (String) -> Unit
) {
    private var cameraClient: MultiCameraClient? = null
    private var currentCamera: MultiCameraClient.ICamera? = null

    private val cameraStateCallback = object : ICameraStateCallBack {
        override fun onCameraState(
            self: MultiCameraClient.ICamera,
            code: ICameraStateCallBack.State,
            msg: String?
        ) {
            when (code) {
                ICameraStateCallBack.State.OPENED -> onStatus("📷 Camera actief")
                ICameraStateCallBack.State.CLOSED -> onStatus("📷 Camera gesloten")
                ICameraStateCallBack.State.ERROR  -> onStatus("⚠️ Camera fout: $msg")
            }
        }
    }

    /** Call this once, from onResume or when permissions are granted. */
    fun openCamera() {
        val client = MultiCameraClient(context, object : MultiCameraClient.IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                // Auto-open first UVC device (the Rally camera)
                device ?: return
                cameraClient?.openCamera(
                    device = device,
                    cameraStateCallBack = cameraStateCallback,
                    cameraRequest = CameraRequest.Builder()
                        .setPreviewWidth(1920)
                        .setPreviewHeight(1080)
                        .setRenderMode(CameraRequest.RenderMode.OPENGL)
                        .setDefaultRotateType(RotateType.ANGLE_0)
                        .setAudioSource(CameraRequest.AudioSource.SOURCE_DEF)
                        .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                        .setAspectRatioShow(true)
                        .create(),
                    previewSurface = previewSurface
                )
            }

            override fun onDetachDev(device: UsbDevice?) {
                onStatus("🔌 Camera losgekoppeld")
                closeCamera()
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: Any?) {
                onStatus("🔗 USB verbonden")
            }

            override fun onDisConnectDev(device: UsbDevice?) {
                onStatus("⚠️ USB verbroken")
            }
        })
        client.register()
        cameraClient = client
        onStatus("🔍 Zoeken naar USB camera...")
    }

    /** Call from onDestroy. */
    fun closeCamera() {
        cameraClient?.closeCamera()
        cameraClient?.unRegister()
        cameraClient?.destroy()
        cameraClient = null
    }
}

// Stub for rotation — matches the library's RotateType enum
object RotateType {
    const val ANGLE_0 = 0
}
