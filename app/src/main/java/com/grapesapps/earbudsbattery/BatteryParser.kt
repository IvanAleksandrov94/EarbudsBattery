package com.grapesapps.earbudsbattery

import java.util.Locale

enum class EarbudsModel(val title: String, val aliases: List<String>) {
    EARFUN("EarFun Air Pro 4+", listOf("EarFun Air Pro 4+", "EarFun Air Pro 4 Plus")),
    ONEPLUS("OnePlus Buds 4", listOf("OnePlus Buds 4")),
}

data class RawBatteryRow(
    val name: String,
    val case: String?,
    val left: String?,
    val right: String?,
    val address: String = "",
)

data class EarbudsBattery(
    val model: EarbudsModel?,
    val case: Int?,
    val left: Int?,
    val right: Int?,
    val address: String = "",
    val title: String = model?.title.orEmpty(),
)

object BatteryParser {
    private val addressPattern = Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")
    private val whitespacePattern = Regex("\\s+")

    private fun level(text: String?): Int? = text?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }

    /** Stable identity is supplied by Settings, independently of user-visible Bluetooth aliases. */
    fun parseDevices(rows: List<RawBatteryRow>): List<EarbudsBattery> =
        rows
            .filter { it.address.matches(addressPattern) && it.name.isNotBlank() }
            .groupBy { it.address.uppercase(Locale.ROOT) }
            .mapNotNull { (address, entries) ->
                val row = entries.singleOrNull() ?: return@mapNotNull null
                EarbudsBattery(
                        null,
                        level(row.case),
                        level(row.left),
                        level(row.right),
                        address,
                        row.name,
                    )
                    .takeIf { it.case != null || it.left != null || it.right != null }
            }

    fun parseRaw(rows: List<RawBatteryRow>): List<EarbudsBattery> {
        return EarbudsModel.entries.mapNotNull { model ->
            val matching =
                rows.filter { row -> model.aliases.any { normalize(it) == normalize(row.name) } }
            // Multiple pairs with identical names cannot be distinguished reliably.
            val row = matching.singleOrNull() ?: return@mapNotNull null
            EarbudsBattery(model, level(row.case), level(row.left), level(row.right)).takeIf {
                it.case != null || it.left != null || it.right != null
            }
        }
    }

    private fun normalize(text: String): String =
        text
            .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
            .trim()
            .replace(whitespacePattern, " ")
            .lowercase(Locale.ROOT)
}
