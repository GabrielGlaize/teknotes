package dev.gabrielglaize.teknotes.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Artist
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Night
import dev.gabrielglaize.teknotes.NightEntry
import dev.gabrielglaize.teknotes.Preferences
import dev.gabrielglaize.teknotes.Soundsystem
import dev.gabrielglaize.teknotes.Style
import dev.gabrielglaize.teknotes.SyncState
import dev.gabrielglaize.teknotes.Teknotes
import dev.gabrielglaize.teknotes.TeknotesException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(teknotes: Teknotes) : ViewModel() {
    val preferences: StateFlow<Preferences> = teknotes.settings.preferences()
        .stateIn(viewModelScope, SharingStarted.Eagerly, teknotes.settings.current())
}

class NotebookViewModel(private val teknotes: Teknotes) : ViewModel() {
    val nights: StateFlow<List<Night>> = teknotes.notebook.nights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "I'm here": returns the night to open. */
    fun checkIn(): String = teknotes.notebook.checkIn(null).id
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

    fun rate(entry: NightEntry, rating: Int) =
        teknotes.notebook.updateEntry(entry.id, if (entry.rating == rating) null else rating, entry.comment)

    fun comment(entry: NightEntry, comment: String) = teknotes.notebook.updateEntry(entry.id, entry.rating, comment)

    fun remove(entry: NightEntry) = teknotes.notebook.removeEntry(entry.id)

    fun edit(department: String? = state.value.night?.department, title: String? = null, comment: String? = null, shared: Boolean? = null) {
        val night = state.value.night ?: return
        teknotes.notebook.updateNight(night.id, department, title ?: night.title, comment ?: night.comment, shared ?: night.shared)
    }

    fun delete() = teknotes.notebook.deleteNight(nightId)
}

enum class DirectoryTab(val label: String) { SOUNDSYSTEMS("Soundsystems"), ARTISTS("Artistes") }

data class DirectoryState(
    val tab: DirectoryTab = DirectoryTab.SOUNDSYSTEMS,
    val query: String = "",
    val style: Style? = null,
    val soundsystems: List<Soundsystem> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val results: List<DirectoryHit> = emptyList(),
)

class DirectoryViewModel(private val teknotes: Teknotes) : ViewModel() {
    private val filters = MutableStateFlow(DirectoryState())

    val state: StateFlow<DirectoryState> = combine(
        teknotes.directory.soundsystems(),
        teknotes.directory.artists(),
        filters,
    ) { soundsystems, artists, filters ->
        val kind = if (filters.tab == DirectoryTab.SOUNDSYSTEMS) DirectoryHit.Kind.SOUNDSYSTEM else DirectoryHit.Kind.ARTIST
        filters.copy(
            soundsystems = soundsystems.filter { filters.style == null || filters.style in it.styles },
            artists = artists.filter { filters.style == null || filters.style in it.styles },
            results = if (filters.query.isBlank()) emptyList() else teknotes.directory.search(filters.query).filter { it.kind == kind },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectoryState())

    fun search(text: String) = filters.update { it.copy(query = text) }

    fun show(tab: DirectoryTab) = filters.update { it.copy(tab = tab) }

    fun filter(style: Style?) = filters.update { it.copy(style = if (it.style == style) null else style) }
}

/** Form to add a soundsystem or an artist, with lookalikes shown before creating a duplicate. */
data class CreateState(
    val kind: DirectoryHit.Kind,
    val name: String,
    val aliases: String = "",
    val department: String? = null,
    val styles: Set<Style> = emptySet(),
    val links: String = "",
    val soundsystemIds: Set<String> = emptySet(),
    val lookalikes: List<DirectoryHit> = emptyList(),
)

class CreateViewModel(private val teknotes: Teknotes, kind: DirectoryHit.Kind, name: String) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateState(kind, name, lookalikes = teknotes.directory.lookalikes(name)))
    val state: StateFlow<CreateState> = mutableState.asStateFlow()

    val soundsystems: StateFlow<List<Soundsystem>> = teknotes.directory.soundsystems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(change: (CreateState) -> CreateState) = mutableState.update { current ->
        val next = change(current)
        if (next.name != current.name) next.copy(lookalikes = teknotes.directory.lookalikes(next.name)) else next
    }

    /** Saves and returns what was created, as a hit the notebook can add. */
    fun save(): DirectoryHit {
        val form = state.value
        val aliases = form.aliases.split(",")
        val links = form.links.split("\n")
        val styles = Style.entries.filter { it in form.styles }
        return if (form.kind == DirectoryHit.Kind.SOUNDSYSTEM) {
            val created = teknotes.directory.createSoundsystem(form.name, aliases, form.department, styles, links)
            DirectoryHit(created.id, DirectoryHit.Kind.SOUNDSYSTEM, created.name, null, 100)
        } else {
            val created = teknotes.directory.createArtist(form.name, aliases, styles, links, form.soundsystemIds.toList())
            DirectoryHit(created.id, DirectoryHit.Kind.ARTIST, created.name, null, 100)
        }
    }
}

data class AccountForm(
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val codeSent: Boolean = false,
    val pseudo: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

class AccountViewModel(private val teknotes: Teknotes) : ViewModel() {
    val account: StateFlow<AccountState> = teknotes.account.state
    val sync: StateFlow<SyncState> = teknotes.sync.state
    private val mutableForm = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = mutableForm.asStateFlow()

    fun edit(change: (AccountForm) -> AccountForm) = mutableForm.update { change(it).copy(message = null) }

    private fun run(after: (AccountForm) -> AccountForm = { it }, action: suspend () -> Unit) {
        mutableForm.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                action()
                mutableForm.update { after(it).copy(busy = false) }
            } catch (error: TeknotesException) {
                mutableForm.update { it.copy(busy = false, message = error.message) }
            }
        }
    }

    fun signIn() = form.value.let { run(after = { it.copy(password = "") }) { teknotes.account.signIn(it.email, it.password) } }

    fun signUp() = form.value.let { run(after = { it.copy(password = "") }) { teknotes.account.signUp(it.email, it.password) } }

    fun sendCode() = run(after = { it.copy(codeSent = true) }) { teknotes.account.sendCode(form.value.email) }

    fun verifyCode() = form.value.let { run(after = { it.copy(code = "", codeSent = false) }) { teknotes.account.verifyCode(it.email, it.code) } }

    fun choosePseudo() = run { teknotes.account.choosePseudo(form.value.pseudo) }

    fun syncNow() = run { teknotes.sync.syncNow() }

    fun signOut() = run { teknotes.account.signOut() }

    fun deleteAccount() = run { teknotes.account.deleteAccount() }

    fun setAppearance(appearance: dev.gabrielglaize.teknotes.Appearance) = teknotes.settings.setAppearance(appearance)

    fun setFieldMode(enabled: Boolean) = teknotes.settings.setFieldMode(enabled)

    fun exportJson() = teknotes.notebook.exportJson()

    fun exportCsv() = teknotes.notebook.exportCsv()
}
