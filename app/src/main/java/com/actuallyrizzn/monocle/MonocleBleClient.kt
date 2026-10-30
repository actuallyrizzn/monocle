package com.actuallyrizzn.monocle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("MissingPermission")
class MonocleBleClient(private val appContext: Context) {

    companion object {
        val NUS: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val RAW: UUID = UUID.fromString("e5700001-7bac-429a-b4ce-57ff900f479d")
        val RAW_RX: UUID = UUID.fromString("e5700002-7bac-429a-b4ce-57ff900f479d")
        val RAW_TX: UUID = UUID.fromString("e5700003-7bac-429a-b4ce-57ff900f479d")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val manager: BluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = manager.adapter

    private val worker = HandlerThread("monocle-ble").apply { start() }
    private val handler = Handler(worker.looper)

    @Volatile
    var status: String = "idle"
        private set

    @Volatile
    var deviceName: String? = null
        private set

    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var rawRx: BluetoothGattCharacteristic? = null
    private var rawTx: BluetoothGattCharacteristic? = null
    private var chunkSize: Int = 20
    private val connected = AtomicBoolean(false)
    private val epoch = AtomicInteger(0)
    private val handshakeDone = AtomicBoolean(false)
    private val rawCccdPending = AtomicBoolean(false)
    private val rxBuf = StringBuilder()
    private val rawBuf = java.io.ByteArrayOutputStream()
    private val rawPackets = mutableListOf<ByteArray>()
    private val execLock = Object()
    private var writeLatch: CountDownLatch? = null
    private var notifyLatch: CountDownLatch? = null
    private val lastError = AtomicReference<String?>(null)
    private val blePrefs = BlePrefs(appContext)
    private val keepAlive = AtomicBoolean(true)
    private val reconnect = AtomicBoolean(true)
    private val connectGate = Object()
    private val keepThread = Thread({
        while (keepAlive.get()) {
            try {
                if (reconnect.get() && !connected.get() && keepAlive.get()) {
                    connectBlocking(20_000)
                }
                val pause = if (connected.get()) 2_500L else 1_000L
                Thread.sleep(pause)
            } catch (_: InterruptedException) {
                break
            }
        }
    }, "monocle-keepalive")

    @Volatile
    var onConnected: (() -> Unit)? = null

    @Volatile
    var rawSubscribed: Boolean = false
        private set

    init {
        keepThread.isDaemon = true
        keepThread.start()
    }

    fun isConnected(): Boolean = connected.get()

    fun lastAddress(): String? = blePrefs.lastAddress()

    fun pauseReconnect() {
        reconnect.set(false)
    }

    fun resumeReconnect() {
        reconnect.set(true)
    }

    fun disconnectNow(reason: String = "disconnected") {
        val done = CountDownLatch(1)
        handler.post {
            stopScan()
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (_: Exception) {
            }
            gatt = null
            rx = null
            tx = null
            rawRx = null
            rawTx = null
            connected.set(false)
            rawSubscribed = false
            status = reason
            done.countDown()
        }
        try {
            done.await(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
    }

    fun connectionEpoch(): Int = epoch.get()

    fun displayStatus(): String = BridgeStatus.detail(connected.get(), status)

    fun lastError(): String? = lastError.get()

    fun takeRawPackets(): List<ByteArray> = synchronized(rawBuf) {
        val copy = rawPackets.map { it.copyOf() }
        rawPackets.clear()
        rawBuf.reset()
        copy
    }

    fun takeRaw(): ByteArray {
        val packets = takeRawPackets()
        if (packets.isEmpty()) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        for (p in packets) out.write(p)
        return out.toByteArray()
    }

    fun connectBlocking(timeoutMs: Long): Boolean {
        synchronized(connectGate) {
            return connectBlockingLocked(timeoutMs)
        }
    }

    private fun connectBlockingLocked(timeoutMs: Long): Boolean {
        if (connected.get()) return true
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        handler.post {
            if (adapter == null || !adapter.isEnabled || adapter.bluetoothLeScanner == null) {
                status = "bluetooth off"
                lastError.set(status)
                latch.countDown()
                return@post
            }
            val known = existingMonocle()
            if (known != null) {
                status = "using known ${known.name ?: known.address}"
                connectGatt(known) { success ->
                    ok.set(success)
                    latch.countDown()
                }
                return@post
            }
            startScan(
                onFound = { device ->
                    connectGatt(device) { success ->
                        ok.set(success)
                        latch.countDown()
                    }
                },
                onMiss = { latch.countDown() },
            )
        }
        val finished = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            if (status == "scanning" || status == "idle") {
                status = "scan/connect timeout"
            }
            lastError.set(status)
            stopScan()
        }
        return finished && ok.get()
    }

    private fun dropGatt(g: BluetoothGatt, reason: String) {
        connected.set(false)
        rawSubscribed = false
        status = reason
        lastError.set(reason)
        handler.post {
            try {
                if (gatt === g || gatt == null) {
                    try {
                        g.disconnect()
                    } catch (_: Exception) {
                    }
                    try {
                        g.close()
                    } catch (_: Exception) {
                    }
                    if (gatt === g) {
                        gatt = null
                        rx = null
                        tx = null
                        rawRx = null
                        rawTx = null
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun exec(code: String, timeoutMs: Long = 8000, interrupt: Boolean = true): String {
        synchronized(execLock) {
            return execLocked(code, timeoutMs, interrupt)
        }
    }

    fun writeRaw(data: ByteArray): Boolean {
        synchronized(execLock) {
            val ch = rawRx ?: return false
            return writeTo(ch, data)
        }
    }

    fun writeRawAndCollect(
        data: ByteArray,
        quietMs: Long = 800,
        deadlineMs: Long = 25_000,
        emptyMs: Long = deadlineMs,
    ): ReplResult {
        synchronized(execLock) {
            val ch = rawRx ?: return ReplResult("ERROR: no raw", ByteArray(0))
            takeRaw()
            if (!writeTo(ch, data)) {
                return ReplResult("ERROR: write failed", ByteArray(0))
            }
            waitRawQuiet(quietMs, deadlineMs, emptyMs)
            val packets = takeRawPackets()
            val raw = if (packets.isEmpty()) {
                ByteArray(0)
            } else {
                val out = java.io.ByteArrayOutputStream()
                for (p in packets) out.write(p)
                out.toByteArray()
            }
            return ReplResult("", raw, packets)
        }
    }

    fun execCollectRaw(
        code: String,
        timeoutMs: Long = 15_000,
        interrupt: Boolean = true,
        rawQuietMs: Long = 800,
        rawDeadlineMs: Long = 25_000,
    ): ReplResult {
        synchronized(execLock) {
            takeRaw()
            val repl = execLocked(code, timeoutMs, interrupt)
            waitRawQuiet(rawQuietMs, rawDeadlineMs)
            val packets = takeRawPackets()
            val raw = if (packets.isEmpty()) {
                ByteArray(0)
            } else {
                val out = java.io.ByteArrayOutputStream()
                for (p in packets) out.write(p)
                out.toByteArray()
            }
            return ReplResult(repl, raw, packets)
        }
    }

    private fun execLocked(code: String, timeoutMs: Long, interrupt: Boolean): String {
        if (!connected.get()) {
            if (!connectBlocking(20_000)) {
                return "ERROR: not connected (${lastError.get() ?: status})"
            }
        }
        // Official Brilliant/WebREPL: one Ctrl-C + Ctrl-A enters raw REPL.
        // Double Ctrl-C reboots this firmware. Ctrl-D in friendly mode also reboots.
        synchronized(rxBuf) { rxBuf.setLength(0) }
        if (interrupt) {
            writeBytes(byteArrayOf(0x03, 0x01))
            waitUntil({ ReplIo.rawReady(it) }, 2500)
        }
        synchronized(rxBuf) { rxBuf.setLength(0) }
        val payload = code.trimEnd() + "\n"
        writeBytes(payload.toByteArray(Charsets.UTF_8))
        writeBytes(byteArrayOf(0x04))
        waitUntil({ ReplIo.rawReady(it) || ReplIo.atPrompt(it) }, timeoutMs)
        val out = synchronized(rxBuf) { rxBuf.toString().trim() }
        return out.ifEmpty { "(no repl output — check the lens)" }
    }

    private fun waitRawQuiet(quietMs: Long, maxMs: Long = 4_000, emptyMs: Long = maxMs) {
        val start = System.currentTimeMillis()
        val deadline = start + maxMs
        var lastSize = synchronized(rawBuf) { rawBuf.size() }
        var lastChange = start
        var sawData = lastSize > 0
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(80)
            val size = synchronized(rawBuf) { rawBuf.size() }
            if (size != lastSize) {
                lastSize = size
                lastChange = System.currentTimeMillis()
                if (size > 0) sawData = true
            } else if (sawData && System.currentTimeMillis() - lastChange >= quietMs) {
                return
            } else if (!sawData && System.currentTimeMillis() - start >= emptyMs) {
                return
            }
        }
    }

    private fun waitUntil(pred: (String) -> Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snap = synchronized(rxBuf) { rxBuf.toString() }
            if (pred(snap)) return true
            val wake = CountDownLatch(1)
            notifyLatch = wake
            wake.await(80, TimeUnit.MILLISECONDS)
        }
        return pred(synchronized(rxBuf) { rxBuf.toString() })
    }

    fun close() {
        keepAlive.set(false)
        keepThread.interrupt()
        handler.post {
            stopScan()
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (_: Exception) {
            }
            gatt = null
            rx = null
            tx = null
            rawRx = null
            rawTx = null
            connected.set(false)
            status = "stopped"
        }
        worker.quitSafely()
    }

    private var scanCallback: ScanCallback? = null

    private fun existingMonocle(): BluetoothDevice? {
        val connectedGatt = try {
            manager.getConnectedDevices(BluetoothProfile.GATT)
        } catch (_: SecurityException) {
            emptyList()
        }
        connectedGatt.firstOrNull { DeviceMatch.isCandidate(it.name) }?.let { return it }
        blePrefs.lastAddress()?.let { addr ->
            try {
                return adapter?.getRemoteDevice(addr)
            } catch (_: Exception) {
            }
        }
        val bonded = try {
            adapter?.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            emptySet()
        }
        return bonded.firstOrNull { DeviceMatch.isCandidate(it.name) }
    }

    private fun startScan(onFound: (BluetoothDevice) -> Unit, onMiss: () -> Unit) {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            status = "no BLE scanner"
            lastError.set(status)
            onMiss()
            return
        }
        val claimed = AtomicBoolean(false)
        var ads = 0
        status = "scanning"
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                ads += 1
                val device = result.device ?: return
                val name = device.name ?: result.scanRecord?.deviceName
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid }
                if (!DeviceMatch.isCandidate(name, uuids)) {
                    status = "scanning ($ads ads)"
                    return
                }
                if (!claimed.compareAndSet(false, true)) return
                stopScan()
                onFound(device)
            }

            override fun onScanFailed(errorCode: Int) {
                status = "scan failed $errorCode"
                lastError.set(status)
                if (claimed.compareAndSet(false, true)) onMiss()
            }
        }
        scanCallback = cb
        // Unfiltered: Samsung often omits the NUS UUID from the advertisement packet.
        scanner.startScan(emptyList(), settings, cb)
        handler.postDelayed({
            if (!claimed.get() && !connected.get()) {
                val bonded = try {
                    adapter?.bondedDevices?.mapNotNull { it.name } ?: emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
                status = "no Monocle seen ($ads ads, bonded=$bonded)"
                lastError.set(status)
                stopScan()
                if (claimed.compareAndSet(false, true)) onMiss()
            }
        }, 10_000)
    }

    private fun stopScan() {
        val cb = scanCallback ?: return
        scanCallback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: Exception) {
        }
    }

    private fun connectGatt(device: BluetoothDevice, done: (Boolean) -> Unit) {
        deviceName = device.name ?: device.address
        blePrefs.saveAddress(device.address)
        status = "connecting ${deviceName}"
        handshakeDone.set(false)
        fun finish(ok: Boolean) {
            if (!handshakeDone.compareAndSet(false, true)) return
            done(ok)
        }
        gatt = device.connectGatt(appContext, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    status = "mtu"
                    if (!g.requestMtu(247)) {
                        status = "discovering"
                        g.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    dropGatt(g, "disconnected")
                    finish(false)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, statusCode: Int) {
                chunkSize = (mtu - 3).coerceIn(20, 125)
                status = "discovering"
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                val svc = g.getService(NUS)
                if (svc == null) {
                    status = "no NUS service"
                    lastError.set(status)
                    finish(false)
                    return
                }
                rx = svc.getCharacteristic(NUS_RX)
                tx = svc.getCharacteristic(NUS_TX)
                val t = tx
                if (rx == null || t == null) {
                    status = "missing NUS characteristics"
                    lastError.set(status)
                    finish(false)
                    return
                }
                val rawSvc = g.getService(RAW)
                rawRx = rawSvc?.getCharacteristic(RAW_RX)
                rawTx = rawSvc?.getCharacteristic(RAW_TX)
                rawSubscribed = false
                g.setCharacteristicNotification(t, true)
                val desc = t.getDescriptor(CCCD)
                if (desc != null) {
                    rawCccdPending.set(rawTx != null)
                    writeDescriptor(g, desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    subscribeRawThenFinish(g, ::finish)
                }
            }

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                statusCode: Int,
            ) {
                if (rawCccdPending.compareAndSet(true, false)) {
                    subscribeRawThenFinish(g, ::finish)
                    return
                }
                if (descriptor.uuid == CCCD && descriptor.characteristic?.uuid == RAW_TX) {
                    rawSubscribed = true
                }
                markConnected()
                finish(true)
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                dispatchNotify(characteristic.uuid, value)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                dispatchNotify(characteristic.uuid, characteristic.value ?: ByteArray(0))
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                statusCode: Int,
            ) {
                writeLatch?.countDown()
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    private fun subscribeRawThenFinish(g: BluetoothGatt, finish: (Boolean) -> Unit) {
        val t = rawTx
        if (t == null) {
            markConnected()
            finish(true)
            return
        }
        g.setCharacteristicNotification(t, true)
        val desc = t.getDescriptor(CCCD)
        if (desc == null) {
            markConnected()
            finish(true)
            return
        }
        writeDescriptor(g, desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    private fun markConnected() {
        connected.set(true)
        epoch.incrementAndGet()
        status = "connected ${deviceName}"
        lastError.set(null)
        try {
            gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        } catch (_: Exception) {
        }
        val cb = onConnected ?: return
        Thread({
            try {
                cb()
            } catch (_: Exception) {
            }
        }, "monocle-hud-up").start()
    }

    private fun dispatchNotify(uuid: UUID, value: ByteArray) {
        if (uuid == RAW_TX) {
            synchronized(rawBuf) {
                if (rawBuf.size() < 512 * 1024) {
                    rawBuf.write(value)
                    rawPackets.add(value.copyOf())
                }
            }
            notifyLatch?.countDown()
            return
        }
        val chunk = value.toString(Charsets.UTF_8)
        synchronized(rxBuf) { rxBuf.append(chunk) }
        notifyLatch?.countDown()
    }

    private fun writeDescriptor(
        g: BluetoothGatt,
        desc: BluetoothGattDescriptor,
        value: ByteArray,
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(desc, value)
        } else {
            @Suppress("DEPRECATION")
            desc.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(desc)
        }
    }

    private fun writeBytes(data: ByteArray) {
        val ch = rx ?: return
        writeTo(ch, data)
    }

    private fun writeTo(ch: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        val g = gatt ?: return false
        val step = chunkSize
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + step, data.size)
            val slice = data.copyOfRange(offset, end)
            writeLatch = CountDownLatch(1)
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(
                    ch,
                    slice,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                ch.value = slice
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            if (!ok) {
                connected.set(false)
                status = "write failed"
                lastError.set("write failed")
                return false
            }
            writeLatch?.await(80, TimeUnit.MILLISECONDS)
            offset = end
        }
        return true
    }
}
