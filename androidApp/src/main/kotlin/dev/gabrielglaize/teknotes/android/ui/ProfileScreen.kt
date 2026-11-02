package dev.gabrielglaize.teknotes.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Appearance
import dev.gabrielglaize.teknotes.SyncState
import dev.gabrielglaize.teknotes.Teknotes
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(teknotes: Teknotes, padding: PaddingValues) {
    val model = viewModel { AccountViewModel(teknotes) }
    val account by model.account.collectAsStateWithLifecycle()
    val sync by model.sync.collectAsStateWithLifecycle()
    val form by model.form.collectAsStateWithLifecycle()
    val preferences by teknotes.settings.preferences().collectAsStateWithLifecycle(teknotes.settings.current())
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        val title = when (val state = account) {
            is AccountState.SignedIn -> state.pseudo
            is AccountState.NeedsPseudo -> "Ton pseudo"
            else -> "Sans compte"
        }
        ScreenTitle("Profil", title)

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val state = account) {
                AccountState.Unavailable -> Explanation("Ton carnet marche sans compte et reste sur ce téléphone. Le serveur de l'annuaire n'est pas encore branché sur cette version.")
                AccountState.Loading -> Explanation("Un instant…")
                AccountState.SignedOut -> SignedOutForm(form, model)
                is AccountState.NeedsPseudo -> PseudoForm(form, model)
                is AccountState.SignedIn -> SignedInPanel(state, sync, form, model)
            }
            form.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }

        SectionLabel("Apparence")
        AppearancePicker(preferences.appearance, model::setAppearance)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mode champ", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Écran sombre, couleurs atténuées et texte plus grand, pour noter en pleine nuit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = preferences.fieldMode, onCheckedChange = model::setFieldMode)
        }

        SectionLabel("Mon carnet")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { share(context, "teknotes.json", "application/json", model.exportJson()) }) { Text("Exporter en JSON") }
            OutlinedButton(onClick = { share(context, "teknotes.csv", "text/csv", model.exportCsv()) }) { Text("Exporter en CSV") }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Explanation(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SignedOutForm(form: AccountForm, model: AccountViewModel) {
    Explanation("Ton carnet marche sans compte et reste sur ce téléphone. Un compte sert à contribuer à l'annuaire partagé et à retrouver ton carnet sur un autre téléphone.")
    OutlinedTextField(
        value = form.email,
        onValueChange = { email -> model.edit { it.copy(email = email) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    if (form.codeSent) {
        Explanation("Un code à 6 chiffres vient de partir par mail.")
        OutlinedTextField(
            value = form.code,
            onValueChange = { code -> model.edit { it.copy(code = code) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Code reçu") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Button(onClick = model::verifyCode, enabled = !form.busy && form.code.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Valider le code") }
        TextButton(onClick = { model.edit { it.copy(codeSent = false, code = "") } }) { Text("Revenir") }
    } else {
        OutlinedTextField(
            value = form.password,
            onValueChange = { password -> model.edit { it.copy(password = password) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = model::signIn, enabled = !form.busy && form.email.isNotBlank() && form.password.isNotBlank()) { Text("Se connecter") }
            OutlinedButton(onClick = model::signUp, enabled = !form.busy && form.email.isNotBlank() && form.password.isNotBlank()) { Text("Créer un compte") }
        }
        TextButton(onClick = model::sendCode, enabled = !form.busy && form.email.isNotBlank()) { Text("Recevoir plutôt un code par mail") }
    }
}

@Composable
private fun PseudoForm(form: AccountForm, model: AccountViewModel) {
    Explanation("Ton pseudo est la seule chose que les autres verront de toi.")
    OutlinedTextField(
        value = form.pseudo,
        onValueChange = { pseudo -> model.edit { it.copy(pseudo = pseudo) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Pseudo") },
        singleLine = true,
    )
    Button(onClick = model::choosePseudo, enabled = !form.busy && form.pseudo.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Valider") }
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMM à HH:mm", java.util.Locale.FRENCH).withZone(ZoneId.systemDefault())

@Composable
private fun SignedInPanel(state: AccountState.SignedIn, sync: SyncState, form: AccountForm, model: AccountViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    Explanation(state.email)
    Text(
        when {
            sync.running -> "Synchronisation…"
            sync.error != null -> sync.error.orEmpty()
            sync.pending > 0 -> if (sync.pending == 1) "1 modification en attente" else "${sync.pending} modifications en attente"
            sync.lastSyncedAt != null -> "Synchronisé le " + timeFormat.format(Instant.ofEpochMilli(sync.lastSyncedAt!!))
            else -> "Pas encore synchronisé"
        },
    )
    Button(onClick = model::syncNow, enabled = !form.busy && !sync.running, modifier = Modifier.fillMaxWidth()) { Text("Synchroniser") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = model::signOut, enabled = !form.busy) { Text("Se déconnecter") }
        TextButton(onClick = { confirmDelete = true }, enabled = !form.busy) { Text("Supprimer mon compte") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePicker(current: Appearance, onChange: (Appearance) -> Unit) {
    val options = listOf(Appearance.SYSTEM to "Système", Appearance.DARK to "Sombre", Appearance.LIGHT to "Clair")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        options.forEachIndexed { index, (appearance, label) ->
            SegmentedButton(
                selected = current == appearance,
                onClick = { onChange(appearance) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

/** Writes the export to a private file and opens the system share sheet. */
private fun share(context: Context, name: String, mime: String, content: String) {
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, name).apply { writeText(content) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Exporter mon carnet"))
}
