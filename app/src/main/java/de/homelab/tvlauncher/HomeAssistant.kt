package de.homelab.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** [alert] hebt den Wert hervor, z. B. bei einem offenen Fenster. */
data class HaStatus(val label: String, val value: String, val alert: Boolean = false)

/** Anzuzeigende Home-Assistant-Entitäten: entity_id to Beschriftung. */
private val entities = listOf(
    "sensor.smart_radiator_thermostat_x_temperatur" to "Wohnzimmer",
    "sensor.smartsensor02_temperatur" to "Bad",
    "sensor.smart_radiator_thermostat_x_temperatur_3" to "Schlafzimmer",
)

/** Fensterkontakte (binary_sensor: on = offen). */
private val windows = listOf(
    "binary_sensor.fensterkontakt_wohnzimmer" to "Fenster Wohnzimmer",
    "binary_sensor.fensterkontakt_badezimmer" to "Fenster Bad",
)

private const val WEATHER_ENTITY = "weather.openweathermap"

private const val REFRESH_MS = 60_000L

/** Fragt die Werte regelmäßig ab. Ohne ha.url/ha.token in local.properties bleibt die Liste leer. */
@Composable
fun rememberHaStatus(): List<HaStatus> {
    var status by remember { mutableStateOf<List<HaStatus>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (BuildConfig.HA_URL.isBlank() || BuildConfig.HA_TOKEN.isBlank()) return@LaunchedEffect
        while (true) {
            val result = withContext(Dispatchers.IO) {
                val temps = entities.mapNotNull { (id, label) -> fetchState(id)?.let { formatValue(it) }?.let { HaStatus(label, it) } }
                val wins = windows.mapNotNull { (id, label) ->
                    when (fetchState(id)?.state) {
                        "on" -> HaStatus(label, "Offen", alert = true)
                        "off" -> HaStatus(label, "Zu")
                        else -> null
                    }
                }
                temps + wins
            }
            // Bei Netzfehlern den letzten bekannten Stand behalten
            if (result.isNotEmpty()) status = result
            delay(REFRESH_MS)
        }
    }
    return status
}

class HaState(val state: String, val unit: String, val attrs: JSONObject?)

private fun formatValue(s: HaState): String? {
    val number = s.state.toDoubleOrNull() ?: return null // unavailable/unknown ausblenden
    return String.format(Locale.getDefault(), "%.1f", number) + if (s.unit.isNotEmpty()) " ${s.unit}" else ""
}

fun fetchState(entityId: String): HaState? = runCatching {
    val conn = URL("${BuildConfig.HA_URL}/api/states/$entityId").openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.HA_TOKEN}")
        if (conn.responseCode != 200) return@runCatching null
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        HaState(
            json.getString("state"),
            json.optJSONObject("attributes")?.optString("unit_of_measurement").orEmpty(),
            json.optJSONObject("attributes"),
        )
    } finally {
        conn.disconnect()
    }
}.getOrNull()

/** Ruft einen Home-Assistant-Dienst auf, z. B. callService("scene", "turn_on", {"entity_id": ...}). */
fun callService(domain: String, service: String, data: JSONObject): Boolean = runCatching {
    val conn = URL("${BuildConfig.HA_URL}/api/services/$domain/$service").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.HA_TOKEN}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(data.toString().toByteArray()) }
        conn.responseCode in 200..299
    } finally {
        conn.disconnect()
    }
}.getOrDefault(false)

/** [condition] ist der HA-Wetterzustand (z. B. "rainy"), [temperature] bereits formatiert. */
data class HaWeather(val condition: String, val temperature: String)

@Composable
fun rememberHaWeather(): HaWeather? {
    var weather by remember { mutableStateOf<HaWeather?>(null) }
    LaunchedEffect(Unit) {
        if (BuildConfig.HA_URL.isBlank() || BuildConfig.HA_TOKEN.isBlank()) return@LaunchedEffect
        while (true) {
            val result = withContext(Dispatchers.IO) {
                fetchState(WEATHER_ENTITY)?.let { st ->
                    val temp = st.attrs?.optDouble("temperature")?.takeIf { !it.isNaN() } ?: return@let null
                    val unit = st.attrs?.optString("temperature_unit")?.takeIf { it.isNotEmpty() } ?: "°C"
                    HaWeather(st.state, String.format(Locale.getDefault(), "%.1f %s", temp, unit))
                }
            }
            if (result != null) weather = result
            delay(REFRESH_MS)
        }
    }
    return weather
}
