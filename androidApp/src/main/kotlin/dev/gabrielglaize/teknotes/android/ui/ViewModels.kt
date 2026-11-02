package dev.gabrielglaize.teknotes.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Artist
import dev.gabrielglaize.teknotes.BlockedUser
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.DirectoryLinks
import dev.gabrielglaize.teknotes.MyDirectoryEntry
import dev.gabrielglaize.teknotes.MyNote
import dev.gabrielglaize.teknotes.SetChoice
import dev.gabrielglaize.teknotes.Night
import dev.gabrielglaize.teknotes.NightEntry
import dev.gabrielglaize.teknotes.MergeProposal
import dev.gabrielglaize.teknotes.Preferences
import dev.gabrielglaize.teknotes.ReportReason
import dev.gabrielglaize.teknotes.Revision
import dev.gabrielglaize.teknotes.Sighting
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
    val choices: List<SetChoice> = emptyList(),
)

class NightViewModel(private val teknotes: Teknotes, private val nightId: String) : ViewModel() {
    private val query = MutableStateFlow("")

    val account: StateFlow<AccountState> = teknotes.account.state

    val state: StateFlow<NightState> = combine(
        teknotes.notebook.nights().map { nights -> nights.firstOrNull { it.id == nightId } },
        teknotes.notebook.entries(nightId),
        query,
    ) { night, entries, query ->
        NightState(night, entries, query, if (query.isBlank()) emptyList() else teknotes.directory.setChoices(query).take(10))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightState())

    fun search(text: String) {
        query.value = text
    }

    /** Second tap of "add in 2 taps". */
    fun add(choice: SetChoice) {
        teknotes.notebook.addSet(nightId, choice)
        query.value = ""
    }

    fun change(entry: NightEntry, hit: DirectoryHit) = teknotes.notebook.changeSet(entry.id, hit)

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

enum class DirectoryScope(val label: String) { MINE("Mon classement"), PUBLIC("Public") }

enum class DirectoryFilter(val label: String, val kind: DirectoryHit.Kind?) {
    ALL("Tout", null),
    ARTISTS("Artistes", DirectoryHit.Kind.ARTIST),
    SOUNDSYSTEMS("Soundsystems", DirectoryHit.Kind.SOUNDSYSTEM),
}

data class DirectoryState(
    val scope: DirectoryScope = DirectoryScope.MINE,
    val filter: DirectoryFilter = DirectoryFilter.ALL,
    val query: String = "",
    val style: Style? = null,
    val soundsystems: List<Soundsystem> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val hasNotes: Boolean = false,
    val ranking: List<MyDirectoryEntry> = emptyList(),
    val links: DirectoryLinks = DirectoryLinks(emptyMap(), emptyMap()),
    val results: List<DirectoryHit> = emptyList(),
)

class DirectoryViewModel(private val teknotes: Teknotes) : ViewModel() {
    private val filters = MutableStateFlow(DirectoryState())

    val directory = teknotes.directory

    val state: StateFlow<DirectoryState> = combine(
        teknotes.directory.soundsystems(),
        teknotes.directory.artists(),
        teknotes.notebook.myDirectory(),
        filters,
    ) { soundsystems, artists, mine, filters ->
        val kind = filters.filter.kind
        filters.copy(
            soundsystems = if (kind == DirectoryHit.Kind.ARTIST) emptyList() else soundsystems.filter { filters.style == null || filters.style in it.styles },
            artists = if (kind == DirectoryHit.Kind.SOUNDSYSTEM) emptyList() else artists.filter { filters.style == null || filters.style in it.styles },
            hasNotes = mine.isNotEmpty(),
            ranking = teknotes.notebook.ranked(mine, kind, filters.query),
            links = teknotes.directory.links(),
            results = if (filters.query.isBlank()) emptyList() else teknotes.directory.search(filters.query).filter { kind == null || it.kind == kind },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectoryState())

    fun search(text: String) = filters.update { it.copy(query = text) }

    fun show(scope: DirectoryScope) = filters.update { it.copy(scope = scope) }

    fun show(filter: DirectoryFilter) = filters.update { it.copy(filter = filter) }

    fun filter(style: Style?) = filters.update { it.copy(style = style) }
}

/** Form to add or edit a soundsystem or an artist, with lookalikes shown before creating a duplicate. */
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

class CreateViewModel(
    private val teknotes: Teknotes,
    kind: DirectoryHit.Kind,
    name: String,
    private val editedId: String? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        if (editedId == null) CreateState(kind, name, lookalikes = teknotes.directory.lookalikes(name)) else existing(kind, editedId),
    )
    val state: StateFlow<CreateState> = mutableState.asStateFlow()

    val directory = teknotes.directory

    private fun existing(kind: DirectoryHit.Kind, id: String): CreateState = if (kind == DirectoryHit.Kind.SOUNDSYSTEM) {
        val entry = teknotes.directory.soundsystem(id)
        CreateState(kind, entry?.name.orEmpty(), entry?.aliases.orEmpty().joinToString(", "), entry?.department, entry?.styles.orEmpty().toSet(), entry?.links.orEmpty().joinToString("\n"))
    } else {
        val entry = teknotes.directory.artist(id)
        CreateState(
            kind, entry?.name.orEmpty(), entry?.aliases.orEmpty().joinToString(", "), null, entry?.styles.orEmpty().toSet(), entry?.links.orEmpty().joinToString("\n"),
            teknotes.directory.soundsystemsOf(id).map { it.id }.toSet(),
        )
    }

    fun update(change: (CreateState) -> CreateState) = mutableState.update { current ->
        val next = change(current)
        // While editing, the entry itself isn't a duplicate to warn about.
        if (editedId == null && next.name != current.name) next.copy(lookalikes = teknotes.directory.lookalikes(next.name)) else next
    }

    /** Saves and returns the entry, as a hit the notebook can add. */
    fun save(): DirectoryHit {
        val form = state.value
        val aliases = form.aliases.split(",")
        val links = form.links.split("\n")
        val styles = form.styles.sortedBy { it.label }
        if (editedId != null) {
            if (form.kind == DirectoryHit.Kind.SOUNDSYSTEM) {
                teknotes.directory.updateSoundsystem(editedId, form.name, aliases, form.department, styles, links)
            } else {
                teknotes.directory.updateArtist(editedId, form.name, aliases, styles, links, form.soundsystemIds.toList())
            }
            return DirectoryHit(editedId, form.kind, form.name.trim(), null, 100)
        }
        return if (form.kind == DirectoryHit.Kind.SOUNDSYSTEM) {
            val created = teknotes.directory.createSoundsystem(form.name, aliases, form.department, styles, links)
            DirectoryHit(created.id, DirectoryHit.Kind.SOUNDSYSTEM, created.name, null, 100)
        } else {
            val created = teknotes.directory.createArtist(form.name, aliases, styles, links, form.soundsystemIds.toList())
            DirectoryHit(created.id, DirectoryHit.Kind.ARTIST, created.name, null, 100)
        }
    }
}

/** What a directory entry shows: its content, and what the community says about it. */
data class Fiche(
    val name: String,
    val department: String?,
    val styles: List<Style>,
    val aliases: List<String>,
    val links: List<String>,
    /** Members of a soundsystem, or soundsystems of an artist: id and name. */
    val related: List<Pair<String, String>>,
)

data class FicheCommunity(
    val sightings: List<Sighting> = emptyList(),
    val proposals: List<MergeProposal> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class FicheViewModel(private val teknotes: Teknotes, val kind: DirectoryHit.Kind, val id: String) : ViewModel() {
    val fiche: StateFlow<Fiche?> = combine(teknotes.directory.soundsystems(), teknotes.directory.artists()) { _, _ -> read() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), read())

    val account: StateFlow<AccountState> = teknotes.account.state

    val notes: StateFlow<List<MyNote>> = teknotes.notebook.notesOn(kind, id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Its place in the user's ranking, from 1, or null. */
    val rank: StateFlow<Int?> = teknotes.notebook.myDirectory()
        .map { items -> teknotes.notebook.ranked(items, null, "").indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableCommunity = MutableStateFlow(FicheCommunity())
    val community: StateFlow<FicheCommunity> = mutableCommunity.asStateFlow()

    private val mergeQuery = MutableStateFlow("")
    val mergeSearch: StateFlow<String> = mergeQuery.asStateFlow()
    val mergeResults: StateFlow<List<DirectoryHit>> = mergeQuery.map { query ->
        if (query.isBlank()) emptyList() else teknotes.directory.search(query).filter { it.kind == kind && it.id != id }.take(6)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    private fun read(): Fiche? = if (kind == DirectoryHit.Kind.SOUNDSYSTEM) {
        teknotes.directory.soundsystem(id)?.let { entry ->
            Fiche(entry.name, entry.department, entry.styles, entry.aliases, entry.links, teknotes.directory.membersOf(id).map { it.id to it.name })
        }
    } else {
        teknotes.directory.artist(id)?.let { entry ->
            Fiche(entry.name, null, entry.styles, entry.aliases, entry.links, teknotes.directory.soundsystemsOf(id).map { it.id to it.name })
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val sightings = teknotes.community.sightings(kind, id)
                val proposals = teknotes.community.mergeProposals(kind, id)
                mutableCommunity.update { it.copy(sightings = sightings, proposals = proposals) }
            } catch (_: TeknotesException) {
                // Offline: the entry still shows, without what needs the server.
            }
        }
    }

    private fun run(done: String? = null, action: suspend () -> Unit) {
        mutableCommunity.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                action()
                mutableCommunity.update { it.copy(busy = false, message = done) }
                refresh()
            } catch (error: TeknotesException) {
                mutableCommunity.update { it.copy(busy = false, message = error.message) }
            }
        }
    }

    fun searchMerge(text: String) {
        mergeQuery.value = text
    }

    fun proposeMerge(absorbed: DirectoryHit) = run("Fusion proposée. Il faut deux autres approbations.") {
        // What's created offline must reach the server before it can be merged.
        teknotes.sync.syncNow()
        teknotes.community.proposeMerge(kind, id, absorbed.id)
        mergeQuery.value = ""
    }

    fun approve(proposal: MergeProposal) = run {
        if (teknotes.community.approveMerge(proposal.id)) teknotes.sync.syncNow()
    }

    fun report(reason: ReportReason) = run("Merci, c'est noté.") {
        teknotes.community.report(kind, id, reason)
        teknotes.sync.schedule()
    }

    fun block(sighting: Sighting) = run("${sighting.pseudo} est bloqué.") { teknotes.community.block(sighting.userId) }
}

data class HistoryState(
    val revisions: List<Revision> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

class HistoryViewModel(private val teknotes: Teknotes, private val kind: DirectoryHit.Kind, private val id: String) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = mutableState.asStateFlow()
    val account: StateFlow<AccountState> = teknotes.account.state

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                // A local edit not sent yet must be part of the history shown.
                if (teknotes.account.state.value is AccountState.SignedIn) teknotes.sync.syncNow()
                mutableState.update { it.copy(revisions = teknotes.community.history(kind, id), loading = false) }
            } catch (error: TeknotesException) {
                mutableState.update { it.copy(loading = false, message = error.message) }
            }
        }
    }

    fun restore(revision: Revision) {
        viewModelScope.launch {
            try {
                teknotes.community.restore(kind, id, revision)
                mutableState.update { it.copy(message = "Version rétablie.") }
                load()
            } catch (error: TeknotesException) {
                mutableState.update { it.copy(message = error.message) }
            }
        }
    }

    fun block(authorId: String) {
        viewModelScope.launch {
            try {
                teknotes.community.block(authorId)
                load()
            } catch (error: TeknotesException) {
                mutableState.update { it.copy(message = error.message) }
            }
        }
    }
}

class BlockedViewModel(private val teknotes: Teknotes) : ViewModel() {
    private val mutableUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    val users: StateFlow<List<BlockedUser>> = mutableUsers.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                mutableUsers.value = teknotes.community.blockedUsers()
            } catch (error: TeknotesException) {
                mutableMessage.value = error.message
            }
        }
    }

    fun unblock(user: BlockedUser) {
        viewModelScope.launch {
            try {
                teknotes.community.unblock(user.id)
                load()
            } catch (error: TeknotesException) {
                mutableMessage.value = error.message
            }
        }
    }
}

data class AccountForm(
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val codeSent: Boolean = false,
    val pseudo: String = "",
    val termsAccepted: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class AccountViewModel(private val teknotes: Teknotes) : ViewModel() {
    val account: StateFlow<AccountState> = teknotes.account.state
    val sync: StateFlow<SyncState> = teknotes.sync.state
    private val mutableForm = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = mutableForm.asStateFlow()

    fun clearMessage() = mutableForm.update { it.copy(message = null) }

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

    fun exportJson() = teknotes.notebook.exportJson()

    fun exportCsv() = teknotes.notebook.exportCsv()
}
