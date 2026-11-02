package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Teknotes

/**
 * Adds a soundsystem or an artist to the directory. Entries that look alike are
 * shown first: picking one avoids a duplicate. Opened from a night, the result is
 * added to that night.
 */
@Composable
fun CreateScreen(teknotes: Teknotes, route: Route.Create, navigator: Navigator) {
    val model = viewModel(key = route.toString()) { CreateViewModel(teknotes, route.kind, route.name) }
    EntryForm(
        model = model,
        editing = false,
        submitLabel = if (route.nightId != null) "Créer et ajouter à la nuit" else "Créer la fiche",
        navigator = navigator,
        finish = { hit ->
            if (route.nightId != null) {
                teknotes.notebook.addEntry(route.nightId, hit)
                navigator.back()
            } else {
                navigator.replace(if (hit.kind == DirectoryHit.Kind.SOUNDSYSTEM) Route.SoundsystemDetail(hit.id) else Route.ArtistDetail(hit.id))
            }
        },
    )
}

/** Edits an entry, like a wiki: every version stays in the history. */
@Composable
fun EditScreen(teknotes: Teknotes, route: Route.Edit, navigator: Navigator) {
    val model = viewModel(key = route.toString()) { CreateViewModel(teknotes, route.kind, "", editedId = route.id) }
    EntryForm(model, editing = true, submitLabel = "Enregistrer", navigator = navigator, finish = { navigator.back() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryForm(
    model: CreateViewModel,
    editing: Boolean,
    submitLabel: String,
    navigator: Navigator,
    finish: (DirectoryHit) -> Unit,
) {
    val form by model.state.collectAsStateWithLifecycle()
    val soundsystems by model.soundsystems.collectAsStateWithLifecycle()
    var pickDepartment by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editing -> "Modifier la fiche"
                            form.kind == DirectoryHit.Kind.SOUNDSYSTEM -> "Nouveau soundsystem"
                            else -> "Nouvel artiste"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.Filled.Close, contentDescription = "Annuler") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!editing) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    listOf(DirectoryHit.Kind.SOUNDSYSTEM to "Soundsystem", DirectoryHit.Kind.ARTIST to "Artiste").forEachIndexed { index, (kind, label) ->
                        SegmentedButton(
                            selected = form.kind == kind,
                            onClick = { model.update { it.copy(kind = kind) } },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label) }
                    }
                }
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { name -> model.update { it.copy(name = name) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                label = { Text("Nom") },
                singleLine = true,
            )

            if (form.lookalikes.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Text("Ça existe peut-être déjà", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.titleSmall)
                    form.lookalikes.forEach { hit ->
                        Text(
                            hit.name + if (hit.kind == DirectoryHit.Kind.ARTIST) "  (artiste)" else "",
                            Modifier.fillMaxWidth().clickable { finish(hit) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            OutlinedTextField(
                value = form.aliases,
                onValueChange = { aliases -> model.update { it.copy(aliases = aliases) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                label = { Text("Aussi appelé (séparés par des virgules)") },
            )
            if (form.kind == DirectoryHit.Kind.SOUNDSYSTEM) {
                TextButton(onClick = { pickDepartment = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(Departments.byCode(form.department)?.label?.let { "Origine : $it" } ?: "Choisir le département d'origine")
                }
            }
            SectionLabel("Styles")
            StyleChips(form.styles, onToggle = { style ->
                model.update { it.copy(styles = if (style in it.styles) it.styles - style else it.styles + style) }
            })
            OutlinedTextField(
                value = form.links,
                onValueChange = { links -> model.update { it.copy(links = links) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                label = { Text("Liens (SoundCloud, Instagram… un par ligne)") },
                minLines = 2,
            )
            if (form.kind == DirectoryHit.Kind.ARTIST && soundsystems.isNotEmpty()) {
                SectionLabel("Joue avec")
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    soundsystems.forEach { soundsystem ->
                        FilterChip(
                            selected = soundsystem.id in form.soundsystemIds,
                            onClick = {
                                model.update {
                                    it.copy(soundsystemIds = if (soundsystem.id in it.soundsystemIds) it.soundsystemIds - soundsystem.id else it.soundsystemIds + soundsystem.id)
                                }
                            },
                            label = { Text(soundsystem.name) },
                        )
                    }
                }
            }
            if (!editing) {
                Text(
                    "Ta fiche rejoint l'annuaire partagé une fois connecté à ton compte.",
                    Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { finish(model.save()) },
                enabled = form.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp),
            ) { Text(submitLabel) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pickDepartment) {
        AlertDialog(
            onDismissRequest = { pickDepartment = false },
            confirmButton = { TextButton(onClick = { pickDepartment = false }) { Text("Fermer") } },
            title = { Text("Département d'origine") },
            text = {
                Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                    Departments.all.forEach { department ->
                        Text(
                            department.label,
                            Modifier.fillMaxWidth().clickable {
                                model.update { it.copy(department = department.code) }
                                pickDepartment = false
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}
