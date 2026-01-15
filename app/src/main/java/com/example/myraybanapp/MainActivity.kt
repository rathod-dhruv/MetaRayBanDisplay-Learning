package com.example.myraybanapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

// Core imports
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.RegistrationState

// Mock device imports
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import com.meta.wearable.dat.mockdevice.api.MockDevice
import com.meta.wearable.dat.mockdevice.api.MockRaybanMeta

// Camera imports
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.camera.types.StreamSessionState

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var aiResponseText: TextView
    private lateinit var mockDeviceButton: Button
    private lateinit var powerOnButton: Button
    private lateinit var selectVideoButton: Button
    private lateinit var startStreamButton: Button
    private lateinit var askAiButton: Button
    private lateinit var cameraPreview: ImageView

    private lateinit var mockDeviceKit: MockDeviceKitInterface
    private var mockDevice: MockDevice? = null
    private var streamSession: StreamSession? = null
    private var currentFrame: Bitmap? = null

    private lateinit var speechRecognizer: SpeechRecognizer
    private val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startListening()
        }
    }

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            (mockDevice as? MockRaybanMeta)?.let { device ->
                try {
                    val getCameraKit = device.javaClass.getMethod("getCameraKit")
                    val cameraKit = getCameraKit.invoke(device)
                    val setCameraFeed = cameraKit.javaClass.getMethod("setCameraFeed", android.net.Uri::class.java)
                    setCameraFeed.invoke(cameraKit, it)
                    statusText.text = "Video feed set ✓\nTap 'Start Stream'"
                    startStreamButton.isEnabled = true
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to set camera feed", e)
                    statusText.text = "Video selected ✓\nTap 'Start Stream'"
                    startStreamButton.isEnabled = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        aiResponseText = findViewById(R.id.aiResponseText)
        mockDeviceButton = findViewById(R.id.mockDeviceButton)
        powerOnButton = findViewById(R.id.powerOnButton)
        selectVideoButton = findViewById(R.id.selectVideoButton)
        startStreamButton = findViewById(R.id.startStreamButton)
        askAiButton = findViewById(R.id.askAiButton)
        cameraPreview = findViewById(R.id.cameraPreview)

        Wearables.initialize(this)
        statusText.text = "SDK Initialized ✓"

        mockDeviceKit = MockDeviceKit.getInstance(this)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechListener()

        lifecycleScope.launch {
            Wearables.registrationState.collect { state ->
                runOnUiThread {
                    if (state is RegistrationState.Registered) {
                        statusText.text = "Status: Registered ✓"
                    }
                }
            }
        }

        mockDeviceButton.setOnClickListener {
            mockDevice = mockDeviceKit.pairRaybanMeta()
            statusText.text = "Mock device paired! ✓\nTap 'Power On'"
            powerOnButton.isEnabled = true
        }

        powerOnButton.setOnClickListener {
            mockDevice?.let { device ->
                device.powerOn()
                device.don()
                statusText.text = "Device ON ✓ (Mocked)\nTap 'Select Video'"
                selectVideoButton.isEnabled = true
            }
        }

        selectVideoButton.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        startStreamButton.setOnClickListener {
            startCameraStream()
        }

        askAiButton.setOnClickListener {
            checkMicPermissionAndListen()
        }
    }

    private fun startCameraStream() {
        statusText.text = "Connecting to device..."
        lifecycleScope.launch {
            try {
                // Ensure previous session is fully cleaned up
                streamSession?.close()
                streamSession = null
                
                streamSession = Wearables.startStreamSession(
                    context = this@MainActivity,
                    deviceSelector = AutoDeviceSelector(),
                    streamConfiguration = StreamConfiguration(videoQuality = VideoQuality.MEDIUM)
                )

                launch {
                    streamSession?.state?.collect { state ->
                        runOnUiThread {
                            when (state) {
                                StreamSessionState.STREAMING -> {
                                    statusText.text = "Streaming! ✓"
                                    askAiButton.isEnabled = true
                                }
                                StreamSessionState.STOPPED -> {
                                    statusText.text = "Stream stopped"
                                    askAiButton.isEnabled = false
                                }
                                else -> statusText.text = "State: $state"
                            }
                        }
                    }
                }

                launch {
                    streamSession?.videoStream?.collect { frame ->
                        try {
                            val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                            frame.buffer.rewind()
                            bitmap.copyPixelsFromBuffer(frame.buffer)
                            currentFrame = bitmap
                            runOnUiThread { cameraPreview.setImageBitmap(bitmap) }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Frame processing error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start stream", e)
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            }
        }
    }

    private fun checkMicPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        aiResponseText.visibility = View.VISIBLE
        aiResponseText.text = "AI: Listening..."
        try {
            runOnUiThread {
                speechRecognizer.startListening(speechRecognizerIntent)
            }
        } catch (e: Exception) {
            aiResponseText.text = "AI Error: ${e.message}"
        }
    }

    private fun setupSpeechListener() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                runOnUiThread { 
                    aiResponseText.text = "AI Error: Voice recognition failed ($error)" 
                    // Re-enable button if error occurs
                    askAiButton.isEnabled = true
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val question = matches[0]
                    runOnUiThread { 
                        aiResponseText.text = "You: \"$question\"\nAI: Thinking..." 
                    }
                    processAiRequest(question)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun processAiRequest(question: String) {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            runOnUiThread {
                aiResponseText.text = "AI: I see what you're looking at. To answer \"$question\", I'd need to connect to a real GPT/Gemini API!"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        streamSession?.close()
        mockDevice?.powerOff()
        mockDeviceKit.reset()
    }
}
