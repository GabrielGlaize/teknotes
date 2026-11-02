package dev.gabrielglaize.teknotes.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Appearance
import dev.gabrielglaize.teknotes.Legal
import dev.gabrielglaize.teknotes.SyncState
import dev.gabrielglaize.teknotes.Teknotes
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(teknotes: Teknotes, padding: PaddingValues, navigator: Navigator) {
    val model = viewModel { AccountViewModel(teknotes) }
    val account by model.account.collectAsStateWithLifecycle()
    val sync by model.sync.collectAsStateWithLifecycle()
    val form by model.form.collectAsStateWithLifecycle()
    val preferences by teknotes.settings.preferences().collectAsStateWithLifecycle(teknotes.settings.current())
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp)) {
        BigTitle((account as? AccountState.SignedIn)?.pseudo ?: "Profil", Modifier.padding(top = 12.dp))

        when (val state = account) {
            AccountState.Unavailable -> Note("Ton carnet marche sans compte et reste sur ce téléphone. Le serveur de l'annuaire n'est pas encore branché sur cette version.", Modifier.padding(top = 8.dp))
            AccountState.Loading -> Note("Un instant…", Modifier.padding(top = 8.dp))
            AccountState.SignedOut -> {
                Note("Ton carnet marche sans compte et reste sur ce téléphone. Un compte sert à contribuer à l'annuaire, à partager tes nuits et à retrouver ton carnet ailleurs.", Modifier.padding(top = 8.dp))
                ActionButton("Créer un compte", Modifier.padding(top = 20.dp)) { navigator.open(Route.Auth(signUp = true)) }
                ActionButton("Se connecter", Modifier.padding(top = 10.dp), primary = false) { navigator.open(Route.Auth(signUp = false)) }
            }
            is AccountState.NeedsPseudo -> {
                Note("Il ne te reste qu'à choisir ton pseudo.", Modifier.padding(top = 8.dp))
                ActionButton("Choisir mon pseudo", Modifier.padding(top = 16.dp)) { navigator.open(Route.Auth(signUp = true)) }
            }
            is AccountState.SignedIn -> {
                Note(state.email, Modifier.padding(top = 4.dp))
                SectionHead("Synchronisation")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(syncText(sync), Modifier.weight(1f), style = textStyle(15.sp))
                    TextAction("Synchroniser", enabled = !form.busy && !sync.running, onClick = model::syncNow)
                }
                Hairline()
                RowLine("Personnes bloquées", chevron = true) { navigator.open(Route.Blocked) }
            }
        }
        form.message?.let { Text(it, Modifier.padding(top = 8.dp), style = textStyle(14.sp, 600), color = Accent) }

        SectionHead("Affichage")
        WordTabs(
            listOf(Appearance.DARK, Appearance.LIGHT, Appearance.SYSTEM),
            preferences.appearance,
            { if (it == Appearance.DARK) "Sombre" else if (it == Appearance.LIGHT) "Clair" else "Système" },
            model::setAppearance,
            size = 15,
        )

        SectionHead("Sécurité")
        if (teknotes.settings.hasPasscode) {
            RowLine("Changer mon code") { navigator.open(Route.Code(CodeMode.CHANGE)) }
            RowLine("Retirer le code") { navigator.open(Route.Code(CodeMode.REMOVE)) }
        } else {
            RowLine("Protéger l'app par un code") { navigator.open(Route.Code(CodeMode.CHOOSE)) }
        }

        SectionHead("Mon carnet")
        RowLine("Exporter en JSON") { share(context, "teknotes.json", "application/json", model.exportJson()) }
        RowLine("Exporter en CSV") { share(context, "teknotes.csv", "text/csv", model.exportCsv()) }

        SectionHead("À propos")
        RowLine("Revoir le tuto") { navigator.open(Route.Tutorial) }
        RowLine("Conditions d'utilisation", chevron = true) { navigator.open(Route.Legal(privacy = false)) }
        RowLine("Confidentialité", chevron = true) { navigator.open(Route.Legal(privacy = true)) }
        Note("Contact : ${Legal.CONTACT_EMAIL}", Modifier.padding(top = 14.dp))

        if (account is AccountState.SignedIn) {
            SectionHead("Compte")
            RowLine("Se déconnecter", enabled = !form.busy, onClick = model::signOut)
            RowLine("Supprimer mon compte", color = Accent, enabled = !form.busy) { confirmDelete = true }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ton compte ?") },
            text = { Text("Ton compte et les nuits envoyées sur le serveur sont effacés. Ton carnet reste sur ce téléphone, et les fiches que tu as ajoutées à l'annuaire restent, sans ton nom.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.deleteAccount()
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMM à HH:mm", java.util.Locale.FRENCH).withZone(ZoneId.systemDefault())

private fun syncText(sync: SyncState): String = when {
    sync.running -> "Synchronisation…"
    sync.error != null -> sync.error.orEmpty()
    sync.pending > 0 -> if (sync.pending == 1) "1 modification en attente" else "${sync.pending} modifications en attente"
    sync.lastSyncedAt != null -> "Synchronisé le " + timeFormat.format(Instant.ofEpochMilli(sync.lastSyncedAt!!))
    else -> "Pas encore synchronisé"
}

/** Writes the export to a private file and opens the system share sheet. */
private fun share(context: Context, name: String, mime: String, content: String) {
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, name).apply { writeText(content) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Exporter mon carnet"))
}
