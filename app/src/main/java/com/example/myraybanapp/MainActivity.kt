package com.example.myraybanapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Correct imports
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var mockDeviceButton: Button
    private lateinit var connectButton: Button

    // MockDeviceKit instance
    private lateinit var mockDeviceKit: MockDeviceKitInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the views
        statusText = findViewById(R.id.statusText)
        mockDeviceButton = findViewById(R.id.mockDeviceButton)
        connectButton = findViewById(R.id.connectButton)

        // Step 1: Initialize the SDK
        Wearables.initialize(this)
        statusText.text = "Status: SDK Initialized ✓"

        // Step 2: Get MockDeviceKit instance (CORRECT WAY)
        mockDeviceKit = MockDeviceKit.getInstance(this)

        // Button to pair a mock device
        mockDeviceButton.setOnClickListener {
            // Pair a simulated Ray-Ban Meta glasses
            val mockDevice = mockDeviceKit.pairRaybanMeta()
            statusText.text = "Status: Mock device paired!"
        }

        // Button to start registration
        connectButton.setOnClickListener {
            statusText.text = "Status: Starting registration..."
            Wearables.startRegistration(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Reset mock device kit when app closes
        mockDeviceKit.reset()
    }
}