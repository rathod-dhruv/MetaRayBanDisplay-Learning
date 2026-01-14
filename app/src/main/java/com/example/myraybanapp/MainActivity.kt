package com.example.myraybanapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

// Core imports
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector

// Mock device imports
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import com.meta.wearable.dat.mockdevice.api.MockRaybanMeta

// Camera imports
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoFrame

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var mockDeviceButton: Button
    private lateinit var powerOnButton: Button
    private lateinit var selectVideoButton: Button
    private lateinit var startStreamButton: Button
    private lateinit var cameraPreview: ImageView

    private lateinit var mockDeviceKit: MockDeviceKitInterface
    private var mockDevice: MockRaybanMeta? = null
    private var streamSession: StreamSession? = null

    // Video picker
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { videoUri ->
            mockDevice?.let { device ->
                device.getCameraKit().setCameraFeed(videoUri)
                statusText.text = "Video set! ✓\nTap 'Start Camera Stream'\n(Must be h265 format!)"
                startStreamButton.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find views
        statusText = findViewById(R.id.statusText)
        mockDeviceButton = findViewById(R.id.mockDeviceButton)
        powerOnButton = findViewById(R.id.powerOnButton)
        selectVideoButton = findViewById(R.id.selectVideoButton)
        startStreamButton = findViewById(R.id.startStreamButton)
        cameraPreview = findViewById(R.id.cameraPreview)

        // Initialize SDK
        Wearables.initialize(this)
        statusText.text = "SDK Initialized ✓"

        // Get MockDeviceKit
        mockDeviceKit = MockDeviceKit.getInstance(this)

        // Disable buttons initially
        powerOnButton.isEnabled = false
        selectVideoButton.isEnabled = false
        startStreamButton.isEnabled = false

        // Button 1: Pair mock device
        mockDeviceButton.setOnClickListener {
            mockDevice = mockDeviceKit.pairRaybanMeta()
            statusText.text = "Mock device paired! ✓\nTap 'Power On'"
            powerOnButton.isEnabled = true
        }

        // Button 2: Power on and simulate wearing
        powerOnButton.setOnClickListener {
            mockDevice?.let { device ->
                device.powerOn()
                device.don()
                statusText.text = "Device ON! ✓\nTap 'Select Video'\n(h265 format required!)"
                selectVideoButton.isEnabled = true
            }
        }

        // Button 3: Select video for mock camera
        selectVideoButton.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        // Button 4: Start camera stream
        startStreamButton.setOnClickListener {
            startCameraStream()
        }
    }

    private fun startCameraStream() {
        statusText.text = "Starting camera stream..."

        lifecycleScope.launch {
            try {
                val streamConfig = StreamConfiguration(
                    videoQuality = VideoQuality.MEDIUM
                )

                streamSession = Wearables.startStreamSession(
                    context = this@MainActivity,
                    deviceSelector = AutoDeviceSelector(),
                    streamConfiguration = streamConfig
                )

                // Observe stream state
                launch {
                    streamSession?.state?.collect { state ->
                        runOnUiThread {
                            when (state) {
                                StreamSessionState.STREAMING -> {
                                    statusText.text = "Streaming! ✓"
                                }
                                StreamSessionState.STOPPED -> {
                                    statusText.text = "Stream stopped"
                                }
                                else -> {
                                    statusText.text = "State: $state"
                                }
                            }
                        }
                    }
                }

                // Observe video frames with CORRECT processing
                launch {
                    streamSession?.videoStream?.collect { frame ->
                        val bitmap = handleVideoFrame(frame)
                        bitmap?.let {
                            runOnUiThread {
                                cameraPreview.setImageBitmap(it)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Error: ${e.message}"
                }
            }
        }
    }

    // CORRECT video frame handling from sample [3]
    private fun handleVideoFrame(videoFrame: VideoFrame): Bitmap? {
        return try {
            val buffer = videoFrame.buffer
            val dataSize = buffer.remaining()
            val byteArray = ByteArray(dataSize)

            val originalPosition = buffer.position()
            buffer.get(byteArray)
            buffer.position(originalPosition)

            // Convert I420 to NV21
            val nv21 = convertI420toNV21(byteArray, videoFrame.width, videoFrame.height)
            val image = YuvImage(nv21, ImageFormat.NV21, videoFrame.width, videoFrame.height, null)

            val out = ByteArrayOutputStream()
            image.compressToJpeg(Rect(0, 0, videoFrame.width, videoFrame.height), 50, out)
            val jpegBytes = out.toByteArray()

            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // Convert I420 to NV21 format [3]
    private fun convertI420toNV21(input: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(input.size)
        val size = width * height
        val quarter = size / 4

        input.copyInto(output, 0, 0, size)

        for (n in 0 until quarter) {
            output[size + n * 2] = input[size + quarter + n]
            output[size + n * 2 + 1] = input[size + n]
        }
        return output
    }

    override fun onDestroy() {
        super.onDestroy()
        streamSession?.close()
        mockDevice?.powerOff()
        mockDeviceKit.reset()
    }
}