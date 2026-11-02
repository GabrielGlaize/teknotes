package dev.gabrielglaize.teknotes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.gabrielglaize.teknotes.db.TeknotesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class Appearance { SYSTEM, DARK, LIGHT }

data class Preferences(val appearance: Appearance)

/** App preferences and the lock of the app, kept on the phone only. */
class SettingsRepository internal constructor(
    private val queries: TeknotesQueries,
    private val clock: () -> Instant,
) {
    fun preferences(): Flow<Preferences> =
        queries.setting(APPEARANCE).asFlow().mapToOneOrNull(Dispatchers.Default).map { Preferences(appearance(it)) }

    fun current(): Preferences = Preferences(appearance(value(APPEARANCE)))

    fun setAppearance(appearance: Appearance) = queries.putSetting(APPEARANCE, appearance.name)

    private fun appearance(stored: String?) = Appearance.entries.firstOrNull { it.name == stored } ?: Appearance.LIGHT

    private fun value(key: String) = queries.setting(key).executeAsOneOrNull()

    // First launch ---------------------------------------------------------------

    val tutorialDone: Boolean get() = value(TUTORIAL) == "true"

    fun setTutorialDone() = queries.putSetting(TUTORIAL, "true")

    // Code that opens the app. It hides the notebook from whoever picks up the phone; the
    // data itself isn't encrypted. Nothing can recover it: forgetting it means reinstalling.

    val hasPasscode: Boolean get() = !value(PASSCODE).isNullOrEmpty()

    /** Four digits. */
    fun setPasscode(code: String) {
        require(code.length == PASSCODE_LENGTH && code.all { it in '0'..'9' }) { "Le code fait 4 chiffres" }
        val salt = Uuid.random().toString()
        queries.putSetting(PASSCODE, salt + ":" + Sha256.stretched(salt, code))
        queries.putSetting(FAILURES, "0")
        queries.putSetting(LOCKED_UNTIL, "0")
    }

    fun removePasscode() {
        queries.putSetting(PASSCODE, "")
        queries.putSetting(BIOMETRICS, "false")
    }

    /** Seconds to wait before another try: 30 s after every 5 wrong codes. */
    fun passcodeWaitSeconds(): Int {
        val until = value(LOCKED_UNTIL)?.toLongOrNull() ?: 0
        return ((until - clock().toEpochMilliseconds() + 999) / 1000).coerceAtLeast(0).toInt()
    }

    /** False for a wrong code, and while waiting after too many of them. */
    fun checkPasscode(code: String): Boolean {
        if (passcodeWaitSeconds() > 0) return false
        val (salt, hash) = value(PASSCODE)?.split(":")?.takeIf { it.size == 2 } ?: return false
        if (Sha256.stretched(salt, code) == hash) {
            queries.putSetting(FAILURES, "0")
            return true
        }
        val failures = (value(FAILURES)?.toIntOrNull() ?: 0) + 1
        queries.putSetting(FAILURES, failures.toString())
        if (failures % MAX_TRIES == 0) {
            queries.putSetting(LOCKED_UNTIL, (clock().toEpochMilliseconds() + WAIT_MS).toString())
        }
        return false
    }

    /** Face ID or fingerprint instead of typing the code. */
    val biometricsEnabled: Boolean get() = value(BIOMETRICS) == "true"

    fun setBiometricsEnabled(enabled: Boolean) = queries.putSetting(BIOMETRICS, enabled.toString())

    // For Swift: plain callbacks instead of Flows.
    fun watchPreferences(onChange: (Preferences) -> Unit): Watcher = preferences().watch(onChange)

    private companion object {
        const val APPEARANCE = "appearance"
        const val TUTORIAL = "tutorial_done"
        const val PASSCODE = "passcode"
        const val FAILURES = "passcode_failures"
        const val LOCKED_UNTIL = "passcode_locked_until"
        const val BIOMETRICS = "biometrics"
        const val PASSCODE_LENGTH = 4
        const val MAX_TRIES = 5
        const val WAIT_MS = 30_000L
    }
}
