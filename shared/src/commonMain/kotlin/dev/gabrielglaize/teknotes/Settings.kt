package dev.gabrielglaize.teknotes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.gabrielglaize.teknotes.db.TeknotesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class Appearance { SYSTEM, DARK, LIGHT }

data class Preferences(
    val appearance: Appearance,
    /** Field mode: dark, dimmed accent, larger text and buttons, for use in the middle of a night. */
    val fieldMode: Boolean,
)

/** App preferences, kept on the phone only. */
class SettingsRepository internal constructor(private val queries: TeknotesQueries) {
    private fun value(key: String): Flow<String?> =
        queries.setting(key).asFlow().mapToOneOrNull(Dispatchers.Default)

    fun preferences(): Flow<Preferences> = combine(value(APPEARANCE), value(FIELD_MODE)) { appearance, field ->
        Preferences(
            appearance = Appearance.entries.firstOrNull { it.name == appearance } ?: Appearance.SYSTEM,
            fieldMode = field == "true",
        )
    }

    fun current(): Preferences = Preferences(
        Appearance.entries.firstOrNull { it.name == queries.setting(APPEARANCE).executeAsOneOrNull() } ?: Appearance.SYSTEM,
        queries.setting(FIELD_MODE).executeAsOneOrNull() == "true",
    )

    fun setAppearance(appearance: Appearance) = queries.putSetting(APPEARANCE, appearance.name)

    fun setFieldMode(enabled: Boolean) = queries.putSetting(FIELD_MODE, enabled.toString())

    // For Swift: plain callbacks instead of Flows.
    fun watchPreferences(onChange: (Preferences) -> Unit): Watcher = preferences().watch(onChange)

    private companion object {
        const val APPEARANCE = "appearance"
        const val FIELD_MODE = "field_mode"
    }
}
