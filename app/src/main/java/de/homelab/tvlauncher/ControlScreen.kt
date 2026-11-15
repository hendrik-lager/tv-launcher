package de.homelab.tvlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

// ---- Konfiguration: welche Entitäten auf der Steuerungsseite erscheinen -------------

private val scenes = listOf(
    "scene.szene_tv" to "TV",
    "scene.szene_essen" to "Essen",
    "scene.szene_bugeln" to "Bügeln",
    "scene.szene_putzen" to "Putzen",
    "scene.szene_tagmodus" to "Tagmodus",
    "scene.nachtmodus" to "Nachtmodus",
    "scene.szene_aus" to "Alles aus",
    "scene.jalousie_sonne" to "Jalousie Sonne",
)

private val covers = listOf(
    "cover.rollladen_wohnzimmer" to "Wohnzimmer",
    "cover.rollladen_kuche" to "Küche",
    "cover.schlafzimmer_jalousie_schlafzimmer" to "Schlafzimmer",
)

private val climates = listOf(
    "climate.smart_radiator_thermostat_x" to "Wohnzimmer",
    "climate.smart_radiator_thermostat_x_2" to "Badezimmer",
    "climate.smart_radiator_thermostat_x_3" to "Schlafzimmer",
)

private const val TEMP_STEP = 0.5
private const val TEMP_MIN = 5.0
private const val TEMP_MAX = 30.0

// ---- Zustand ----------------------------------------------------------------------

data class CoverItem(val id: String, val name: String, val state: String)
data class ClimateItem(val id: String, val name: String, val on: Boolean, val current: Double?, val target: Double?)
data class ControlState(val covers: List<CoverItem> = emptyList(), val climates: List<ClimateItem> = emptyList())

private class ControlController(private val scope: CoroutineScope) {
    var state by mutableStateOf(ControlState())
        private set

    suspend fun refresh() {
        val new = withContext(Dispatchers.IO) {
            ControlState(
                covers = covers.mapNotNull { (id, name) ->
                    fetchState(id)?.takeIf { it.state != "unavailable" }?.let { CoverItem(id, name, it.state) }
                },
                climates = climates.mapNotNull { (id, name) ->
                    fetchState(id)?.takeIf { it.state != "unavailable" }?.let {
                        ClimateItem(
                            id, name,
                            on = it.state != "off",
                            current = it.attrs?.optDouble("current_temperature")?.takeIf { v -> !v.isNaN() },
                            target = it.attrs?.optDouble("temperature")?.takeIf { v -> !v.isNaN() },
                        )
                    }
                },
            )
        }
        // Bei Netzfehlern den letzten Stand behalten
        if (new.covers.isNotEmpty() || new.climates.isNotEmpty()) state = new
    }

    fun call(domain: String, service: String, data: JSONObject) {
        scope.launch {
            withContext(Dispatchers.IO) { callService(domain, service, data) }
            delay(1_000)
            refresh()
        }
    }

    fun scene(id: String) = call("scene", "turn_on", JSONObject().put("entity_id", id))

    fun cover(id: String, service: String) = call("cover", service, JSONObject().put("entity_id", id))

    fun changeTarget(item: ClimateItem, delta: Double) {
        if (!item.on) return
        val base = item.target ?: item.current ?: 20.0
        val target = (base + delta).coerceIn(TEMP_MIN, TEMP_MAX)
        // sofort anzeigen, der Abgleich folgt nach dem Dienstaufruf
        state = state.copy(climates = state.climates.map { if (it.id == item.id) it.copy(target = target) else it })
        call("climate", "set_temperature", JSONObject().put("entity_id", item.id).put("temperature", target))
    }

    fun togglePower(item: ClimateItem) =
        call("climate", "set_hvac_mode", JSONObject().put("entity_id", item.id).put("hvac_mode", if (item.on) "off" else "heat"))
}

// ---- Seite ------------------------------------------------------------------------

@Composable
fun ControlScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { ControlController(scope) }
    LaunchedEffect(Unit) {
        while (true) {
            controller.refresh()
            delay(5_000)
        }
    }
    BackHandler(onBack = onBack)
    ControlContent(
        state = controller.state,
        onBack = onBack,
        onScene = controller::scene,
        onCover = controller::cover,
        onTarget = controller::changeTarget,
        onPower = controller::togglePower,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlContent(
    state: ControlState,
    onBack: () -> Unit,
    onScene: (String) -> Unit,
    onCover: (String, String) -> Unit,
    onTarget: (ClimateItem, Double) -> Unit,
    onPower: (ClimateItem) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111A33), Color(0xFF05070D))))
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Rounded.ArrowBack, "Zurück", onBack)
            Spacer(Modifier.width(24.dp))
            Text("Steuerung", color = Color(0xFFF2F5FF), fontSize = 36.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Section("Szenen") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    scenes.forEachIndexed { i, (id, name) ->
                        PillButton(name, if (i == 0) Modifier.focusRequester(first) else Modifier) { onScene(id) }
                    }
                }
            }
            if (state.covers.isNotEmpty()) {
                Section("Rollläden") {
                    state.covers.forEach { c ->
                        ItemRow(c.name, coverLabel(c.state)) {
                            IconAction(Icons.Rounded.KeyboardArrowUp, "Hoch") { onCover(c.id, "open_cover") }
                            IconAction(Icons.Rounded.Stop, "Stopp") { onCover(c.id, "stop_cover") }
                            IconAction(Icons.Rounded.KeyboardArrowDown, "Runter") { onCover(c.id, "close_cover") }
                        }
                    }
                }
            }
            if (state.climates.isNotEmpty()) {
                Section("Heizung") {
                    state.climates.forEach { c ->
                        ItemRow(c.name, climateLabel(c)) {
                            IconAction(Icons.Rounded.Remove, "Kälter", enabled = c.on) { onTarget(c, -TEMP_STEP) }
                            IconAction(Icons.Rounded.Add, "Wärmer", enabled = c.on) { onTarget(c, TEMP_STEP) }
                            IconAction(Icons.Rounded.PowerSettingsNew, "Ein/Aus", highlight = c.on) { onPower(c) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun coverLabel(state: String) = when (state) {
    "open" -> "Offen"
    "closed" -> "Geschlossen"
    "opening" -> "Öffnet …"
    "closing" -> "Schließt …"
    else -> state
}

private fun climateLabel(c: ClimateItem): String {
    fun fmt(v: Double) = String.format(Locale.getDefault(), "%.1f °C", v)
    val now = c.current?.let { "Ist ${fmt(it)}" }
    return if (c.on) listOfNotNull(c.target?.let { "Soll ${fmt(it)}" }, now).joinToString(" · ") else listOfNotNull("Aus", now).joinToString(" · ")
}

// ---- Bausteine --------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color(0xFF8E9ABF), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun ItemRow(title: String, subtitle: String, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFFF2F5FF), fontSize = 22.sp)
            Text(subtitle, color = Color(0xFF8E9ABF), fontSize = 15.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1A2342),
            focusedContainerColor = Color(0xFF6EA8FF),
            contentColor = Color(0xFFF2F5FF),
            focusedContentColor = Color(0xFF0B0F1A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(label, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) Color(0xFF2B5FD9) else Color(0xFF1A2342),
            focusedContainerColor = Color(0xFF6EA8FF),
            contentColor = Color(0xFFF2F5FF).copy(alpha = if (enabled) 1f else 0.3f),
            focusedContentColor = Color(0xFF0B0F1A).copy(alpha = if (enabled) 1f else 0.4f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp))
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Steuerung")
@Composable
private fun ControlPreview() = ControlContent(
    state = ControlState(
        covers = listOf(
            CoverItem("a", "Wohnzimmer", "open"),
            CoverItem("b", "Küche", "closed"),
            CoverItem("c", "Schlafzimmer", "open"),
        ),
        climates = listOf(
            ClimateItem("a", "Wohnzimmer", false, 19.4, 21.0),
            ClimateItem("b", "Badezimmer", true, 18.6, 22.0),
            ClimateItem("c", "Schlafzimmer", false, 18.0, null),
        ),
    ),
    onBack = {}, onScene = {}, onCover = { _, _ -> }, onTarget = { _, _ -> }, onPower = {},
)
