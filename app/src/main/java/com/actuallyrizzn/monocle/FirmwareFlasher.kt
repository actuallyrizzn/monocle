package com.actuallyrizzn.monocle

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Nordic application DFU. SoftDevice/bootloader stay. Zip may be staged over HTTP. */
class FirmwareFlasher(
    private val context: Context,
    private val ble: MonocleBleClient,
) {
    companion object {
        const val ASSET = "monocle-hibernate.zip"
        const val STAGED = "monocle-staged.zip"
        const val TIMEOUT_SEC = 120L
        const val MAX_ZIP_BYTES = 4L * 1024 * 1024
        @Volatile var lastScan: List<String> = emptyList()
        @Volatile var lastTried: List<String> = emptyList()
        @Volatile var lastSource: String = "bundled"

        fun stagedFile(context: Context): File = File(context.filesDir, STAGED)

        fun isAppZip(file: File): Boolean {
            if (!file.isFile || file.length() < 1024 || file.length() > MAX_ZIP_BYTES) {
                return false
            }
            file.inputStream().use { input ->
                val mag = ByteArray(4)
                if (input.read(mag) != 4) return false
                return mag[0] == 0x50.toByte() && mag[1] == 0x4B.toByte() &&
                    mag[2] == 0x03.toByte() && mag[3] == 0x04.toByte()
            }
        }

        /** Same as Nordic BootloaderScannerFactory.getIncrementedAddress. */
        fun incrementedAddress(address: String): String {
            val a = address.trim().uppercase(Locale.US)
            if (a.length < 17) return a
            val first = a.substring(0, 15)
            val last = a.substring(15)
            val n = last.toIntOrNull(16) ?: return a
            return first + String.format(Locale.US, "%02X", (n + 1) and 0xFF)
        }
    }

    fun copyZip(): File {
        val dest = File(context.filesDir, ASSET)
        context.assets.open(ASSET).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    fun stageZip(bytes: ByteArray): File {
        if (bytes.size < 1024 || bytes.size > MAX_ZIP_BYTES) {
            throw IllegalArgumentException("zip size ${bytes.size}")
        }
        if (bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte() ||
            bytes[2] != 0x03.toByte() || bytes[3] != 0x04.toByte()
        ) {
            throw IllegalArgumentException("not a zip")
        }
        val dest = stagedFile(context)
        dest.outputStream().use { it.write(bytes) }
        return dest
    }

    fun flashBundled(): String = flashZip(null)

    fun flashZip(preferred: File?): String {
        val fallback = ble.lastAddress() ?: return "ERROR: no last Monocle address"
        val staged = preferred ?: stagedFile(context)
        val zip: File
        if (isAppZip(staged)) {
            zip = staged
            lastSource = "staged"
        } else {
            zip = copyZip()
            lastSource = "bundled"
        }
        if (!zip.isFile || zip.length() < 1024) {
            return "ERROR: missing firmware zip"
        }
        ble.pauseReconnect()
        try {
            try {
                val bring = Intent(context, MainActivity::class.java)
                bring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(bring)
            } catch (_: Exception) {
            }
            if (ble.isConnected()) {
                try {
                    ble.exec(MonocleCommands.enterDfu(), timeoutMs = 5_000)
                } catch (_: Exception) {
                }
            }
            ble.disconnectNow("dfu")
            try {
                Thread.sleep(5_000)
            } catch (_: InterruptedException) {
            }
            val hits = scanAds(15_000)
            lastScan = hits.map { "${it.first}|${it.second}" }
            val dfuHit = hits.firstOrNull { it.second.contains("dfu") }?.first
            val candidates = linkedSetOf<String>()
            if (dfuHit != null) candidates.add(dfuHit)
            candidates.add(incrementedAddress(fallback))
            candidates.add(fallback)
            lastTried = candidates.toList()
            DfuServiceInitiator.createDfuNotificationChannel(context)
            var last = "ERROR: no addresses"
            for (addr in candidates) {
                last = dfuOnce(addr, zip)
                if (last == "OK") return "OK"
                if (!last.contains("GATT CONN TIMEOUT") && !last.contains("timeout")) {
                    return last
                }
            }
            return last
        } finally {
            ble.resumeReconnect()
        }
    }

    private fun dfuOnce(addr: String, zip: File): String {
        val err = AtomicReference<String?>(null)
        val done = CountDownLatch(1)
        val listener = object : DfuProgressListenerAdapter() {
            override fun onDfuCompleted(deviceAddress: String) {
                done.countDown()
            }

            override fun onDfuAborted(deviceAddress: String) {
                err.compareAndSet(null, "aborted")
                done.countDown()
            }

            override fun onError(
                deviceAddress: String,
                error: Int,
                errorType: Int,
                message: String?,
            ) {
                err.compareAndSet(null, message ?: "dfu $error")
                done.countDown()
            }
        }
        DfuServiceListenerHelper.registerProgressListener(context, listener)
        return try {
            DfuServiceInitiator(addr)
                .setDeviceName("DfuTarg")
                .setKeepBond(false)
                .setForceDfu(false)
                .setForeground(true)
                .setDisableNotification(false)
                .setPacketsReceiptNotificationsEnabled(false)
                .setNumberOfRetries(1)
                .setScanTimeout(15_000)
                .setRebootTime(2_000)
                .setZip(zip.absolutePath)
                .start(context, DfuService::class.java)
            val finished = done.await(TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) "ERROR: dfu timeout $addr" else {
                val fail = err.get()
                if (fail != null) "ERROR: $fail" else "OK"
            }
        } finally {
            DfuServiceListenerHelper.unregisterProgressListener(context, listener)
        }
    }

    private fun scanAds(timeoutMs: Long): List<Pair<String, String>> {
        val adapter = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val scanner = adapter.bluetoothLeScanner ?: return emptyList()
        val found = LinkedHashMap<String, String>()
        val claimed = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val addr = result.device?.address ?: return
                val name = (result.device?.name ?: result.scanRecord?.deviceName ?: "").trim()
                synchronized(found) {
                    val prev = found[addr]
                    if (prev.isNullOrEmpty() && name.isNotEmpty()) found[addr] = name
                    else if (prev == null) found[addr] = name
                }
                if (name.lowercase().contains("dfu") && claimed.compareAndSet(false, true)) {
                    done.countDown()
                }
            }

            override fun onScanFailed(errorCode: Int) {
                done.countDown()
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return try {
            scanner.startScan(emptyList(), settings, cb)
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
            synchronized(found) { found.entries.map { it.key to it.value } }
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                scanner.stopScan(cb)
            } catch (_: Exception) {
            }
        }
    }
}
