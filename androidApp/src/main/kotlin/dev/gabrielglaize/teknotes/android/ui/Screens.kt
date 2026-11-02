package dev.gabrielglaize.teknotes.android.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Night
import dev.gabrielglaize.teknotes.Teknotes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector) {
    NOTEBOOK("Carnet", Icons.AutoMirrored.Filled.List),
    DIRECTORY("Annuaire", Icons.Filled.Search),
    PROFILE("Profil", Icons.Filled.Person),
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRENCH)

fun formatNightDate(isoDate: String): String = LocalDate.parse(isoDate).format(dateFormat)

@Composable
fun TeknotesRoot(teknotes: Teknotes) {
    var tab by rememberSaveable { mutableStateOf(Tab.NOTEBOOK) }
    var openNight by rememberSaveable { mutableStateOf<String?>(null) }

    openNight?.let { nightId ->
        BackHandler { openNight = null }
        NightScreen(teknotes, nightId, onBack = { openNight = null })
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
            Tab.NOTEBOOK -> NotebookScreen(teknotes, padding, onOpen = { openNight = it })
            Tab.DIRECTORY -> DirectoryScreen(teknotes, padding)
            Tab.PROFILE -> ProfileScreen(padding)
        }
    }
}

@Composable
private fun ScreenTitle(label: String, title: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Text(label.uppercase(), style = flyerLabel().copy(color = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(8.dp))
        Text(title.uppercase(), style = chromeTitle())
    }
}

@Composable
private fun NotebookScreen(teknotes: Teknotes, padding: PaddingValues, onOpen: (String) -> Unit) {
    val model = viewModel { NotebookViewModel(teknotes) }
    val nights by model.nights.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { ScreenTitle("Carnet", "Mes nuits") }
        item {
            Button(
                onClick = { onOpen(model.checkIn()) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
            NightRow(night, onClick = { onOpen(night.id) })
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
            Text(if (night.entryCount == 1) "1 son noté" else "${night.entryCount} sons notés")
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NightScreen(teknotes: Teknotes, nightId: String, onBack: () -> Unit) {
    val model = viewModel(key = nightId) { NightViewModel(teknotes, nightId) }
    val state by model.state.collectAsStateWithLifecycle()
    var pickDepartment by remember { mutableStateOf(false) }
    val night = state.night

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(night?.let { formatNightDate(it.date) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    TextButton(onClick = { model.delete(); onBack() }) { Text("Supprimer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                TextButton(onClick = { pickDepartment = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(Departments.byCode(night?.department)?.label ?: "Choisir le département")
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = model::search,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("Soundsystem ou artiste entendu") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            if (state.query.isNotBlank()) {
                items(state.results, key = { "hit-" + it.id }) { hit -> HitRow(hit, onClick = { model.add(hit) }) }
                item {
                    ListItem(
                        modifier = Modifier.clickable { model.createAndAdd(state.query) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("Créer « ${state.query.trim()} »") },
                        supportingContent = { Text("Nouveau soundsystem dans l'annuaire") },
                    )
                }
            }
            item {
                Text(
                    "ENTENDU CETTE NUIT",
                    Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
                    style = flyerLabel(),
                )
            }
            if (state.entries.isEmpty()) {
                item {
                    Text(
                        "Cherche un nom au-dessus, puis touche-le pour l'ajouter.",
                        Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.entries, key = { it.id }) { entry ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    headlineContent = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        // Big touch targets: rated at night, often with one hand.
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { star ->
                                Text(
                                    if ((entry.rating ?: 0) >= star) "●" else "○",
                                    Modifier.clickable { model.rate(entry, star) }.padding(horizontal = 6.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 24.sp,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { model.remove(entry) }) { Icon(Icons.Filled.Close, contentDescription = "Retirer") }
                    },
                )
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
                                model.setDepartment(department.code)
                                pickDepartment = false
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun HitRow(hit: DirectoryHit, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        overlineContent = {
            Text(if (hit.kind == DirectoryHit.Kind.SOUNDSYSTEM) "SOUNDSYSTEM" else "ARTISTE", style = flyerLabel())
        },
        headlineContent = { Text(hit.name) },
        supportingContent = hit.matchedAlias?.let { alias -> { Text("aussi appelé $alias") } },
    )
}

@Composable
private fun DirectoryScreen(teknotes: Teknotes, padding: PaddingValues) {
    val model = viewModel { DirectoryViewModel(teknotes) }
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.currentQuery.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            ScreenTitle("Annuaire", "Soundsystems")
            OutlinedTextField(
                value = query,
                onValueChange = model::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Chercher, même avec une faute") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (query.isNotBlank()) {
            items(state.results, key = { "hit-" + it.id }) { hit -> HitRow(hit, onClick = {}) }
        } else {
            items(state.soundsystems, key = { it.id }) { soundsystem ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    overlineContent = {
                        Text(
                            (Departments.byCode(soundsystem.department)?.label ?: "Origine inconnue").uppercase(),
                            style = flyerLabel(),
                        )
                    },
                    headlineContent = { Text(soundsystem.name) },
                    supportingContent = { Text(soundsystem.styles.joinToString(" · ") { it.label }) },
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        ScreenTitle("Profil", "Sans compte")
        Text(
            "Ton carnet marche sans compte et reste sur ce téléphone. Un compte servira à contribuer à l'annuaire partagé : il arrive avec la synchronisation.",
            Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(1.dp))
    }
}
