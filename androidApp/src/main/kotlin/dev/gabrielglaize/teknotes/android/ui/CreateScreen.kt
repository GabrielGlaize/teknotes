package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                teknotes.notebook.addSet(route.nightId, teknotes.directory.defaultChoice(hit))
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

@Composable
private fun EntryForm(
    model: CreateViewModel,
    editing: Boolean,
    submitLabel: String,
    navigator: Navigator,
    finish: (DirectoryHit) -> Unit,
) {
    val form by model.state.collectAsStateWithLifecycle()
    var pickDepartment by remember { mutableStateOf(false) }
    var pickStyles by remember { mutableStateOf(false) }
    var pickSoundsystem by remember { mutableStateOf(false) }
    val isSoundsystem = form.kind == DirectoryHit.Kind.SOUNDSYSTEM

    BackScaffold(navigator, if (editing) "Modifier la fiche" else "Nouvelle fiche") {
        if (!editing) {
            WordTabs(
                listOf(DirectoryHit.Kind.ARTIST, DirectoryHit.Kind.SOUNDSYSTEM),
                form.kind,
                { if (it == DirectoryHit.Kind.SOUNDSYSTEM) "Soundsystem" else "Artiste" },
                { kind -> model.update { it.copy(kind = kind) } },
                size = 16,
            )
        }
        LineField("Nom", form.name, { name -> model.update { it.copy(name = name) } })

        if (form.lookalikes.isNotEmpty()) {
            Text("Ça existe peut-être déjà", Modifier.padding(top = 8.dp), style = textStyle(13.sp, 600), color = Accent)
            form.lookalikes.forEach { hit -> HitRow(hit) { finish(hit) } }
        }

        if (!isSoundsystem) {
            Picked("Joue sur") {
                form.soundsystemIds.forEach { id ->
                    val name = model.directory.soundsystem(id)?.name ?: return@forEach
                    Tag(name, trailing = "×") { model.update { it.copy(soundsystemIds = it.soundsystemIds - id) } }
                }
                Tag("Ajouter", trailing = "+") { pickSoundsystem = true }
            }
        }
        Picked("Styles") {
            form.styles.sortedBy { it.label }.forEach { style ->
                Tag(style.label, trailing = "×") { model.update { it.copy(styles = it.styles - style) } }
            }
            Tag("Ajouter", trailing = "+") { pickStyles = true }
        }

        LineField("Aussi appelé (séparés par des virgules)", form.aliases, { aliases -> model.update { it.copy(aliases = aliases) } })
        LineField("Liens (SoundCloud, Instagram… un par ligne)", form.links, { links -> model.update { it.copy(links = links) } }, singleLine = false)

        if (isSoundsystem) {
            RowLine("Origine", Departments.byCode(form.department)?.name ?: "Non précisée", chevron = true) { pickDepartment = true }
        }

        ActionButton(submitLabel, Modifier.padding(top = 24.dp), enabled = form.name.isNotBlank()) { finish(model.save()) }
        if (!editing) Note("Ta fiche rejoint l'annuaire public une fois connecté à ton compte.", Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(32.dp))
    }

    if (pickDepartment) {
        DepartmentPicker("Origine", onPick = { code -> model.update { it.copy(department = code) } }, onClose = { pickDepartment = false })
    }
    if (pickStyles) {
        StylePicker(model.directory, form.styles, { styles -> model.update { it.copy(styles = styles) } }, onClose = { pickStyles = false })
    }
    if (pickSoundsystem) {
        SearchPicker(
            title = "Joue sur",
            prompt = "Chercher un soundsystem",
            items = { model.directory.browse(DirectoryHit.Kind.SOUNDSYSTEM, it) },
            label = { it.name },
            onPick = { hit -> model.update { it.copy(soundsystemIds = it.soundsystemIds + hit.id) } },
            onClose = { pickSoundsystem = false },
        )
    }
}

@Composable
private fun Picked(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, Modifier.padding(bottom = 8.dp), style = textStyle(13.sp, 600), color = Mute)
        Tags(content)
    }
}
