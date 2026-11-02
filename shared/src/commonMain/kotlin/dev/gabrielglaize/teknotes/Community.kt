package dev.gabrielglaize.teknotes

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

/** One version of a directory entry, as kept by the server. */
data class Revision(
    val id: Long,
    /** Null: imported data, or an account deleted since. */
    val authorId: String?,
    /** Same: null when nobody can be named. */
    val authorPseudo: String?,
    /** ISO date (yyyy-MM-dd). */
    val date: String,
    /** What this version changed, in plain French ("Nom", "Styles"…). Empty for the first version. */
    val changes: List<String>,
    val name: String,
    val aliases: List<String>,
    val department: String?,
    val styles: List<Style>,
    val links: List<String>,
)

/** A night someone else shared where this soundsystem or artist was heard. */
data class Sighting(
    /** ISO date (yyyy-MM-dd). */
    val date: String,
    val department: String?,
    val userId: String,
    val pseudo: String,
)

data class MergeProposal(
    val id: Long,
    /** The other entry of the pair. */
    val otherId: String,
    val otherName: String,
    /** True: the other entry would be absorbed into this one. False: the other way round. */
    val keepsThisEntry: Boolean,
    val approvals: Int,
    val approvedByMe: Boolean,
) {
    val needed: Int get() = APPROVALS_NEEDED

    companion object {
        const val APPROVALS_NEEDED = 3
    }
}

data class BlockedUser(val id: String, val pseudo: String)

enum class ReportReason(val label: String) {
    FALSE("Fausses informations"),
    OFFENSIVE("Contenu offensant"),
    PRIVATE("Données personnelles"),
    SPAM("Publicité ou spam"),
    OTHER("Autre"),
}

/**
 * What needs the server and an account: history, merges, reports, blocks, shared nights.
 * Everything here is online only; the notebook never depends on it.
 */
class Community internal constructor(
    private val remote: SupabaseClient?,
    private val directory: DirectoryRepository,
    private val account: () -> Account,
) {
    private fun entity(kind: DirectoryHit.Kind) = if (kind == DirectoryHit.Kind.SOUNDSYSTEM) "soundsystem" else "artist"

    private fun signedIn(): SupabaseClient {
        if (!account().canContribute) throw TeknotesException("Connecte-toi d'abord depuis le profil.")
        return remote ?: throw TeknotesException("Le serveur n'est pas configuré.")
    }

    /** Versions of an entry, newest first. Authors the reader has blocked are left out by the server. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun history(kind: DirectoryHit.Kind, id: String): List<Revision> = guard {
        val client = remote ?: throw TeknotesException("Le serveur n'est pas configuré.")
        client.from("revisions")
            .select(Columns.raw("id,author,before,after,created_at,profiles(pseudo)")) {
                filter {
                    eq("entity", entity(kind))
                    eq("entity_id", id)
                }
                order("id", Order.DESCENDING)
                limit(50)
            }
            .decodeList<RevisionRow>()
            .map { it.toModel() }
    }

    /** Puts an entry back as it was in [revision]: a new edit, so nothing is lost. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun restore(kind: DirectoryHit.Kind, id: String, revision: Revision) = guard {
        signedIn()
        if (kind == DirectoryHit.Kind.SOUNDSYSTEM) {
            directory.updateSoundsystem(id, revision.name, revision.aliases, revision.department, revision.styles, revision.links)
        } else {
            val members = directory.soundsystemsOf(id).map { it.id }
            directory.updateArtist(id, revision.name, revision.aliases, revision.styles, revision.links, members)
        }
    }

    /** Shared nights of other people where this entry was heard, newest first. Empty without an account. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun sightings(kind: DirectoryHit.Kind, id: String): List<Sighting> = guard {
        if (!account().canContribute) return@guard emptyList()
        val client = remote ?: return@guard emptyList()
        val column = if (kind == DirectoryHit.Kind.SOUNDSYSTEM) "soundsystem_id" else "artist_id"
        val me = account().userId()
        client.from("night_entries")
            .select(Columns.raw("nights!inner(date,department,owner,profiles(pseudo))")) {
                filter { eq(column, id) }
                limit(200)
            }
            .decodeList<SightingRow>()
            .map { it.nights }
            .filter { it.owner != me }
            .distinctBy { it.owner to it.date }
            .sortedByDescending { it.date }
            .take(30)
            .map { Sighting(it.date, it.department, it.owner, it.profiles?.pseudo.orEmpty()) }
    }

    /** Open merge proposals about this entry. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun mergeProposals(kind: DirectoryHit.Kind, id: String): List<MergeProposal> = guard {
        if (!account().canContribute) return@guard emptyList()
        val client = remote ?: return@guard emptyList()
        val me = account().userId()
        client.from("merge_proposals")
            .select(Columns.raw("id,kept_id,absorbed_id,merge_votes(voter)")) {
                filter {
                    eq("entity", entity(kind))
                    exact("applied_at", null)
                    or {
                        eq("kept_id", id)
                        eq("absorbed_id", id)
                    }
                }
            }
            .decodeList<MergeProposalRow>()
            .map { row ->
                val keeps = row.keptId == id
                val otherId = if (keeps) row.absorbedId else row.keptId
                val otherName = if (kind == DirectoryHit.Kind.SOUNDSYSTEM) directory.soundsystem(otherId)?.name else directory.artist(otherId)?.name
                MergeProposal(
                    id = row.id,
                    otherId = otherId,
                    otherName = otherName ?: "Fiche inconnue",
                    keepsThisEntry = keeps,
                    approvals = row.votes.size,
                    approvedByMe = row.votes.any { it.voter == me },
                )
            }
    }

    /** Proposes that [absorbedId] be merged into [keptId]. Applied once three people agree. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun proposeMerge(kind: DirectoryHit.Kind, keptId: String, absorbedId: String) = guard {
        signedIn().postgrest.rpc(
            "propose_merge",
            buildJsonObject {
                put("p_entity", entity(kind))
                put("p_kept", keptId)
                put("p_absorbed", absorbedId)
            },
        )
        Unit
    }

    /** Approves a proposal. Returns true if this approval applied the merge. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun approveMerge(proposalId: Long): Boolean = guard {
        signedIn().postgrest.rpc("approve_merge", buildJsonObject { put("p_proposal", proposalId) })
            .decodeAs<MergeResult>()
            .applied
    }

    /** Reports an entry. Three reports from different people hide it until it is checked. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun report(kind: DirectoryHit.Kind, id: String, reason: ReportReason) = guard {
        try {
            signedIn().from("reports").insert(ReportRow(entity(kind), id, reason.label))
        } catch (error: PostgrestRestException) {
            if (error.code == "23505") throw TeknotesException("Tu as déjà signalé cette fiche.")
            throw error
        }
        Unit
    }

    /** Hides a person from history and shared nights, for the current user only. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun block(userId: String) = guard {
        val client = signedIn()
        val me = account().userId() ?: throw TeknotesException("Connecte-toi d'abord depuis le profil.")
        client.from("blocks").upsert(BlockRow(me, userId)) { ignoreDuplicates = true }
        Unit
    }

    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun unblock(userId: String) = guard {
        val client = signedIn()
        val me = account().userId() ?: throw TeknotesException("Connecte-toi d'abord depuis le profil.")
        client.from("blocks").delete {
            filter {
                eq("blocker", me)
                eq("blocked", userId)
            }
        }
        Unit
    }

    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun blockedUsers(): List<BlockedUser> = guard {
        val client = signedIn()
        val me = account().userId() ?: return@guard emptyList()
        client.from("blocks")
            .select(Columns.raw("blocked,profiles!blocks_blocked_fkey(pseudo)")) { filter { eq("blocker", me) } }
            .decodeList<BlockedRow>()
            .map { BlockedUser(it.blocked, it.profiles?.pseudo.orEmpty()) }
    }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: TeknotesException) {
        throw error
    } catch (error: Exception) {
        throw TeknotesException(friendlyMessage(error), error)
    }
}

@Serializable
private data class PseudoRow(val pseudo: String)

@Serializable
private data class RevisionRow(
    val id: Long,
    val author: String?,
    val before: JsonObject?,
    val after: JsonObject,
    @SerialName("created_at") val createdAt: String,
    val profiles: PseudoRow?,
) {
    fun toModel(): Revision {
        val changes = if (before == null) emptyList() else REVISION_FIELDS.filter { (key, _) -> !sameValue(before[key], after[key]) }.map { it.second }
        return Revision(
            id = id,
            authorId = author,
            authorPseudo = profiles?.pseudo,
            date = createdAt.take(10),
            changes = changes,
            name = after["name"]?.jsonPrimitive?.content.orEmpty(),
            aliases = after["aliases"].strings(),
            department = after["department"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
            styles = after["styles"].strings().map(Style::fromId),
            links = after["links"].strings(),
        )
    }
}

private val REVISION_FIELDS = listOf(
    "name" to "Nom",
    "aliases" to "Aussi appelé",
    "department" to "Origine",
    "styles" to "Styles",
    "links" to "Liens",
)

/** Lists are compared without their order: reordering styles isn't a change worth showing. */
private fun sameValue(a: JsonElement?, b: JsonElement?): Boolean =
    if (a is JsonArray && b is JsonArray) a.toSet() == b.toSet() else a == b

private fun JsonElement?.strings(): List<String> =
    (this as? JsonArray)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

@Serializable
private data class SightingNight(val date: String, val department: String?, val owner: String, val profiles: PseudoRow?)

@Serializable
private data class SightingRow(val nights: SightingNight)

@Serializable
private data class VoteRow(val voter: String)

@Serializable
private data class MergeProposalRow(
    val id: Long,
    @SerialName("kept_id") val keptId: String,
    @SerialName("absorbed_id") val absorbedId: String,
    @SerialName("merge_votes") val votes: List<VoteRow>,
)

@Serializable
private data class MergeResult(val applied: Boolean)

@Serializable
private data class ReportRow(val entity: String, @SerialName("entity_id") val entityId: String, val reason: String)

@Serializable
private data class BlockRow(val blocker: String, val blocked: String)

@Serializable
private data class BlockedRow(val blocked: String, val profiles: PseudoRow?)
