package dev.gabrielglaize.teknotes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Handle returned by the watchXxx functions, which let Swift follow a Flow with a
 * plain callback (called on the main thread). Call [cancel] when the screen goes away.
 */
class Watcher internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

internal fun <T> Flow<T>.watch(onEach: (T) -> Unit): Watcher {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    return Watcher(scope.launch { collect { onEach(it) } })
}

internal fun List<String>.joinLines(): String = joinToString("\n") { it.trim() }.trim()

internal fun String.splitLines(): List<String> = split("\n").map { it.trim() }.filter { it.isNotEmpty() }
