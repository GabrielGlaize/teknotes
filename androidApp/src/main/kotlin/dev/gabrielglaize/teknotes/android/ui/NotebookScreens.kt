package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Night
import dev.gabrielglaize.teknotes.NightEntry
import dev.gabrielglaize.teknotes.SetChoice
import dev.gabrielglaize.teknotes.Teknotes

@Composable
fun NotebookScreen(teknotes: Teknotes, padding: PaddingValues, navigator: Navigator, onFirstNote: () -> Unit) {
    val model = viewModel { NotebookViewModel(teknotes) }
    val nights by model.nights.collectAsStateWithLifecycle()
    val months = nights.groupBy { monthTitle(it.date) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = padding) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BigTitle("Tek'Notes !", Modifier.weight(1f))
                Chip("J'y suis") { navigator.open(Route.Night(model.checkIn())) }
            }
        }
        if (nights.isEmpty()) {
            item { TipCard("Tu viens d'entendre un set ? Note ton premier soundsystem ou artiste : tape son nom, touche la bonne ligne.", onFirstNote) }
        }
        months.forEach { (month, list) ->
            item(key = month) { Text(month, Modifier.padding(top = 20.dp, bottom = 6.dp), style = textStyle(13.sp, 600), color = Mute) }
            items(list, key = { it.id }) { night -> NightRow(night, onClick = { navigator.open(Route.Night(night.id)) }) }
        }
    }
}

@Composable
private fun NightRow(night: Night, onClick: () -> Unit) {
    val details = listOfNotNull(
        weekdayName(night.date),
        if (night.entryCount == 1) "1 set" else "${night.entryCount} sets",
        "partagée".takeIf { night.shared },
    ).joinToString(" · ")
    val heard = night.sets.take(3).joinToString(", ") + if (night.sets.size > 3) "…" else ""
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(dayNumber(night.date), Modifier.width(62.dp), style = textStyle(46.sp, 800).copy(letterSpacing = (-2).sp, lineHeight = 48.sp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (night.title.isNotEmpty()) Text(night.title, style = textStyle(16.sp, 700))
                if (night.sets.isEmpty()) {
                    Text("Rien de noté", style = textStyle(14.sp), color = Mute)
                } else {
                    Text(heard, style = textStyle(14.sp, if (night.title.isEmpty()) 600 else 400), color = if (night.title.isEmpty()) Ink else Mute, maxLines = 3)
                }
                Text(details, style = textStyle(12.sp), color = Mute)
            }
            ScoreText(night.average, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun NightScreen(teknotes: Teknotes, nightId: String, navigator: Navigator) {
    val model = viewModel(key = nightId) { NightViewModel(teknotes, nightId) }
    val state by model.state.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    var pickDepartment by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf<Pair<NightEntry, DirectoryHit.Kind>?>(null) }
    val night = state.night ?: return

    BackScaffold(
        navigator,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Supprimer cette nuit") }, onClick = {
                        menu = false
                        confirmDelete = true
                    })
                }
            }
        },
    ) {
        Text(nightTitle(night.date), style = textStyle(30.sp, 800).copy(letterSpacing = (-0.5).sp))
        var title by remember(night.id) { mutableStateOf(night.title) }
        PlainField(title, "Donner un titre", 17, Modifier.padding(vertical = 10.dp)) {
            title = it
            model.edit(title = it)
        }

        SectionHead("Sets") {
            if (state.entries.isNotEmpty()) Text("${state.entries.size}", style = textStyle(15.sp), color = Mute)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchLine("Artiste ou soundsystem", state.query, model::search, Modifier.weight(1f))
            DictationButton(onText = model::search)
        }
        val query = state.query.trim()
        if (query.isNotEmpty()) {
            state.choices.forEach { choice -> ChoiceRow(choice) { model.add(choice) } }
            RowLine("Créer l'artiste « $query »", color = Accent) {
                navigator.open(Route.Create(DirectoryHit.Kind.ARTIST, query, nightId))
                model.search("")
            }
            RowLine("Créer le soundsystem « $query »", color = Accent) {
                navigator.open(Route.Create(DirectoryHit.Kind.SOUNDSYSTEM, query, nightId))
                model.search("")
            }
            Spacer(Modifier.height(12.dp))
        }
        state.entries.forEach { entry ->
            androidx.compose.runtime.key(entry.id) {
                EntryRow(entry, model) { kind -> changing = entry to kind }
            }
        }
        if (state.entries.isEmpty() && query.isEmpty()) {
            Note("Tape un artiste ou un soundsystem, puis touche la bonne ligne.", Modifier.padding(top = 12.dp))
        }

        SectionHead("Souvenirs")
        var memories by remember(night.id) { mutableStateOf(night.comment) }
        Row(verticalAlignment = Alignment.Top) {
            PlainField(memories, "Ce qu'il faut retenir de cette nuit", 16, Modifier.weight(1f).padding(top = 10.dp), singleLine = false) {
                memories = it
                model.edit(comment = it)
            }
            DictationButton(onText = { spoken ->
                memories = listOf(memories, spoken).filter { it.isNotBlank() }.joinToString(" ")
                model.edit(comment = memories)
            })
        }

        SectionHead("Détails")
        RowLine("Département", Departments.byCode(night.department)?.name ?: "Non précisé", chevron = true) { pickDepartment = true }
        ShareRow(night, account, model, navigator)
    }

    if (pickDepartment) {
        DepartmentPicker("Département", onPick = { model.edit(department = it) }, onClose = { pickDepartment = false })
    }

    changing?.let { (entry, kind) ->
        val isSoundsystem = kind == DirectoryHit.Kind.SOUNDSYSTEM
        SearchPicker(
            title = if (isSoundsystem) "Sur quel soundsystem ?" else "Quel artiste ?",
            prompt = if (isSoundsystem) "Chercher un soundsystem" else "Chercher un artiste",
            items = { teknotes.directory.browse(kind, it) },
            label = { it.name },
            onPick = { model.change(entry, it) },
            onClose = { changing = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette nuit ?") },
            text = { Text("Elle disparaît de ton carnet, avec tout ce que tu y as noté.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.delete()
                    navigator.back()
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Text without a frame, for what lives quietly in the page. */
@Composable
private fun PlainField(value: String, prompt: String, size: Int, modifier: Modifier = Modifier, singleLine: Boolean = true, onChange: (String) -> Unit) {
    Box(modifier) {
        if (value.isEmpty()) Text(prompt, style = textStyle(size.sp), color = Mute)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = prompt },
            textStyle = textStyle(size.sp).copy(color = Ink),
            cursorBrush = SolidColor(Accent),
            singleLine = singleLine,
        )
    }
}

@Composable
private fun ChoiceRow(choice: SetChoice, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            SetName(choice.artistName, choice.soundsystemName, size = 16, modifier = Modifier.weight(1f))
            if (choice.artistName == null || choice.soundsystemName == null) {
                Text(if (choice.artistName == null) "soundsystem seul" else "artiste seul", style = textStyle(13.sp), color = Mute)
            }
        }
        Hairline()
    }
}

@Composable
private fun EntryRow(entry: NightEntry, model: NightViewModel, onChange: (DirectoryHit.Kind) -> Unit) {
    var comment by remember(entry.id) { mutableStateOf(entry.comment) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SetName(entry.artistName, entry.soundsystemName, entry.label, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions sur ce set", tint = Mute) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (entry.artistId == null) "Préciser l'artiste" else "Changer l'artiste") }, onClick = {
                        menu = false
                        onChange(DirectoryHit.Kind.ARTIST)
                    })
                    DropdownMenuItem(text = { Text(if (entry.soundsystemId == null) "Préciser le soundsystem" else "Changer le soundsystem") }, onClick = {
                        menu = false
                        onChange(DirectoryHit.Kind.SOUNDSYSTEM)
                    })
                    DropdownMenuItem(text = { Text("Retirer") }, onClick = {
                        menu = false
                        model.remove(entry)
                    })
                }
            }
        }
        if (entry.soundsystemId == null || entry.artistId == null) {
            TextAction(if (entry.soundsystemId == null) "Sur quel soundsystem ?" else "Quel artiste ?") {
                onChange(if (entry.soundsystemId == null) DirectoryHit.Kind.SOUNDSYSTEM else DirectoryHit.Kind.ARTIST)
            }
        }
        NumberRate(entry.rating ?: 0) { model.rate(entry, it) }
        Row(verticalAlignment = Alignment.Top) {
            PlainField(comment, "Un mot sur ce set", 15, Modifier.weight(1f).padding(top = 12.dp), singleLine = false) {
                comment = it
                model.comment(entry, it)
            }
            DictationButton(onText = { spoken ->
                comment = listOf(comment, spoken).filter { it.isNotBlank() }.joinToString(" ")
                model.comment(entry, comment)
            })
        }
        Hairline(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ShareRow(night: Night, account: AccountState, model: NightViewModel, navigator: Navigator) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Partager cette nuit", style = textStyle(16.sp, 500))
            when (account) {
                is AccountState.SignedIn -> Note("Visible à partir du lendemain, avec ton pseudo et le département seulement.")
                AccountState.Unavailable -> Note("Le partage arrivera avec le serveur de l'annuaire.")
                else -> {
                    Note("Il faut un compte pour partager une nuit.")
                    TextAction("Créer un compte ou se connecter") { navigator.open(Route.Auth(signUp = true)) }
                }
            }
        }
        if (account is AccountState.SignedIn) {
            Switch(
                checked = night.shared,
                onCheckedChange = { model.edit(shared = it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}
