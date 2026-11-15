package de.homelab.tvlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text

private val TextPrimary = Color(0xFFF2F5FF)
private val TextSecondary = Color(0xFF8E9ABF)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { InputSettings(context) }
    val inputs = rememberHdmiInputs()
    BackHandler(onBack = onBack)
    SettingsContent(
        inputs = inputs,
        settings = settings,
        onBack = onBack,
        onSystemSettings = { openSettings(context) },
    )
}

@Composable
fun SettingsContent(
    inputs: List<HdmiInput>,
    settings: InputSettings,
    onBack: () -> Unit,
    onSystemSettings: () -> Unit,
) {
    var editing by remember { mutableStateOf<HdmiInput?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111A33), Color(0xFF05070D))))
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Rounded.ArrowBack, "Zurück", onBack, Modifier.focusRequester(first))
            Spacer(Modifier.width(24.dp))
            Text("Einstellungen", color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("HDMI-Eingänge", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (inputs.isEmpty()) {
                    Text("Der TV meldet keine HDMI-Eingänge.", color = TextSecondary, fontSize = 16.sp)
                }
                inputs.forEach { input ->
                    val visible = settings.isVisible(input.id)
                    val name = settings.names[input.id]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                name ?: input.label,
                                color = if (visible) TextPrimary else TextSecondary,
                                fontSize = 22.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(if (visible) "Wird angezeigt" else "Ausgeblendet")
                                    if (name != null) append(" · Original: ${input.label}")
                                },
                                color = TextSecondary,
                                fontSize = 15.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconAction(
                                if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                if (visible) "Ausblenden" else "Anzeigen",
                                highlight = visible,
                            ) { settings.setVisible(input.id, !visible) }
                            IconAction(Icons.Rounded.Edit, "Umbenennen") { editing = input }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("System", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                PillButton("Android-Systemeinstellungen öffnen", onClick = onSystemSettings)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { input ->
        RenameDialog(
            original = input.label,
            current = settings.names[input.id].orEmpty(),
            onSave = { settings.rename(input.id, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RenameDialog(original: String, current: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val field = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(560.dp)
                .background(Color(0xFF1A2342), RoundedCornerShape(24.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Eingang umbenennen", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text("Original: $original", color = TextSecondary, fontSize = 15.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 22.sp),
                cursorBrush = SolidColor(Color(0xFF6EA8FF)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .focusRequester(field),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Speichern") { onSave(text) }
                PillButton("Zurücksetzen") { onSave(null) }
                PillButton("Abbrechen", onClick = onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}
