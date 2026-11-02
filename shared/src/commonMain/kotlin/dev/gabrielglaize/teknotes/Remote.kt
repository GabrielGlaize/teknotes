package dev.gabrielglaize.teknotes

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [inMemorySession]: tests keep each session apart instead of sharing the platform storage. */
internal fun createRemote(config: SupabaseConfig, inMemorySession: Boolean): SupabaseClient =
    createSupabaseClient(config.url, config.key) {
        install(Auth) {
            if (inMemorySession) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
        }
        install(Postgrest)
        install(Functions)
    }

// Rows as they are in Supabase (snake_case). The server fills authorship and dates itself.

@Serializable
internal data class SoundsystemRow(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val department: String?,
    val styles: List<String>,
    val links: List<String>,
)

@Serializable
internal data class ArtistRow(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val styles: List<String>,
    val links: List<String>,
)

@Serializable
internal data class MembershipRow(
    @SerialName("soundsystem_id") val soundsystemId: String,
    @SerialName("artist_id") val artistId: String,
)

@Serializable
internal data class NightRow(
    val id: String,
    val date: String,
    val department: String?,
    val title: String,
    val comment: String,
    val shared: Boolean,
)

@Serializable
internal data class NightEntryRow(
    val id: String,
    @SerialName("night_id") val nightId: String,
    @SerialName("soundsystem_id") val soundsystemId: String?,
    @SerialName("artist_id") val artistId: String?,
    val rating: Int?,
    val comment: String,
)

@Serializable
internal data class ProfileRow(val id: String, val pseudo: String)

internal fun Soundsystem.toRow() = SoundsystemRow(id, name, aliases, department, styles.map { it.id }, links)

internal fun SoundsystemRow.toModel() =
    Soundsystem(id, name, aliases, department, styles.mapNotNull(Style::fromId), links)

internal fun Artist.toRow() = ArtistRow(id, name, aliases, styles.map { it.id }, links)

internal fun ArtistRow.toModel() = Artist(id, name, aliases, styles.mapNotNull(Style::fromId), links)
