package com.grapesapps.earbudsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Direct Bluetooth connection events avoid waiting for Google's widget refresh. */
class ConnectionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val store = BatteryStore(context)
        AudioConnections.ensure(context)
        val ids = store.targetIds()
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val address =
                    intent
                        .getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?.address
                        ?.uppercase(Locale.ROOT) ?: return
                if (address !in AudioConnections.addresses(context)) disconnect(context, address)
                return
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> Unit
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED" -> {
                if (
                    intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) !=
                        BluetoothProfile.STATE_CONNECTED
                )
                    return
            }
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> Unit
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_TURNING_OFF,
                    BluetoothAdapter.STATE_OFF -> {
                        for (id in ids) {
                            SystemBatterySource.forget(id)
                            store.save(id, emptyList())
                            SmartspacerTargetProvider.notifyChange(
                                context,
                                EarbudsTarget::class.java,
                                id,
                            )
                        }
                        return
                    }
                    BluetoothAdapter.STATE_ON -> Unit
                    else -> return
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SmartspacerTargetProvider.notifyChange(context, EarbudsTarget::class.java, null)
            }
            else -> return
        }
        if (ids.isEmpty() && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        // Connection/profile events may arrive before Settings has populated Fast Pair metadata.
        // This is one deferred read per event, not a recurring timer.
        handler.postDelayed(
            {
                val currentIds = store.targetIds()
                if (currentIds.isEmpty()) {
                    pending.finish()
                    return@postDelayed
                }
                val remaining = AtomicInteger(currentIds.size)
                currentIds.forEach { id ->
                    SystemBatterySource.request(context, id, force = true) {
                        if (remaining.decrementAndGet() == 0) pending.finish()
                    }
                }
            },
            CONNECTION_SETTLE_MILLIS,
        )
    }

    companion object {
        private const val CONNECTION_SETTLE_MILLIS = 500L
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

        fun disconnect(context: Context, address: String) {
            val store = BatteryStore(context)
            for (id in store.targetIds()) {
                // Invalidate pending replies before hiding the card.
                SystemBatterySource.forget(id)
                val previous = store.load(id)
                val remaining = previous.filter { it.address.isNotEmpty() && it.address != address }
                if (remaining != previous) {
                    store.save(id, remaining)
                    SmartspacerTargetProvider.notifyChange(context, EarbudsTarget::class.java, id)
                }
            }
        }
    }
}
