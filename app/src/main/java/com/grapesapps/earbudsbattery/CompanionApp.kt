package com.grapesapps.earbudsbattery

import java.util.Locale

/** Companion app selection follows the original device name, independently of its address. */
internal object CompanionApp {
    fun packageName(deviceName: String): String? {
        val name = deviceName.lowercase(Locale.ROOT)
        return when {
            "earfun" in name -> "com.corelink.earfun"
            "oneplus" in name -> "com.heytap.headset"
            else -> null
        }
    }
}
