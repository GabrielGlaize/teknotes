package dev.gabrielglaize.teknotes

/**
 * Where the shared directory lives. The key is the public (anon) key: it only
 * grants what the access rules allow, so it can ship in the app.
 */
data class SupabaseConfig(val url: String, val key: String)

/** Error with a message ready to show to the user, in French. */
class TeknotesException(message: String, cause: Throwable? = null) : Exception(message, cause)
