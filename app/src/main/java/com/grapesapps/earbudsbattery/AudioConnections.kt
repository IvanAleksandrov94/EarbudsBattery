@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.grapesapps.earbudsbattery

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Audio endpoint events cover Classic and LE Audio without a polling service. */
object AudioConnections {
    interface Source { fun audioAddresses(): Set<String> }
    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothTypes = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
    @Volatile
    private var registered = false
    fun addresses(context: Context): Set<String> = if (context is Source) context.audioAddresses() else context.getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in bluetoothTypes }
        .map { it.address.uppercase(Locale.ROOT) }.filter { it.isNotBlank() }.toSet()

    fun ensure(context: Context) {
        if (registered) return
        val app = context.applicationContext
        handler.post {
            if (registered) return@post
            registered = true
            var previous = emptySet<String>()
            var initialized = false
            val callback = object : AudioDeviceCallback() {
                fun changed() {
                    val current = addresses(app)
                    if (initialized && current == previous) return
                    initialized = true
                    (previous - current).forEach { ConnectionReceiver.disconnect(app, it) }
                    val store = BatteryStore(app)
                    previous = current
                    store.targetIds().forEach { SystemBatterySource.request(app, it, force = true) }
                }
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = changed()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = changed()
            }
            app.getSystemService(AudioManager::class.java).registerAudioDeviceCallback(callback, handler)
        }
    }
}
