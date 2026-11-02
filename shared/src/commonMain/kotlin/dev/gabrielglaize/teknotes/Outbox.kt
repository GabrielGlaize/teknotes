package dev.gabrielglaize.teknotes

import dev.gabrielglaize.teknotes.db.TeknotesQueries
import kotlin.time.Instant

/** What the outbox can hold, in the order changes must reach the server (references first). */
internal enum class Entity(val key: String) {
    SOUNDSYSTEM("soundsystem"),
    ARTIST("artist"),
    MEMBERSHIP("membership"),
    NIGHT("night"),
    NIGHT_ENTRY("night_entry");

    companion object {
        fun of(key: String) = entries.first { it.key == key }
    }
}

internal enum class Operation(val key: String) { UPSERT("upsert"), DELETE("delete") }

/** Writes every local change down so the sync can send it later, whatever happens in between. */
internal class Outbox(private val queries: TeknotesQueries, private val clock: () -> Instant) {
    /** Called after each local change, so the sync can be scheduled. */
    var onChange: () -> Unit = {}

    fun record(entity: Entity, id: String, operation: Operation = Operation.UPSERT) {
        queries.enqueue(entity.key, id, operation.key, clock().toEpochMilliseconds())
    }

    fun changed() = onChange()

    fun isPending(entity: Entity, id: String) = queries.isPending(entity.key, id).executeAsOne() > 0
}

internal fun membershipKey(soundsystemId: String, artistId: String) = "$soundsystemId:$artistId"
