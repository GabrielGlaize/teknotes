package dev.gabrielglaize.teknotes.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Night
import dev.gabrielglaize.teknotes.NightEntry
import dev.gabrielglaize.teknotes.Soundsystem
import dev.gabrielglaize.teknotes.Teknotes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NotebookViewModel(private val teknotes: Teknotes) : ViewModel() {
    val nights: StateFlow<List<Night>> = teknotes.notebook.nights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "I'm here": returns the night to open. */
    fun checkIn(): String = teknotes.notebook.checkIn().id
}

data class NightState(
    val night: Night? = null,
    val entries: List<NightEntry> = emptyList(),
    val query: String = "",
    val results: List<DirectoryHit> = emptyList(),
)

class NightViewModel(private val teknotes: Teknotes, private val nightId: String) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<NightState> = combine(
        teknotes.notebook.nights().map { nights -> nights.firstOrNull { it.id == nightId } },
        teknotes.notebook.entries(nightId),
        query,
    ) { night, entries, query ->
        NightState(night, entries, query, if (query.isBlank()) emptyList() else teknotes.directory.search(query).take(8))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightState())

    fun search(text: String) {
        query.value = text
    }

    /** Second tap of "add in 2 taps". */
    fun add(hit: DirectoryHit) {
        teknotes.notebook.addEntry(nightId, hit)
        query.value = ""
    }

    /** Nothing matched: creates the soundsystem in the directory, then adds it. */
    fun createAndAdd(name: String) {
        val soundsystem = teknotes.directory.createSoundsystem(name)
        add(DirectoryHit(soundsystem.id, DirectoryHit.Kind.SOUNDSYSTEM, soundsystem.name, null, 100))
    }

    fun rate(entry: NightEntry, rating: Int) {
        teknotes.notebook.rateEntry(entry.id, if (entry.rating == rating) null else rating, entry.comment)
    }

    fun remove(entry: NightEntry) = teknotes.notebook.removeEntry(entry.id)

    fun setDepartment(code: String?) {
        val night = state.value.night ?: return
        teknotes.notebook.updateNight(night.id, code, night.title, night.comment, night.shared)
    }

    fun delete() = teknotes.notebook.deleteNight(nightId)
}

data class DirectoryState(
    val query: String = "",
    val soundsystems: List<Soundsystem> = emptyList(),
    val results: List<DirectoryHit> = emptyList(),
)

class DirectoryViewModel(private val teknotes: Teknotes) : ViewModel() {
    private val query = MutableStateFlow("")
    val currentQuery: StateFlow<String> = query.asStateFlow()

    val state: StateFlow<DirectoryState> = combine(teknotes.directory.soundsystems(), query) { soundsystems, query ->
        DirectoryState(query, soundsystems, if (query.isBlank()) emptyList() else teknotes.directory.search(query))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectoryState())

    fun search(text: String) {
        query.value = text
    }
}
