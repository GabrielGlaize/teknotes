package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Legal
import dev.gabrielglaize.teknotes.LegalSection
import dev.gabrielglaize.teknotes.MergeProposal
import dev.gabrielglaize.teknotes.ReportReason
import dev.gabrielglaize.teknotes.Teknotes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(navigator: Navigator, title: String = "", content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) { content() } }
}

@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label.uppercase(), style = flyerLabel())
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A soundsystem or an artist: its content, and what the community can do with it. */
@Composable
fun FicheScreen(teknotes: Teknotes, kind: DirectoryHit.Kind, id: String, navigator: Navigator) {
    val model = viewModel(key = "$kind-$id") { FicheViewModel(teknotes, kind, id) }
    val fiche by model.fiche.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val community by model.community.collectAsStateWithLifecycle()
    var reporting by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    val isSoundsystem = kind == DirectoryHit.Kind.SOUNDSYSTEM
    val signedIn = account is AccountState.SignedIn

    val entry = fiche
    if (entry == null) {
        LaunchedEffect(id) { navigator.back() } // gone from the directory (hidden after reports, merged)
        return
    }

    BackScaffold(navigator) {
        ScreenTitle(if (isSoundsystem) "Soundsystem" else "Artiste", entry.name)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isSoundsystem) Fact("Origine", Departments.byCode(entry.department)?.label ?: "Inconnue")
            Fact("Styles", entry.styles.joinToString(" · ") { it.label })
            Fact("Aussi appelé", entry.aliases.joinToString(", "))
            Fact("Liens", entry.links.joinToString("\n"))
        }

        if (entry.related.isNotEmpty()) {
            SectionLabel(if (isSoundsystem) "Membres" else "Soundsystems")
            entry.related.forEach { (relatedId, name) ->
                Text(
                    name,
                    Modifier.fillMaxWidth().clickable {
                        navigator.open(if (isSoundsystem) Route.ArtistDetail(relatedId) else Route.SoundsystemDetail(relatedId))
                    }.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (community.proposals.isNotEmpty()) {
            SectionLabel("Fusions proposées")
            community.proposals.forEach { proposal -> ProposalRow(proposal, entry.name, community.busy, onApprove = { model.approve(proposal) }) }
        }

        if (community.sightings.isNotEmpty()) {
            SectionLabel("Nuits partagées")
            community.sightings.forEach { sighting ->
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatNightDate(sighting.date), style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(Departments.byCode(sighting.department)?.label, sighting.pseudo).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { model.block(sighting) }, enabled = !community.busy) { Text("Bloquer") }
                }
            }
        }

        community.message?.let {
            Text(it, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.primary)
        }

        SectionLabel("L'annuaire est à tout le monde")
        if (signedIn) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { navigator.open(Route.Edit(kind, id)) }, modifier = Modifier.fillMaxWidth()) { Text("Modifier la fiche") }
                OutlinedButton(onClick = { navigator.open(Route.History(kind, id)) }, modifier = Modifier.fillMaxWidth()) { Text("Voir l'historique") }
                OutlinedButton(onClick = { merging = true }, modifier = Modifier.fillMaxWidth()) { Text("Signaler un doublon") }
                TextButton(onClick = { reporting = true }, modifier = Modifier.fillMaxWidth()) { Text("Signaler un problème") }
            }
        } else {
            Text(
                "Connecte-toi depuis le profil pour modifier cette fiche, voir son historique ou la signaler.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (reporting) {
        AlertDialog(
            onDismissRequest = { reporting = false },
            title = { Text("Quel est le problème ?") },
            text = {
                Column {
                    ReportReason.entries.forEach { reason ->
                        Text(
                            reason.label,
                            Modifier.fillMaxWidth().clickable {
                                reporting = false
                                model.report(reason)
                            }.padding(vertical = 12.dp),
                        )
                    }
                    Text(
                        "Trois signalements de personnes différentes masquent la fiche en attendant une vérification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { reporting = false }) { Text("Annuler") } },
        )
    }

    if (merging) {
        val query by model.mergeSearch.collectAsStateWithLifecycle()
        val results by model.mergeResults.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("C'est le doublon de quelle fiche ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "La fiche choisie sera fondue dans « ${entry.name} » quand trois personnes seront d'accord.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = model::searchMerge,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Chercher la fiche") },
                        singleLine = true,
                    )
                    results.forEach { hit ->
                        Text(
                            hit.name,
                            Modifier.fillMaxWidth().clickable {
                                merging = false
                                model.proposeMerge(hit)
                            }.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { merging = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProposalRow(proposal: MergeProposal, name: String, busy: Boolean, onApprove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (proposal.keepsThisEntry) "« ${proposal.otherName} » fondu ici" else "Fondu dans « ${proposal.otherName} »",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${proposal.approvals} accord${if (proposal.approvals > 1) "s" else ""} sur ${proposal.needed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!proposal.approvedByMe) TextButton(onClick = onApprove, enabled = !busy) { Text("Approuver") }
    }
}

/** Every version of an entry; any of them can be put back. */
@Composable
fun HistoryScreen(teknotes: Teknotes, route: Route.History, navigator: Navigator) {
    val model = viewModel(key = route.toString()) { HistoryViewModel(teknotes, route.kind, route.id) }
    val state by model.state.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()

    BackScaffold(navigator, "Historique") {
        state.message?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.primary) }
        if (state.loading) Text("Un instant…", Modifier.padding(20.dp))
        if (!state.loading && state.revisions.isEmpty() && state.message == null) {
            Text("Pas d'historique à afficher.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.revisions.forEachIndexed { index, revision ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(formatNightDate(revision.date).uppercase(), style = flyerLabel())
                Text(revision.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (revision.changes.isEmpty()) "Création de la fiche" else "Modifié : " + revision.changes.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    revision.authorPseudo?.let { "par $it" } ?: "auteur inconnu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (index > 0) TextButton(onClick = { model.restore(revision) }) { Text("Rétablir cette version") }
                    val authorId = revision.authorId
                    val me = (account as? AccountState.SignedIn)?.pseudo
                    if (authorId != null && revision.authorPseudo != me) TextButton(onClick = { model.block(authorId) }) { Text("Bloquer") }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun BlockedScreen(teknotes: Teknotes, navigator: Navigator) {
    val model = viewModel { BlockedViewModel(teknotes) }
    val users by model.users.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()

    BackScaffold(navigator, "Personnes bloquées") {
        message?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.primary) }
        if (users.isEmpty()) {
            Text("Personne pour l'instant.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        users.forEach { user ->
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(user.pseudo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { model.unblock(user) }) { Text("Débloquer") }
            }
        }
    }
}

@Composable
fun LegalScreen(privacy: Boolean, navigator: Navigator) {
    val sections: List<LegalSection> = if (privacy) Legal.privacy else Legal.terms
    BackScaffold(navigator, if (privacy) "Confidentialité" else "Conditions d'utilisation") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            sections.forEach { section ->
                Column {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
