package de.homelab.tvlauncher

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgTop = Color(0xFF111A33)
private val BgBottom = Color(0xFF05070D)
private val Accent = Color(0xFF6EA8FF)
private val CardIdle = Color(0xFF1A2342)
private val CardFocused = Color(0xFF2B3C73)
private val TextPrimary = Color(0xFFF2F5FF)
private val TextSecondary = Color(0xFF8E9ABF)
private val Warning = Color(0xFFFFB74D)
private val Online = Color(0xFF4ADE80)

@Composable
fun LauncherScreen() {
    var page by remember { mutableStateOf(Page.Home) }
    when (page) {
        Page.Home -> HomePage(onControlsClick = { page = Page.Controls })
        Page.Controls -> ControlScreen(onBack = { page = Page.Home })
    }
}

private enum class Page { Home, Controls }

@Composable
private fun HomePage(onControlsClick: () -> Unit) {
    val context = LocalContext.current
    val inputs = rememberHdmiInputs()
    val status = rememberHaStatus()
    // Ein Launcher darf per Zurück-Taste nicht beendet werden.
    BackHandler(enabled = true) {}
    LauncherContent(
        onControlsClick = onControlsClick,
        inputs = inputs,
        status = status,
        onInputClick = { launchHdmiInput(context, it) },
        onSettingsClick = { openSettings(context) },
    )
}

@Composable
fun LauncherContent(
    inputs: List<HdmiInput>,
    onInputClick: (HdmiInput) -> Unit,
    onSettingsClick: () -> Unit,
    status: List<HaStatus> = emptyList(),
    onControlsClick: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {
        Clock(Modifier.align(Alignment.TopStart))
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (BuildConfig.HA_URL.isNotBlank() || LocalInspectionMode.current) {
                RoundIconButton(Icons.Rounded.Dashboard, "Steuerung", onControlsClick)
            }
            RoundIconButton(Icons.Rounded.Settings, "Einstellungen", onSettingsClick)
        }

        Column(Modifier.fillMaxSize().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (inputs.isEmpty()) {
            Text(
                "Keine HDMI-Eingänge gefunden",
                color = TextSecondary,
                fontSize = 24.sp,
            )
        } else {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 32.dp),
            ) {
                items(inputs, key = { it.id }) { input ->
                    val mod = if (input == inputs.first()) Modifier.focusRequester(first) else Modifier
                    InputCard(input, mod) { onInputClick(input) }
                }
            }
        }

        }
        if (status.isNotEmpty()) {
            StatusBar(status)
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBar(status: List<HaStatus>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        status.forEach { item ->
            Column(
                Modifier
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.value,
                    color = if (item.alert) Warning else TextPrimary,
                    fontSize = 22.sp,
                    maxLines = 1,
                    fontWeight = if (item.alert) FontWeight.SemiBold else FontWeight.Light,
                )
                Text(item.label, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    val time = remember(now.time / 60_000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now.time / 60_000) { SimpleDateFormat("EEEE, d. MMMM", Locale.getDefault()).format(now) }
    Column(modifier) {
        Text(time, color = TextPrimary, fontSize = 56.sp, fontWeight = FontWeight.Light)
        Text(date, color = TextSecondary, fontSize = 20.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Accent,
            contentColor = TextPrimary,
            focusedContentColor = Color(0xFF0B0F1A),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(30.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InputCard(input: HdmiInput, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Accent else Color.Transparent, label = "border")
    val shape = RoundedCornerShape(22.dp)

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(210.dp)
            .height(230.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(3.dp, border, shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CardIdle,
            focusedContainerColor = CardFocused,
            contentColor = TextPrimary,
            focusedContentColor = TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.SettingsInputHdmi,
                contentDescription = null,
                tint = if (focused) Accent else TextSecondary,
                modifier = Modifier.size(68.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(input.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(if (input.connected) Online else TextSecondary.copy(alpha = 0.5f), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (input.connected) "Signal erkannt" else "Kein Signal",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Mit Eingängen")
@Composable
private fun LauncherPreview() {
    LauncherContent(
        inputs = listOf(
            HdmiInput("1", "HDMI 1 · Apple TV", true),
            HdmiInput("2", "HDMI 2 · PlayStation", true),
            HdmiInput("3", "HDMI 3", false),
            HdmiInput("4", "HDMI 4 · PC", true),
        ),
        onInputClick = {},
        onSettingsClick = {},
        status = listOf(
            HaStatus("Wohnzimmer", "19,4 °C"),
            HaStatus("Bad", "18,9 °C"),
            HaStatus("Schlafzimmer", "18,0 °C"),
            HaStatus("Außen", "8,8 °C"),
            HaStatus("Fenster Wohnzimmer", "Offen", alert = true),
            HaStatus("Fenster Bad", "Zu"),
        ),
    )
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Leer")
@Composable
private fun LauncherEmptyPreview() = LauncherContent(emptyList(), {}, {})
