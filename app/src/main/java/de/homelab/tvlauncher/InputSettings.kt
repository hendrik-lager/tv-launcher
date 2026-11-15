package de.homelab.tvlauncher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Gespeicherte Einstellungen für die HDMI-Eingänge: ausgeblendete Eingänge und eigene Namen. */
class InputSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("inputs", Context.MODE_PRIVATE)

    var hidden by mutableStateOf(prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet())
        private set

    var names by mutableStateOf(
        prefs.all.filterKeys { it.startsWith(NAME_PREFIX) }
            .mapKeys { it.key.removePrefix(NAME_PREFIX) }
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
            .toMap()
    )
        private set

    fun isVisible(id: String) = id !in hidden

    fun setVisible(id: String, visible: Boolean) {
        hidden = if (visible) hidden - id else hidden + id
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply()
    }

    /** Leerer Name setzt auf den Standardnamen des TVs zurück. */
    fun rename(id: String, name: String?) {
        val clean = name?.trim().orEmpty()
        names = if (clean.isEmpty()) names - id else names + (id to clean)
        prefs.edit().apply { if (clean.isEmpty()) remove(NAME_PREFIX + id) else putString(NAME_PREFIX + id, clean) }.apply()
    }

    /** Wendet Namen an und entfernt ausgeblendete Eingänge. */
    fun applyTo(inputs: List<HdmiInput>): List<HdmiInput> =
        inputs.filter { isVisible(it.id) }.map { it.copy(label = names[it.id] ?: it.label) }

    private companion object {
        const val KEY_HIDDEN = "hidden"
        const val NAME_PREFIX = "name."
    }
}
