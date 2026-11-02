package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.AccountState
import dev.gabrielglaize.teknotes.Teknotes

/**
 * Creating an account and signing in are two separate paths; choosing a pseudo ends the
 * first sign-in, whichever way it came.
 */
@Composable
fun AuthScreen(teknotes: Teknotes, route: Route.Auth, navigator: Navigator) {
    val model = viewModel { AccountViewModel(teknotes) }
    val account by model.account.collectAsStateWithLifecycle()
    val form by model.form.collectAsStateWithLifecycle()
    var signUp by remember { mutableStateOf(route.signUp) }
    var usingCode by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        if (account is AccountState.SignedIn) navigator.back()
    }

    BackScaffold(navigator) {
        val fill = !form.busy && form.email.isNotBlank() && form.password.isNotBlank()
        when {
            account is AccountState.NeedsPseudo -> {
                BigTitle("Ton pseudo", Modifier.padding(bottom = 8.dp))
                Note("C'est la seule chose que les autres verront de toi.", Modifier.padding(bottom = 16.dp))
                LineField("Pseudo", form.pseudo, { pseudo -> model.edit { it.copy(pseudo = pseudo) } })
                Row(
                    Modifier.fillMaxWidth().toggleable(value = form.termsAccepted, role = Role.Checkbox, onValueChange = { accepted -> model.edit { it.copy(termsAccepted = accepted) } }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = form.termsAccepted, onCheckedChange = null, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                    Text("J'accepte les conditions d'utilisation", Modifier.weight(1f).padding(start = 8.dp), style = textStyle(15.sp))
                }
                TextAction("Lire les conditions") { navigator.open(Route.Legal(privacy = false)) }
                Message(form.message)
                ActionButton("Valider", Modifier.padding(top = 20.dp), enabled = !form.busy && form.pseudo.isNotBlank() && form.termsAccepted, onClick = model::choosePseudo)
            }
            usingCode -> {
                BigTitle("Recevoir un code", Modifier.padding(bottom = 16.dp))
                LineField("Email", form.email, { email -> model.edit { it.copy(email = email) } }, keyboard = KeyboardType.Email)
                if (form.codeSent) {
                    Note("Un code à 6 chiffres vient de partir par mail.", Modifier.padding(top = 4.dp))
                    LineField("Code reçu", form.code, { code -> model.edit { it.copy(code = code) } }, keyboard = KeyboardType.NumberPassword)
                }
                Message(form.message)
                if (form.codeSent) {
                    ActionButton("Valider le code", Modifier.padding(top = 20.dp), enabled = !form.busy && form.code.isNotBlank(), onClick = model::verifyCode)
                } else {
                    ActionButton("Envoyer le code", Modifier.padding(top = 20.dp), enabled = !form.busy && form.email.isNotBlank(), onClick = model::sendCode)
                }
                TextAction("Revenir", Modifier.padding(top = 8.dp), color = Ink) {
                    model.edit { it.copy(codeSent = false, code = "") }
                    usingCode = false
                }
            }
            signUp -> {
                BigTitle("Créer un compte", Modifier.padding(bottom = 8.dp))
                Note("Pour contribuer à l'annuaire, partager tes nuits et retrouver ton carnet sur un autre téléphone.", Modifier.padding(bottom = 16.dp))
                LineField("Email", form.email, { email -> model.edit { it.copy(email = email) } }, keyboard = KeyboardType.Email)
                LineField("Mot de passe (6 caractères minimum)", form.password, { password -> model.edit { it.copy(password = password) } }, keyboard = KeyboardType.Password, secure = true)
                Message(form.message)
                ActionButton("Créer mon compte", Modifier.padding(top = 20.dp), enabled = fill, onClick = model::signUp)
                TextAction("J'ai déjà un compte", Modifier.padding(top = 8.dp), color = Ink) {
                    model.clearMessage()
                    signUp = false
                }
            }
            else -> {
                BigTitle("Se connecter", Modifier.padding(bottom = 16.dp))
                LineField("Email", form.email, { email -> model.edit { it.copy(email = email) } }, keyboard = KeyboardType.Email)
                LineField("Mot de passe", form.password, { password -> model.edit { it.copy(password = password) } }, keyboard = KeyboardType.Password, secure = true)
                Message(form.message)
                ActionButton("Me connecter", Modifier.padding(top = 20.dp), enabled = fill, onClick = model::signIn)
                TextAction("Recevoir plutôt un code par mail", Modifier.padding(top = 8.dp), color = Ink) {
                    model.clearMessage()
                    usingCode = true
                }
                TextAction("Je n'ai pas encore de compte", color = Ink) {
                    model.clearMessage()
                    signUp = true
                }
            }
        }
    }
}

@Composable
private fun Message(text: String?) {
    text?.let { Text(it, Modifier.padding(top = 8.dp), style = textStyle(14.sp, 600), color = Accent) }
}
