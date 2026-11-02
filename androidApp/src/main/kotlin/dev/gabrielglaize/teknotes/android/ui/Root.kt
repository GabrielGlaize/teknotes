package dev.gabrielglaize.teknotes.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Teknotes

private enum class Tab(val label: String, val icon: ImageVector) {
    NOTEBOOK("Carnet", Icons.AutoMirrored.Filled.List),
    DIRECTORY("Annuaire", Icons.Filled.Search),
    PROFILE("Profil", Icons.Filled.Person),
}

/** Screens opened on top of the tabs. */
sealed interface Route {
    data class Night(val id: String) : Route
    data class SoundsystemDetail(val id: String) : Route
    data class ArtistDetail(val id: String) : Route

    /** Creation form; when opened from a night, what is created is added to it. */
    data class Create(val kind: DirectoryHit.Kind, val name: String, val nightId: String?) : Route
    data class Edit(val kind: DirectoryHit.Kind, val id: String) : Route
    data class History(val kind: DirectoryHit.Kind, val id: String) : Route
    data object Blocked : Route
    data class Legal(val privacy: Boolean) : Route
}

private fun Route.encode(): String = when (this) {
    is Route.Night -> "n|$id"
    is Route.SoundsystemDetail -> "s|$id"
    is Route.ArtistDetail -> "a|$id"
    is Route.Create -> "c|${kind.name}|${nightId.orEmpty()}|$name"
    is Route.Edit -> "e|${kind.name}|$id"
    is Route.History -> "h|${kind.name}|$id"
    Route.Blocked -> "b"
    is Route.Legal -> "l|$privacy"
}

private fun decode(text: String): Route {
    val parts = text.split("|", limit = 4)
    return when (parts[0]) {
        "n" -> Route.Night(parts[1])
        "s" -> Route.SoundsystemDetail(parts[1])
        "a" -> Route.ArtistDetail(parts[1])
        "e" -> Route.Edit(DirectoryHit.Kind.valueOf(parts[1]), parts[2])
        "h" -> Route.History(DirectoryHit.Kind.valueOf(parts[1]), parts[2])
        "b" -> Route.Blocked
        "l" -> Route.Legal(parts[1].toBoolean())
        else -> Route.Create(DirectoryHit.Kind.valueOf(parts[1]), parts[3], parts[2].ifEmpty { null })
    }
}

/** Navigation state: what is open, kept across rotation. */
class Navigator(val stack: MutableList<Route>) {
    fun open(route: Route) {
        stack += route
    }

    fun back() {
        stack.removeAt(stack.lastIndex)
    }

    /** Replaces the top screen (e.g. the creation form by the night it came from). */
    fun replace(route: Route?) {
        back()
        route?.let { open(it) }
    }
}

/**
 * Gives a screen its own view models, cleared when it closes. Without this they live as long
 * as the activity: a form or a history opened again would show old data.
 */
@Composable
private fun OwnViewModels(route: Route, content: @Composable () -> Unit) {
    key(route) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(Unit) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
    }
}

@Composable
fun TeknotesRoot(teknotes: Teknotes) {
    var tab by rememberSaveable { mutableStateOf(Tab.NOTEBOOK) }
    val stack = rememberSaveable(
        saver = listSaver(save = { list -> list.map { it.encode() } }, restore = { saved -> saved.map(::decode).toMutableStateList() }),
    ) { mutableListOf<Route>().toMutableStateList() }
    val navigator = Navigator(stack)

    stack.lastOrNull()?.let { route ->
        BackHandler { navigator.back() }
        OwnViewModels(route) {
            when (route) {
            is Route.Night -> NightScreen(teknotes, route.id, navigator)
            is Route.SoundsystemDetail -> FicheScreen(teknotes, DirectoryHit.Kind.SOUNDSYSTEM, route.id, navigator)
            is Route.ArtistDetail -> FicheScreen(teknotes, DirectoryHit.Kind.ARTIST, route.id, navigator)
            is Route.Create -> CreateScreen(teknotes, route, navigator)
            is Route.Edit -> EditScreen(teknotes, route, navigator)
            is Route.History -> HistoryScreen(teknotes, route, navigator)
            Route.Blocked -> BlockedScreen(teknotes, navigator)
            is Route.Legal -> LegalScreen(route.privacy, navigator)
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.NOTEBOOK -> NotebookScreen(teknotes, padding, navigator)
            Tab.DIRECTORY -> DirectoryScreen(teknotes, padding, navigator)
            Tab.PROFILE -> ProfileScreen(teknotes, padding, navigator)
        }
    }
}
