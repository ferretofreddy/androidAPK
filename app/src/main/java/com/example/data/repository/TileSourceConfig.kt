package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences

data class TileSourcePreset(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val requiresApiKey: Boolean = false,
    val description: String
)

class TileSourceConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("garmin_tile_source_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SELECTED_PRESET = "selected_preset_id"
        const val KEY_CUSTOM_URL = "custom_tile_url"
        const val KEY_API_KEY = "tile_api_key"

        val PRESET_OSM = TileSourcePreset(
            id = "osm_standard",
            name = "OpenStreetMap Standard",
            urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            requiresApiKey = false,
            description = "Servidor comunitario de OSM. (Visualización interactiva y cachés normales)"
        )

        val PRESET_OPENMAPTILES = TileSourcePreset(
            id = "self_hosted",
            name = "Servidor Propio / TileServer GL",
            urlTemplate = "http://192.168.1.100:8080/styles/basic/{z}/{x}/{y}.png",
            requiresApiKey = false,
            description = "Servidor TileServer GL u OpenMapTiles local/propio para descargas masivas ilimitadas."
        )

        val PRESET_MAPTILER = TileSourcePreset(
            id = "maptiler",
            name = "MapTiler Cloud",
            urlTemplate = "https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key={api_key}",
            requiresApiKey = true,
            description = "Requiere API Key de MapTiler. Apto para descargas de mapas según plan."
        )

        val PRESET_THUNDERFOREST = TileSourcePreset(
            id = "thunderforest",
            name = "Thunderforest Outdoors",
            urlTemplate = "https://tile.thunderforest.com/outdoors/{z}/{x}/{y}.png?apikey={api_key}",
            requiresApiKey = true,
            description = "Requiere API Key de Thunderforest. Mapas topográficos para exteriores."
        )

        val PRESETS = listOf(PRESET_OSM, PRESET_OPENMAPTILES, PRESET_MAPTILER, PRESET_THUNDERFOREST)
    }

    var selectedPresetId: String
        get() = prefs.getString(KEY_SELECTED_PRESET, PRESET_OSM.id) ?: PRESET_OSM.id
        set(value) = prefs.edit().putString(KEY_SELECTED_PRESET, value).apply()

    var customUrl: String
        get() = prefs.getString(KEY_CUSTOM_URL, PRESET_OPENMAPTILES.urlTemplate) ?: PRESET_OPENMAPTILES.urlTemplate
        set(value) = prefs.edit().putString(KEY_CUSTOM_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    fun getActivePreset(): TileSourcePreset {
        return PRESETS.find { it.id == selectedPresetId } ?: PRESET_OSM
    }

    fun getActiveTileUrlTemplate(): String {
        val preset = getActivePreset()
        val template = if (preset.id == PRESET_OPENMAPTILES.id) {
            customUrl.ifBlank { PRESET_OPENMAPTILES.urlTemplate }
        } else {
            preset.urlTemplate
        }
        return template.replace("{api_key}", apiKey)
    }
}
