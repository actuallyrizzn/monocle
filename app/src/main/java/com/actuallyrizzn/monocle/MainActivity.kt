package com.actuallyrizzn.monocle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var hint: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1_000)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        hint = findViewById(R.id.hint)
        findViewById<TextView>(R.id.version).text = "version ${BuildConfig.VERSION_NAME}"
        val limitField = findViewById<EditText>(R.id.recordLimit)
        val limitPrefs = RecordLimitPrefs(this)
        limitField.setText(limitPrefs.seconds().toString())
        findViewById<Button>(R.id.btnRecordLimit).setOnClickListener {
            val parsed = limitField.text.toString().toDoubleOrNull() ?: RecordLimit.DEFAULT
            limitPrefs.save(parsed)
            limitField.setText(limitPrefs.seconds().toString())
            hint.text = hintText(limitPrefs)
        }
        val presencePrefs = PresencePrefs(this)
        val urlField = findViewById<EditText>(R.id.presenceUrl)
        val keyField = findViewById<EditText>(R.id.presenceKey)
        urlField.setText(presencePrefs.url())
        findViewById<Button>(R.id.btnPresence).setOnClickListener {
            val typedKey = keyField.text.toString()
            presencePrefs.save(urlField.text.toString(), typedKey.ifBlank { null })
            urlField.setText(presencePrefs.url())
            keyField.text.clear()
            val client = MonocleService.presenceClient
            if (client != null) {
                client.baseUrl = presencePrefs.url()
                client.apiKey = presencePrefs.apiKey()
            }
            hint.text = hintText(limitPrefs, presencePrefs)
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!hasBtPerms()) {
                permissionLauncher.launch(neededPermissions())
                status.text = "grant Bluetooth + location, then tap Start again"
                return@setOnClickListener
            }
            startForegroundService(Intent(this, MonocleService::class.java))
            refresh()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            startService(Intent(this, MonocleService::class.java).setAction(MonocleService.ACTION_STOP))
            refresh()
        }
        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            permissionLauncher.launch(neededPermissions())
        }
        hint.text = hintText(limitPrefs, presencePrefs)
        refresh()
    }

    private fun hintText(
        limitPrefs: RecordLimitPrefs,
        presencePrefs: PresencePrefs = PresencePrefs(this),
    ): String {
        val presence = if (presencePrefs.configured()) {
            "Presence ${presencePrefs.url()} · key saved"
        } else {
            "Presence not set"
        }
        return getString(R.string.curl_hint, MonocleCommands.HTTP_PORT) +
            "\nRecord limit ${limitPrefs.seconds()}s" +
            "\n$presence"
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        val running = MonocleService.isRunning
        val ble = MonocleService.ble
        val hb = MonocleService.presence
        status.text = BridgeStatus.screen(
            version = BuildConfig.VERSION_NAME,
            serviceRunning = running,
            bluetoothGranted = hasBtPerms(),
            lensConnected = ble?.isConnected() == true,
            rawStatus = ble?.displayStatus() ?: "no BLE yet",
            deviceName = ble?.deviceName,
            presenceOn = if (hb == null) null else hb.lastOn,
            presenceError = hb?.lastError,
        )
    }

    private fun hasBtPerms(): Boolean {
        return neededPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_SCAN
        }
        list += Manifest.permission.ACCESS_FINE_LOCATION
        list += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }
}
