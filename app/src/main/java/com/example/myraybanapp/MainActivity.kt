package com.example.myraybanapp

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// Correct imports
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var mockDeviceButton: Button
    private lateinit var streamButton: Button

    private lateinit var mockDeviceKit: MockDeviceKitInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the views
        statusText = findViewById(R.id.statusText)
        mockDeviceButton = findViewById(R.id.mockDeviceButton)
        streamButton = findViewById(R.id.streamButton)

        // Step 1: Initialize the SDK
        Wearables.initialize(this)
        statusText.text = "Status: SDK Initialized ✓"

        // Step 2: Get MockDeviceKit instance
        mockDeviceKit = MockDeviceKit.getInstance(this)

        // Button to pair a mock device
        mockDeviceButton.setOnClickListener {
            val mockDevice = mockDeviceKit.pairRaybanMeta()
            statusText.text = "Status: Mock device paired! ✓"

            // Enable stream button after pairing
            streamButton.isEnabled = true
        }

        // Button to observe devices (instead of registration)
        streamButton.isEnabled = false
        streamButton.setOnClickListener {
            observeDevices()
        }
    }

    private fun observeDevices() {
        lifecycleScope.launch {
            Wearables.devices.collect { devices ->
                runOnUiThread {
                    if (devices.isNotEmpty()) {
                        statusText.text = "Status: Found ${devices.size} device(s)!"
                    } else {
                        statusText.text = "Status: No devices found"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mockDeviceKit.reset()
    }
}