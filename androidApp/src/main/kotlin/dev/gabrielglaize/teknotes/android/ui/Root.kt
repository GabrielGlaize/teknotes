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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.vector.ImageVector
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
}

private fun Route.encode(): String = when (this) {
    is Route.Night -> "n|$id"
    is Route.SoundsystemDetail -> "s|$id"
    is Route.ArtistDetail -> "a|$id"
    is Route.Create -> "c|${kind.name}|${nightId.orEmpty()}|$name"
}

private fun decode(text: String): Route {
    val parts = text.split("|", limit = 4)
    return when (parts[0]) {
        "n" -> Route.Night(parts[1])
        "s" -> Route.SoundsystemDetail(parts[1])
        "a" -> Route.ArtistDetail(parts[1])
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

@Composable
fun TeknotesRoot(teknotes: Teknotes) {
    var tab by rememberSaveable { mutableStateOf(Tab.NOTEBOOK) }
    val stack = rememberSaveable(
        saver = listSaver(save = { list -> list.map { it.encode() } }, restore = { saved -> saved.map(::decode).toMutableStateList() }),
    ) { mutableListOf<Route>().toMutableStateList() }
    val navigator = Navigator(stack)

    stack.lastOrNull()?.let { route ->
        BackHandler { navigator.back() }
        when (route) {
            is Route.Night -> NightScreen(teknotes, route.id, navigator)
            is Route.SoundsystemDetail -> SoundsystemScreen(teknotes, route.id, navigator)
            is Route.ArtistDetail -> ArtistScreen(teknotes, route.id, navigator)
            is Route.Create -> CreateScreen(teknotes, route, navigator)
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
            Tab.PROFILE -> ProfileScreen(teknotes, padding)
        }
    }
}
