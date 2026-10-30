package com.actuallyrizzn.monocle

import android.app.Activity
import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuDeviceSelector

class DfuService : DfuBaseService() {
    override fun getNotificationTarget(): Class<out Activity> = MainActivity::class.java

    override fun isDebug(): Boolean = true

    /** Unfiltered scan: Samsung often strips the DFU UUID from ads. Match DfuTarg by name or MAC. */
    override fun getDeviceSelector(): DfuDeviceSelector {
        return object : DfuDeviceSelector {
            override fun matches(
                device: BluetoothDevice,
                rssi: Int,
                scanRecord: ByteArray,
                originalAddress: String,
                incrementedAddress: String,
            ): Boolean {
                val n = device.name?.lowercase().orEmpty()
                if (n.contains("dfu")) return true
                val a = device.address
                return a.equals(originalAddress, ignoreCase = true) ||
                    a.equals(incrementedAddress, ignoreCase = true)
            }
        }
    }
}
