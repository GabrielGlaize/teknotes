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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Night
import dev.gabrielglaize.teknotes.NightEntry
import dev.gabrielglaize.teknotes.Teknotes

@Composable
fun NotebookScreen(teknotes: Teknotes, padding: PaddingValues, navigator: Navigator) {
    val model = viewModel { NotebookViewModel(teknotes) }
    val nights by model.nights.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { ScreenTitle("Carnet", "Mes nuits") }
        item {
            Button(
                onClick = { navigator.open(Route.Night(model.checkIn())) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(64.dp),
            ) {
                Text("J'y suis", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (nights.isEmpty()) {
            item {
                Text(
                    "Aucune nuit notée pour l'instant. Le soir venu, appuie sur « J'y suis ».",
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(nights, key = { it.id }) { night ->
            NightRow(night, onClick = { navigator.open(Route.Night(night.id)) })
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun NightRow(night: Night, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        overlineContent = { Text(formatNightDate(night.date).uppercase(), style = flyerLabel()) },
        headlineContent = {
            Text(night.title.ifBlank { Departments.byCode(night.department)?.label ?: "Département à préciser" })
        },
        supportingContent = {
            val count = if (night.entryCount == 1) "1 son noté" else "${night.entryCount} sons notés"
            Text(if (night.shared) "$count · partagée" else count)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightScreen(teknotes: Teknotes, nightId: String, navigator: Navigator) {
    val model = viewModel(key = nightId) { NightViewModel(teknotes, nightId) }
    val state by model.state.collectAsStateWithLifecycle()
    var pickDepartment by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val night = state.night ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatNightDate(night.date)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = { TextButton(onClick = { confirmDelete = true }) { Text("Supprimer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                TextButton(onClick = { pickDepartment = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(Departments.byCode(night.department)?.label ?: "Choisir le département")
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = model::search,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("Soundsystem ou artiste entendu") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { DictationButton(onText = model::search) },
                    singleLine = true,
                )
            }
            if (state.query.isNotBlank()) {
                items(state.results, key = { "hit-" + it.id }) { hit -> HitRow(hit, onClick = { model.add(hit) }) }
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            navigator.open(Route.Create(DirectoryHit.Kind.SOUNDSYSTEM, state.query.trim(), nightId))
                            model.search("")
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("Créer « ${state.query.trim()} »") },
                        supportingContent = { Text("Nouveau soundsystem ou artiste dans l'annuaire") },
                    )
                }
            }

            item { SectionLabel("Entendu cette nuit") }
            if (state.entries.isEmpty()) {
                item {
                    Text(
                        "Cherche un nom au-dessus, puis touche-le pour l'ajouter.",
                        Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.entries, key = { it.id }) { entry -> EntryRow(entry, model) }

            item {
                SectionLabel("La nuit")
                NightFields(night, model)
            }
        }
    }

    if (pickDepartment) {
        AlertDialog(
            onDismissRequest = { pickDepartment = false },
            confirmButton = { TextButton(onClick = { pickDepartment = false }) { Text("Fermer") } },
            title = { Text("Département") },
            text = {
                LazyColumn(Modifier.height(400.dp)) {
                    items(Departments.all, key = { it.code }) { department ->
                        Text(
                            department.label,
                            Modifier.fillMaxWidth().clickable {
                                model.edit(department = department.code)
                                pickDepartment = false
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
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

@Composable
private fun EntryRow(entry: NightEntry, model: NightViewModel) {
    var comment by remember(entry.id) { mutableStateOf(entry.comment) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { model.remove(entry) }) { Icon(Icons.Filled.Close, contentDescription = "Retirer ${entry.label}") }
        }
        // Big touch targets: rated at night, often with one hand.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { star ->
                Text(
                    if ((entry.rating ?: 0) >= star) "●" else "○",
                    Modifier
                        .clickable(onClickLabel = "Noter $star sur 5") { model.rate(entry, star) }
                        .semantics { contentDescription = "Note $star sur 5" + if (entry.rating == star) ", choisie" else "" }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 24.sp,
                )
            }
        }
        OutlinedTextField(
            value = comment,
            onValueChange = {
                comment = it
                model.comment(entry, it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Un mot sur ce set") },
            trailingIcon = {
                DictationButton(onText = { spoken ->
                    comment = listOf(comment, spoken).filter { it.isNotBlank() }.joinToString(" ")
                    model.comment(entry, comment)
                })
            },
        )
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun NightFields(night: Night, model: NightViewModel) {
    var title by remember(night.id) { mutableStateOf(night.title) }
    var comment by remember(night.id) { mutableStateOf(night.comment) }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                model.edit(title = it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Titre (facultatif)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = comment,
            onValueChange = {
                comment = it
                model.edit(comment = it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Souvenirs de la nuit") },
            minLines = 3,
            trailingIcon = {
                DictationButton(onText = { spoken ->
                    comment = listOf(comment, spoken).filter { it.isNotBlank() }.joinToString(" ")
                    model.edit(comment = comment)
                })
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Partager cette nuit", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Visible des autres à partir du lendemain, avec le département seulement. Il faut un compte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = night.shared, onCheckedChange = { model.edit(shared = it) })
        }
        Spacer(Modifier.height(32.dp))
    }
}
