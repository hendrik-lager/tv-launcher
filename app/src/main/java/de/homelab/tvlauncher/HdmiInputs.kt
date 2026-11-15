package de.homelab.tvlauncher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class HdmiInput(
    val id: String,
    val label: String,
    val connected: Boolean,
)

/** Liefert die aktuelle Liste der HDMI-Eingänge und aktualisiert sie live. */
@Composable
fun rememberHdmiInputs(): List<HdmiInput> {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager }
    var inputs by remember { mutableStateOf(queryHdmiInputs(context, manager)) }

    DisposableEffect(manager) {
        val refresh = { inputs = queryHdmiInputs(context, manager) }
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputAdded(inputId: String) = refresh()
            override fun onInputRemoved(inputId: String) = refresh()
            override fun onInputUpdated(inputId: String) = refresh()
            override fun onInputStateChanged(inputId: String, state: Int) = refresh()
        }
        manager.registerCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterCallback(callback) }
    }
    return inputs
}

private fun queryHdmiInputs(context: Context, manager: TvInputManager): List<HdmiInput> =
    manager.tvInputList
        .filter { it.type == TvInputInfo.TYPE_HDMI }
        // CEC-Geräte hängen an einem physischen Port; nur Port-Eingänge zeigen
        .filter { it.parentId == null }
        .map { info ->
            val custom = info.loadCustomLabel(context)?.toString()
            HdmiInput(
                id = info.id,
                label = custom?.takeIf { it.isNotBlank() }
                    ?: info.loadLabel(context)?.toString()
                    ?: "HDMI",
                connected = manager.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED,
            )
        }
        .sortedBy { it.label }

fun launchHdmiInput(context: Context, input: HdmiInput) {
    val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "${input.label} konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
    }
}

fun openSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_SETTINGS),
        Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(context, "Einstellungen nicht verfügbar", Toast.LENGTH_SHORT).show()
}
