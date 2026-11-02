package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.MyDirectoryEntry
import dev.gabrielglaize.teknotes.Teknotes

@Composable
fun DirectoryScreen(teknotes: Teknotes, padding: PaddingValues, navigator: Navigator, onFirstNote: () -> Unit) {
    val model = viewModel { DirectoryViewModel(teknotes) }
    val state by model.state.collectAsStateWithLifecycle()
    var filtering by remember { mutableStateOf(false) }
    val newKind = if (state.filter == DirectoryFilter.ARTISTS) DirectoryHit.Kind.ARTIST else DirectoryHit.Kind.SOUNDSYSTEM
    val open = { kind: DirectoryHit.Kind, id: String, personal: Boolean ->
        navigator.open(if (kind == DirectoryHit.Kind.SOUNDSYSTEM) Route.SoundsystemDetail(id, personal) else Route.ArtistDetail(id, personal))
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = padding) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BigTitle("Annuaire", Modifier.weight(1f))
                IconButton(onClick = { navigator.open(Route.Create(newKind, state.query.trim(), nightId = null)) }) {
                    Icon(Icons.Filled.Add, contentDescription = if (newKind == DirectoryHit.Kind.SOUNDSYSTEM) "Ajouter un soundsystem" else "Ajouter un artiste")
                }
            }
            Segments(DirectoryScope.entries, state.scope, { it.label }, { model.show(it) })
            SearchLine("Chercher, même avec une faute", state.query, model::search, Modifier.padding(top = 10.dp))
            Row(Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DirectoryFilter.entries.forEach { filter -> Chip(filter.label, state.filter == filter) { model.show(filter) } }
                if (state.scope == DirectoryScope.PUBLIC) {
                    Chip(state.style?.let { "${it.label} ×" } ?: "Style ▾", state.style != null) { filtering = true }
                }
            }
        }

        if (state.scope == DirectoryScope.MINE) {
            if (!state.hasNotes) {
                item { TipCard("Ton classement se remplit avec tes notes. Commence par un soundsystem ou un artiste entendu.", onFirstNote) }
            } else if (state.ranking.isEmpty()) {
                item { Note("Rien de tel dans ton classement. Regarde dans Public.", Modifier.padding(top = 16.dp)) }
            }
            itemsIndexed(state.ranking, key = { _, entry -> entry.id }) { index, entry ->
                RankRow(index + 1, entry) { open(entry.kind, entry.id, true) }
            }
        } else if (state.query.isNotBlank()) {
            if (state.results.isEmpty()) item { Note("Pas de fiche à ce nom. Le + en haut permet de la créer.", Modifier.padding(top = 16.dp)) }
            items(state.results, key = { "hit-" + it.id }) { hit -> HitRow(hit) { open(hit.kind, hit.id, false) } }
        } else {
            items(state.soundsystems, key = { it.id }) { soundsystem ->
                PublicRow(
                    null,
                    soundsystem.name,
                    listOfNotNull(
                        state.links.artistsOfSoundsystem[soundsystem.id]?.let { "avec " + it.joinToString(", ") },
                        soundsystem.styles.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label },
                    ).joinToString(" · "),
                ) { open(DirectoryHit.Kind.SOUNDSYSTEM, soundsystem.id, false) }
            }
            items(state.artists, key = { it.id }) { artist ->
                PublicRow(
                    artist.name,
                    state.links.soundsystemsOfArtist[artist.id]?.joinToString(", "),
                    artist.styles.joinToString(", ") { it.label },
                ) { open(DirectoryHit.Kind.ARTIST, artist.id, false) }
            }
        }
    }

    if (filtering) {
        StylePicker(
            model.directory,
            setOfNotNull(state.style),
            { model.filter(it.firstOrNull()) },
            onClose = { filtering = false },
            filtering = true,
        )
    }
}

@Composable
private fun PublicRow(artist: String?, soundsystem: String?, details: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SetName(artist, soundsystem)
            if (details.isNotEmpty()) Text(details, style = textStyle(13.sp), color = Mute, maxLines = 2)
        }
        Hairline()
    }
}

/** A line of the ranking: the rank and the score are the design. */
@Composable
private fun RankRow(rank: Int, entry: MyDirectoryEntry, onClick: () -> Unit) {
    val times = if (entry.nights == 1) "1 nuit" else "${entry.nights} nuits"
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", Modifier.width(64.dp), style = textStyle(if (rank > 99) 28.sp else 42.sp, 800).copy(letterSpacing = (-2).sp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = textStyle(16.sp, 700), maxLines = 1)
                Text((if (entry.kind == DirectoryHit.Kind.ARTIST) "Artiste" else "Soundsystem") + " · $times", style = textStyle(12.sp), color = Mute)
            }
            ScoreText(entry.average)
        }
    }
}
