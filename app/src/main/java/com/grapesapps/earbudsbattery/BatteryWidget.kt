package com.grapesapps.earbudsbattery

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerWidgetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.getColumnSpan
import com.kieronquinn.app.smartspacer.sdk.utils.getRowSpan

/** Google widget updates are a signal; raw Settings data keeps left/right values separate. */
class BatteryWidget : SmartspacerWidgetProvider() {
    override fun onWidgetChanged(smartspacerId: String, remoteViews: RemoteViews?) {
        AudioConnections.ensure(provideContext())
        if (remoteViews != null)
            SystemBatterySource.request(provideContext(), smartspacerId, force = true)
    }

    override fun onViewDataChanged(smartspacerId: String, viewIdentifier: String?, viewId: Int?) {
        SystemBatterySource.request(provideContext(), smartspacerId, force = true)
    }

    override fun getAppWidgetProviderInfo(smartspacerId: String): AppWidgetProviderInfo? =
        find(provideContext())

    override fun getConfig(smartspacerId: String) =
        Config(width = getColumnSpan(5), height = getRowSpan(5))

    companion object {
        fun find(context: Context): AppWidgetProviderInfo? {
            val component =
                ComponentName(
                    "com.google.android.settings.intelligence",
                    "com.google.android.settings.intelligence.modules.batterywidget.impl.BatteryAppWidgetProvider",
                )
            return AppWidgetManager.getInstance(context).installedProviders.firstOrNull {
                it.provider == component
            }
        }
    }
}
