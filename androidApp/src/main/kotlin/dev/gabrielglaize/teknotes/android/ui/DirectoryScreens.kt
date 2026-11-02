package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Style
import dev.gabrielglaize.teknotes.Teknotes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(teknotes: Teknotes, padding: PaddingValues, navigator: Navigator) {
    val model = viewModel { DirectoryViewModel(teknotes) }
    val state by model.state.collectAsStateWithLifecycle()
    val kind = if (state.tab == DirectoryTab.SOUNDSYSTEMS) DirectoryHit.Kind.SOUNDSYSTEM else DirectoryHit.Kind.ARTIST
    val openHit = { hit: DirectoryHit ->
        navigator.open(if (hit.kind == DirectoryHit.Kind.SOUNDSYSTEM) Route.SoundsystemDetail(hit.id) else Route.ArtistDetail(hit.id))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            ScreenTitle("Annuaire", state.tab.label)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                DirectoryTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { model.show(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, DirectoryTab.entries.size),
                    ) { Text(tab.label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = model::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Chercher, même avec une faute") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            StyleChips(setOfNotNull(state.style), onToggle = model::filter)
            FilledTonalButton(
                onClick = { navigator.open(Route.Create(kind, state.query.trim(), nightId = null)) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(if (kind == DirectoryHit.Kind.SOUNDSYSTEM) "  Ajouter un soundsystem" else "  Ajouter un artiste")
            }
        }
        when {
            state.query.isNotBlank() -> items(state.results, key = { "hit-" + it.id }) { hit -> HitRow(hit, onClick = { openHit(hit) }) }
            state.tab == DirectoryTab.SOUNDSYSTEMS -> items(state.soundsystems, key = { it.id }) { soundsystem ->
                DirectoryRow(
                    label = Departments.byCode(soundsystem.department)?.label ?: "Origine inconnue",
                    name = soundsystem.name,
                    styles = soundsystem.styles,
                    onClick = { navigator.open(Route.SoundsystemDetail(soundsystem.id)) },
                )
            }
            else -> items(state.artists, key = { it.id }) { artist ->
                DirectoryRow(
                    label = "Artiste",
                    name = artist.name,
                    styles = artist.styles,
                    onClick = { navigator.open(Route.ArtistDetail(artist.id)) },
                )
            }
        }
    }
}

@Composable
private fun DirectoryRow(label: String, name: String, styles: List<Style>, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        overlineContent = { Text(label.uppercase(), style = flyerLabel()) },
        headlineContent = { Text(name) },
        supportingContent = { Text(styles.joinToString(" · ") { it.label }) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(navigator: Navigator, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding -> Column(Modifier.padding(padding)) { content() } }
}

@Composable
private fun Facts(vararg facts: Pair<String, String>) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        facts.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Column {
                Text(label.uppercase(), style = flyerLabel())
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun SoundsystemScreen(teknotes: Teknotes, id: String, navigator: Navigator) {
    val soundsystem = remember(id) { teknotes.directory.soundsystem(id) }
    if (soundsystem == null) {
        LaunchedEffect(id) { navigator.back() } // gone from the directory (hidden after reports)
        return
    }
    val members = remember(id) { teknotes.directory.membersOf(id) }
    DetailScaffold(navigator) {
        ScreenTitle("Soundsystem", soundsystem.name)
        Facts(
            "Origine" to (Departments.byCode(soundsystem.department)?.label ?: "Inconnue"),
            "Styles" to soundsystem.styles.joinToString(" · ") { it.label },
            "Aussi appelé" to soundsystem.aliases.joinToString(", "),
            "Liens" to soundsystem.links.joinToString("\n"),
        )
        if (members.isNotEmpty()) {
            SectionLabel("Membres")
            members.forEach { artist ->
                Row(Modifier.fillMaxWidth().clickable { navigator.open(Route.ArtistDetail(artist.id)) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(teknotes: Teknotes, id: String, navigator: Navigator) {
    val artist = remember(id) { teknotes.directory.artist(id) }
    if (artist == null) {
        LaunchedEffect(id) { navigator.back() } // gone from the directory (hidden after reports)
        return
    }
    val soundsystems = remember(id) { teknotes.directory.soundsystemsOf(id) }
    DetailScaffold(navigator) {
        ScreenTitle("Artiste", artist.name)
        Facts(
            "Styles" to artist.styles.joinToString(" · ") { it.label },
            "Aussi appelé" to artist.aliases.joinToString(", "),
            "Liens" to artist.links.joinToString("\n"),
        )
        if (soundsystems.isNotEmpty()) {
            SectionLabel("Soundsystems")
            soundsystems.forEach { soundsystem ->
                Row(Modifier.fillMaxWidth().clickable { navigator.open(Route.SoundsystemDetail(soundsystem.id)) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(soundsystem.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
