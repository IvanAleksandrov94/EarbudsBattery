package com.grapesapps.earbudsbattery

import java.util.Locale

/**
 * Pure snapshot reduction: connectivity is authoritative; missing battery parts retain their last
 * value.
 */
internal object BatteryResolver {
    data class Result(
        val connectedDevices: List<EarbudsBattery>,
        val targets: List<EarbudsBattery>,
        val needsFollowUp: Boolean,
    )

    fun resolve(
        rows: List<RawBatteryRow>,
        connectedAddresses: Set<String>,
        knownDevices: List<EarbudsBattery>,
        currentTargets: List<EarbudsBattery>,
        selectedAddresses: Set<String>?,
    ): Result {
        val connected = connectedAddresses.mapTo(sortedSetOf()) { it.uppercase(Locale.ROOT) }
        val selected = selectedAddresses?.mapTo(hashSetOf()) { it.uppercase(Locale.ROOT) }
        val fresh =
            BatteryParser.parseDevices(
                    rows.filter { it.address.uppercase(Locale.ROOT) in connected }
                )
                .associateBy { it.address }
        val previous = (knownDevices + currentTargets).associateBy { it.address }
        val resolved =
            connected.mapNotNull { address ->
                val current = fresh[address]
                val old = previous[address]
                current?.copy(
                    case = current.case ?: old?.case,
                    left = current.left ?: old?.left,
                    right = current.right ?: old?.right,
                ) ?: old
            }
        fun isSelected(device: EarbudsBattery): Boolean =
            selected?.contains(device.address)
                ?: EarbudsModel.entries.any { device.title in it.aliases }
        return Result(
            connectedDevices = resolved,
            targets = resolved.filter(::isSelected),
            needsFollowUp =
                fresh.values.any {
                    isSelected(it) && (it.case == null || it.left == null || it.right == null)
                },
        )
    }
}
