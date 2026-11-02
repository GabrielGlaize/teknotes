package dev.gabrielglaize.teknotes

/** Read-only view of internals, for tests. */
internal object TeknotesDatabaseAccess {
    fun pending(app: Teknotes): List<String> = app.pendingChanges()
}
