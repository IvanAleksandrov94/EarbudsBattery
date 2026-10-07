package com.grapesapps.earbudsbattery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists target snapshots, user selections and the device catalogue across process restarts. */
class BatteryStore(context: Context) {
    private val preferences = context.getSharedPreferences("battery", Context.MODE_PRIVATE)

    fun hasSnapshot(id: String): Boolean = preferences.contains(id)

    fun register(id: String) {
        if (!hasSnapshot(id)) save(id, emptyList())
    }

    fun targetIds(): Set<String> =
        preferences.all
            .asSequence()
            .filter { (key, value) ->
                key != "known_devices" && value is String && value.startsWith("{\"batteries\"")
            }
            .map { it.key }
            .toSet()

    fun save(id: String, batteries: List<EarbudsBattery>) {
        val json =
            JSONObject()
                .put(
                    "batteries",
                    JSONArray().apply {
                        batteries.forEach { battery ->
                            put(
                                JSONObject()
                                    .put("model", battery.model?.name ?: JSONObject.NULL)
                                    .put("address", battery.address)
                                    .put("title", battery.title)
                                    .put("case", battery.case ?: JSONObject.NULL)
                                    .put("left", battery.left ?: JSONObject.NULL)
                                    .put("right", battery.right ?: JSONObject.NULL)
                            )
                        }
                    },
                )
        preferences.edit().putString(id, json.toString()).apply()
    }

    fun load(id: String): List<EarbudsBattery> {
        val text = preferences.getString(id, null) ?: return emptyList()
        return runCatching {
                val batteries = JSONObject(text).getJSONArray("batteries")
                (0 until batteries.length()).map { i ->
                    val item = batteries.getJSONObject(i)
                    fun level(key: String) = if (item.isNull(key)) null else item.getInt(key)
                    val model =
                        if (item.isNull("model")) null
                        else EarbudsModel.valueOf(item.getString("model"))
                    EarbudsBattery(
                        model,
                        level("case"),
                        level("left"),
                        level("right"),
                        item.optString("address", ""),
                        item.optString("title", model?.title.orEmpty()),
                    )
                }
            }
            .getOrDefault(emptyList())
    }

    fun selection(id: String): String? = preferences.getString("selection:$id", null)

    fun selections(id: String): Set<String>? =
        preferences.getStringSet("selections:$id", null)?.toSet()
            ?: selection(id)?.let { setOf(it) }

    fun remember(devices: List<EarbudsBattery>) {
        if (devices.isEmpty()) return
        val previous = knownDevices()
        val merged =
            (previous + devices)
                .associateBy { it.address }
                .values
                .filter { it.address.isNotEmpty() }
        if (previous != merged) save("known_devices", merged)
    }

    fun knownDevices(): List<EarbudsBattery> = load("known_devices")

    fun selectMany(id: String, addresses: Set<String>, connected: List<EarbudsBattery>) {
        preferences
            .edit()
            .putStringSet("selections:$id", addresses.toSet())
            .remove("selection:$id")
            .apply()
        save(id, connected.filter { it.address in addresses })
    }

    fun select(id: String, battery: EarbudsBattery) {
        preferences.edit().remove("selections:$id").apply()
        remember(listOf(battery))
        preferences.edit().putString("selection:$id", battery.address).apply()
        save(id, listOf(battery))
    }

    fun remove(id: String) {
        preferences.edit().remove(id).remove("selection:$id").remove("selections:$id").apply()
    }
}
