package dev.gabrielglaize.teknotes.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Legal
import dev.gabrielglaize.teknotes.LegalSection
import dev.gabrielglaize.teknotes.MergeProposal
import dev.gabrielglaize.teknotes.MyNote
import dev.gabrielglaize.teknotes.ReportReason
import dev.gabrielglaize.teknotes.Teknotes

/** Two faces of an entry. [personal]: what the user noted. Otherwise what everyone shares, with no rating at all. */
@Composable
fun FicheScreen(teknotes: Teknotes, kind: DirectoryHit.Kind, id: String, personal: Boolean, navigator: Navigator) {
    val model = viewModel(key = "$kind-$id") { FicheViewModel(teknotes, kind, id) }
    val fiche by model.fiche.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val community by model.community.collectAsStateWithLifecycle()
    val notes by model.notes.collectAsStateWithLifecycle()
    var reporting by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    val isSoundsystem = kind == DirectoryHit.Kind.SOUNDSYSTEM
    val signedIn = account is AccountState.SignedIn
    val context = LocalContext.current

    val entry = fiche
    if (entry == null) {
        LaunchedEffect(id) { navigator.back() } // gone from the directory (hidden after reports, merged)
        return
    }

    if (personal) {
        val rank by model.rank.collectAsStateWithLifecycle()
        val ratings = notes.mapNotNull { it.entry.rating }
        BackScaffold(navigator) {
            Text(if (isSoundsystem) "Soundsystem" else "Artiste", style = textStyle(14.sp, 600), color = Mute)
            BigTitle(entry.name, Modifier.padding(top = 2.dp))
            ScoreText(ratings.takeIf { it.isNotEmpty() }?.average(), size = 72, modifier = Modifier.padding(top = 16.dp))
            Note(
                listOfNotNull(
                    rank?.let { if (it == 1) "1er de ton classement" else "${it}e de ton classement" },
                    ratings.takeIf { it.isNotEmpty() }?.let { if (it.size == 1) "1 note" else "${it.size} notes" },
                ).joinToString(" · "),
            )
            SectionHead("Mes notes")
            if (notes.isEmpty()) Note("Pas encore dans ton carnet.", Modifier.padding(top = 4.dp))
            notes.forEach { note -> NoteRow(note) { navigator.open(Route.Night(note.entry.nightId)) } }
            Spacer(Modifier.height(20.dp))
            RowLine("Voir la fiche publique", chevron = true) {
                navigator.open(if (isSoundsystem) Route.SoundsystemDetail(id) else Route.ArtistDetail(id))
            }
        }
        return
    }

    BackScaffold(navigator) {
        Text(if (isSoundsystem) "Soundsystem" else "Artiste", style = textStyle(14.sp, 600), color = Mute)
        BigTitle(entry.name, Modifier.padding(top = 2.dp))
        if (entry.aliases.isNotEmpty()) Note("aussi appelé " + entry.aliases.joinToString(", "), Modifier.padding(top = 4.dp))
        if (entry.styles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Tags { entry.styles.forEach { Tag(it.label) } }
        }

        if (notes.isNotEmpty()) {
            RowLine("Voir mes notes", if (notes.size == 1) "1 note" else "${notes.size} notes", chevron = true) {
                navigator.open(if (isSoundsystem) Route.SoundsystemDetail(id, true) else Route.ArtistDetail(id, true))
            }
        }

        if (entry.related.isNotEmpty()) {
            SectionHead(if (isSoundsystem) "Artistes" else "Joue sur")
            entry.related.forEach { (relatedId, name) ->
                RowLine(name, chevron = true) {
                    navigator.open(if (isSoundsystem) Route.ArtistDetail(relatedId) else Route.SoundsystemDetail(relatedId))
                }
            }
        }

        if (entry.links.isNotEmpty()) {
            SectionHead("Liens")
            entry.links.forEach { link ->
                RowLine(link, color = Accent) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                }
            }
        }

        if (community.proposals.isNotEmpty()) {
            SectionHead("Fusions proposées")
            community.proposals.forEach { proposal -> ProposalRow(proposal, community.busy, onApprove = { model.approve(proposal) }) }
        }

        if (community.sightings.isNotEmpty()) {
            SectionHead("Nuits partagées par d'autres")
            community.sightings.forEach { sighting ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatNightDate(sighting.date), style = textStyle(15.sp, 600))
                        Note(listOfNotNull(sighting.pseudo, Departments.byCode(sighting.department)?.name).joinToString(" · "))
                    }
                    TextAction("Bloquer", color = Mute, enabled = !community.busy) { model.block(sighting) }
                }
                Hairline()
            }
        }

        community.message?.let { Text(it, Modifier.padding(top = 16.dp), style = textStyle(14.sp, 600), color = Accent) }

        SectionHead("Contribuer")
        if (signedIn) {
            RowLine("Modifier la fiche") { navigator.open(Route.Edit(kind, id)) }
            RowLine("Voir l'historique", chevron = true) { navigator.open(Route.History(kind, id)) }
            RowLine("Signaler un doublon") { merging = true }
            RowLine("Signaler un problème") { reporting = true }
        } else {
            Note("Tout le monde peut compléter cette fiche, avec un compte.", Modifier.padding(top = 4.dp))
            if (account !is AccountState.Unavailable) {
                TextAction("Créer un compte ou se connecter") { navigator.open(Route.Auth(signUp = true)) }
            }
        }

        Departments.byCode(entry.department)?.name?.let { Note("Origine : $it", Modifier.padding(top = 20.dp)) }
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
                    Note("Trois signalements de personnes différentes masquent la fiche en attendant une vérification.")
                }
            },
            confirmButton = { TextButton(onClick = { reporting = false }) { Text("Annuler") } },
        )
    }

    if (merging) {
        val query by model.mergeSearch.collectAsStateWithLifecycle()
        val results by model.mergeResults.collectAsStateWithLifecycle()
        ChoiceDialog("Doublon de quelle fiche ?", onClose = { merging = false }) {
            SearchLine("Chercher la fiche", query, model::searchMerge, Modifier.padding(vertical = 8.dp))
            results.forEach { hit ->
                HitRow(hit) {
                    merging = false
                    model.proposeMerge(hit)
                }
            }
            Note("La fiche choisie sera fondue dans « ${entry.name} » quand trois personnes seront d'accord.", Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun NoteRow(note: MyNote, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    formatNightDate(note.nightDate) + if (note.nightTitle.isEmpty()) "" else " · ${note.nightTitle}",
                    style = textStyle(13.sp),
                    color = Mute,
                )
                SetName(note.entry.artistName, note.entry.soundsystemName, note.entry.label, size = 16)
                if (note.entry.comment.isNotEmpty()) Text(note.entry.comment, style = textStyle(15.sp))
            }
            ScoreText(note.entry.rating?.toDouble())
        }
        Hairline()
    }
}

@Composable
private fun ProposalRow(proposal: MergeProposal, busy: Boolean, onApprove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (proposal.keepsThisEntry) "« ${proposal.otherName} » fondu ici" else "Fondu dans « ${proposal.otherName} »",
                style = textStyle(15.sp, 600),
            )
            Note("${proposal.approvals} accord${if (proposal.approvals > 1) "s" else ""} sur ${proposal.needed}")
        }
        if (!proposal.approvedByMe) TextAction("Approuver", enabled = !busy, onClick = onApprove)
    }
    Hairline()
}

/** Every version of an entry; any of them can be put back. */
@Composable
fun HistoryScreen(teknotes: Teknotes, route: Route.History, navigator: Navigator) {
    val model = viewModel(key = route.toString()) { HistoryViewModel(teknotes, route.kind, route.id) }
    val state by model.state.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()

    BackScaffold(navigator, "Historique") {
        state.message?.let { Text(it, Modifier.padding(top = 8.dp), style = textStyle(14.sp, 600), color = Accent) }
        if (state.loading) Note("Un instant…", Modifier.padding(top = 8.dp))
        if (!state.loading && state.revisions.isEmpty() && state.message == null) Note("Pas d'historique à afficher.", Modifier.padding(top = 8.dp))
        state.revisions.forEachIndexed { index, revision ->
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatNightDate(revision.date) + " · " + (revision.authorPseudo ?: "auteur inconnu"), style = textStyle(13.sp), color = Mute)
                Text(revision.name, style = textStyle(17.sp, 700))
                Text(if (revision.changes.isEmpty()) "Création de la fiche" else "Modifié : " + revision.changes.joinToString(", "), style = textStyle(15.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (index > 0) TextAction("Rétablir cette version") { model.restore(revision) }
                    val authorId = revision.authorId
                    val me = (account as? AccountState.SignedIn)?.pseudo
                    if (authorId != null && revision.authorPseudo != me) TextAction("Bloquer", color = Mute) { model.block(authorId) }
                }
            }
            Hairline()
        }
    }
}

@Composable
fun BlockedScreen(teknotes: Teknotes, navigator: Navigator) {
    val model = viewModel { BlockedViewModel(teknotes) }
    val users by model.users.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()

    BackScaffold(navigator, "Personnes bloquées") {
        message?.let { Text(it, Modifier.padding(top = 8.dp), style = textStyle(14.sp, 600), color = Accent) }
        if (users.isEmpty()) Note("Personne pour l'instant.", Modifier.padding(top = 8.dp))
        users.forEach { user ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(user.pseudo, Modifier.weight(1f), style = textStyle(16.sp, 600))
                TextAction("Débloquer") { model.unblock(user) }
            }
            Hairline()
        }
    }
}

@Composable
fun LegalScreen(privacy: Boolean, navigator: Navigator) {
    val sections: List<LegalSection> = if (privacy) Legal.privacy else Legal.terms
    BackScaffold(navigator, if (privacy) "Confidentialité" else "Conditions d'utilisation") {
        sections.forEach { section ->
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(section.title, style = textStyle(16.sp, 700))
                Text(section.text, style = textStyle(15.sp))
            }
            Hairline()
        }
    }
}
