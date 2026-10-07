package com.grapesapps.earbudsbattery

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import com.kieronquinn.app.smartspacer.sdk.model.CompatibilityState
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceAction
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.BaseTemplateData.SubItemLoggingInfo
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate

class EarbudsTarget : SmartspacerTargetProvider() {
    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val store = BatteryStore(provideContext())
        store.register(smartspacerId)
        AudioConnections.ensure(provideContext())
        val cached = store.load(smartspacerId)
        return cached.map { createTarget(provideContext(), smartspacerId, it) }
    }

    override fun getConfig(smartspacerId: String?): Config {
        val context = provideContext()
        smartspacerId?.let { BatteryStore(context).register(it) }
        return Config(
            label = context.getString(R.string.target_label),
            description = context.getString(R.string.target_description),
            icon = AndroidIcon.createWithResource(context, R.drawable.ic_earbuds),
            allowAddingMoreThanOnce = true,
            configActivity = Intent(context, DevicePickerActivity::class.java),
            widgetProvider = "${context.packageName}.widgets.battery",
            refreshPeriodMinutes = 0,
            refreshIfNotVisible = false,
            compatibilityState =
                if (SystemBatterySource.isAvailable(context) && BatteryWidget.find(context) != null)
                    CompatibilityState.Compatible
                else CompatibilityState.Incompatible(context.getString(R.string.source_missing)),
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String) = false

    override fun onProviderRemoved(smartspacerId: String) {
        SystemBatterySource.forget(smartspacerId)
        BatteryStore(provideContext()).remove(smartspacerId)
    }

    companion object {
        fun batteryLine(context: Context, battery: EarbudsBattery): String {
            fun value(level: Int?) = level?.let { "$it%" } ?: "—"
            return context.getString(
                R.string.battery_line,
                value(battery.case),
                value(battery.left),
                value(battery.right),
            )
        }

        // The SDK has no public no-complications flag; its merger inspects this template metadata.
        @SuppressLint("RestrictedApi")
        fun createTarget(
            context: Context,
            smartspacerId: String,
            battery: EarbudsBattery,
        ): SmartspaceTarget {
            val id =
                "earbuds_${smartspacerId}_${battery.address.ifEmpty { battery.model?.name.orEmpty() }}"
            val launchIntent = CompanionApp.packageName(battery.title)?.let {
                context.packageManager.getLaunchIntentForPackage(it)
            }
            return TargetTemplate.Basic(
                    id = id,
                    componentName = ComponentName(context, EarbudsTarget::class.java),
                    title = Text(battery.title),
                    subtitle = Text(batteryLine(context, battery)),
                    icon = Icon(AndroidIcon.createWithResource(context, R.drawable.ic_earbuds)),
                    onClick = launchIntent?.let { TapAction(id = "${id}_launch", intent = it) },
                )
                .create()
                .apply {
                    canBeDismissed = false
                    canTakeTwoComplications = false
                    // A nonempty base ID reserves the supplemental slot in Smartspacer's merger.
                    // No supplemental text/icon is emitted, so this does not draw an extra item.
                    baseAction = SmartspaceAction("${id}_reserved", title = "")
                    // Stop primary weather complications from replacing the model name as well.
                    templateData?.primaryItem =
                        templateData
                            ?.primaryItem
                            ?.copy(
                                loggingInfo =
                                    SubItemLoggingInfo(
                                        SmartspaceTarget.FEATURE_MEDIA,
                                        0,
                                        context.packageName,
                                    )
                            )
                }
        }
    }
}
