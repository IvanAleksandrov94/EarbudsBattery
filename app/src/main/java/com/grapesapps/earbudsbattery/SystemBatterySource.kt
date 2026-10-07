package com.grapesapps.earbudsbattery

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.ResultReceiver
import android.os.SystemClock
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import java.util.concurrent.atomic.AtomicBoolean

/** Reads Google's system battery data before the widget merges the left and right earbuds. */
object SystemBatterySource {
    private const val ACTION = "settings.intelligence.battery.action.FETCH_BLUETOOTH_BATTERY_DATA"
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val QUERY_COOLDOWN_NANOS = 30_000_000_000L
    private const val RESPONSE_TIMEOUT_MILLIS = 4_000L
    private const val FOLLOW_UP_DELAY_MILLIS = 900L
    private val handler = Handler(Looper.getMainLooper())
    private val lastRequests = mutableMapOf<String, Long>()

    @Suppress("DEPRECATION")
    fun isAvailable(context: Context): Boolean =
        context.packageManager
            .queryBroadcastReceivers(Intent(ACTION).setPackage(SETTINGS_PACKAGE), 0)
            .isNotEmpty()

    @Suppress("DEPRECATION")
    fun parse(data: Bundle): List<RawBatteryRow>? {
        if (data.isEmpty) return emptyList()
        if (data.containsKey("bluetoothWrapDataListKey")) {
            return data.getParcelableArrayList<ContentValues>("bluetoothWrapDataListKey")?.map { row
                ->
                RawBatteryRow(
                    row.getAsString("name") ?: row.getAsString("alias") ?: "",
                    row.getAsString("batteryLevelCase"),
                    row.getAsString("batteryLevelLeft"),
                    row.getAsString("batteryLevelRight"),
                    row.getAsString("address").orEmpty(),
                )
            }
        }
        val names = data.getStringArrayList("bluetoothNameListKey") ?: return null
        val levels = data.getStringArrayList("bluetoothBatteryLevelListKey") ?: return null
        if (levels.size != names.size * 4) return null
        return names.mapIndexed { index, name ->
            RawBatteryRow(name, levels[index * 4 + 1], levels[index * 4 + 2], levels[index * 4 + 3])
        }
    }

    fun forget(id: String) {
        synchronized(lastRequests) { lastRequests.remove(id) }
    }

    fun request(
        context: Context,
        id: String,
        force: Boolean = false,
        retryMissing: Boolean = true,
        connections: () -> Set<String> = { AudioConnections.addresses(context) },
        finished: () -> Unit = {},
    ) {
        val started = SystemClock.elapsedRealtimeNanos()
        val shouldRequest =
            synchronized(lastRequests) {
                val previous = lastRequests[id]
                if (!force && previous != null && started - previous < QUERY_COOLDOWN_NANOS) false
                else {
                    lastRequests[id] = started
                    true
                }
            }
        // Prevent refresh-notify-query loops and concurrent requests for the same target.
        if (!shouldRequest) {
            finished()
            return
        }
        val app = context.applicationContext
        fetch(app) { rows ->
            var deferred = false
            try {
                if (synchronized(lastRequests) { lastRequests[id] != started }) return@fetch
                val store = BatteryStore(app)
                val connected = connections()
                val previousTargets = store.load(id)
                val result =
                    BatteryResolver.resolve(
                        rows.orEmpty(),
                        connected,
                        store.knownDevices(),
                        previousTargets,
                        store.selections(id),
                    )
                store.remember(result.connectedDevices)
                val changed = !store.hasSnapshot(id) || previousTargets != result.targets
                if (changed) store.save(id, result.targets)
                if (changed)
                    SmartspacerTargetProvider.notifyChange(app, EarbudsTarget::class.java, id)
                // Fast Pair may fill case/left/right in separate steps after the same event.
                // One bounded follow-up belongs to that event; there is no repeating timer.
                if (retryMissing && result.needsFollowUp) {
                    deferred = true
                    handler.postDelayed(
                        {
                            if (synchronized(lastRequests) { lastRequests[id] != started })
                                finished()
                            else
                                request(
                                    app,
                                    id,
                                    force = true,
                                    retryMissing = false,
                                    connections = connections,
                                    finished = finished,
                                )
                        },
                        FOLLOW_UP_DELAY_MILLIS,
                    )
                }
            } finally {
                if (!deferred) finished()
            }
        }
    }

    /** One system snapshot for the picker; no Bluetooth permission or scanning is needed. */
    fun fetch(context: Context, callback: (List<RawBatteryRow>?) -> Unit) {
        val app = context.applicationContext
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun finish(data: Bundle?, code: Int?) {
            if (!completed.compareAndSet(false, true)) return
            handler.removeCallbacks(timeout)
            val rows =
                if (code == 0 && data != null) runCatching { parse(data) }.getOrNull() else null
            callback(rows)
        }
        timeout = Runnable { finish(null, null) }
        val receiver =
            object : ResultReceiver(handler) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    finish(resultData, resultCode)
                }
            }
        // Settings must receive a Binder-backed callback, not the app's ResultReceiver subclass.
        val parcel = Parcel.obtain()
        val remote =
            try {
                receiver.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                ResultReceiver.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        handler.postDelayed(timeout, RESPONSE_TIMEOUT_MILLIS)
        try {
            app.sendBroadcast(
                Intent(ACTION)
                    .setPackage(SETTINGS_PACKAGE)
                    .putExtra("android.intent.extra.RESULT_RECEIVER", remote)
            )
        } catch (_: SecurityException) {
            handler.post { finish(null, null) }
        }
    }
}
