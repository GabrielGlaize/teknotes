package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gabrielglaize.teknotes.Teknotes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CodeMode { CHOOSE, CHANGE, REMOVE }

private val IntroPages = listOf(
    Triple("1", "Note ce que tu entends", "Un set, c'est un artiste sur un soundsystem : Pyl (Caisson Noir). Une note de 1 à 5, en deux touches, même dans le noir."),
    Triple("2", "Ton classement se fait tout seul", "Tes notes rangent les artistes et les soundsystems du meilleur au moins bon, sans compte ni réseau."),
    Triple("3", "Reste discret", "Jamais de lieu précis ni de photo. Ton carnet reste privé tant que tu ne partages pas une nuit."),
)

/** The first launch: what the app is for, then the code. [replay]: seen again, the code isn't asked. */
@Composable
fun IntroScreen(teknotes: Teknotes, replay: Boolean = false, onDone: () -> Unit) {
    var choosingCode by remember { mutableStateOf(false) }
    if (choosingCode) {
        PasscodeSetup(teknotes, allowsSkipping = true, onDone = onDone)
        return
    }
    val pager = rememberPagerState(pageCount = { IntroPages.size })
    val scope = rememberCoroutineScope()
    val finish = { if (replay || teknotes.settings.hasPasscode) onDone() else choosingCode = true }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction(if (replay) "Fermer" else "Passer", color = Mute, onClick = finish)
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            val (digit, title, text) = IntroPages[page]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(digit, style = textStyle(150.sp, 800).copy(letterSpacing = (-6).sp, lineHeight = 150.sp))
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(2.dp).background(Ink))
                Text(title, style = textStyle(32.sp, 800).copy(letterSpacing = (-1).sp, lineHeight = 36.sp))
                Text(text, Modifier.padding(top = 12.dp), style = textStyle(16.sp), color = Mute)
            }
        }
        val last = pager.currentPage == IntroPages.lastIndex
        ActionButton(if (!last) "Continuer" else if (replay) "Terminer" else "Choisir mon code", Modifier.padding(bottom = 12.dp)) {
            if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

/** Four marks and a keypad, drawn like the rest of the app: big digits, no circles. */
@Composable
private fun Keypad(title: String, note: String, onCode: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    LaunchedEffect(code) {
        if (code.length == 4) {
            delay(120) // the last mark shows before anything changes
            val entered = code
            code = ""
            onCode(entered)
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, Modifier.padding(top = 48.dp), style = textStyle(30.sp, 800).copy(letterSpacing = (-1).sp), textAlign = TextAlign.Center)
        Text(note, Modifier.padding(top = 6.dp), style = textStyle(14.sp), color = Mute, textAlign = TextAlign.Center)
        Row(
            Modifier.padding(top = 28.dp).semantics { contentDescription = "${code.length} chiffres sur 4" },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            (0 until 4).forEach { index ->
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(if (index < code.length) Ink else Line))
            }
        }
        Spacer(Modifier.weight(1f))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { keys ->
            Row(Modifier.fillMaxWidth()) {
                keys.forEach { key ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 72.dp).then(
                            if (key.isEmpty()) {
                                Modifier
                            } else {
                                Modifier.clickable {
                                    if (key == "⌫") code = code.dropLast(1) else if (code.length < 4) code += key
                                }
                            },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = textStyle(if (key == "⌫") 26.sp else 38.sp, 800))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Chooses a code, typed twice. */
@Composable
fun PasscodeSetup(teknotes: Teknotes, allowsSkipping: Boolean = false, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Keypad(
            title = if (first == null) "Choisis ton code" else "Encore une fois",
            note = if (mismatch) "Les deux codes sont différents. Recommence." else "Il protège ton carnet sur ce téléphone. S'il est oublié, il faudra réinstaller l'app : garde un compte pour ne rien perdre.",
        ) { code ->
            val chosen = first
            when {
                chosen == null -> {
                    first = code
                    mismatch = false
                }
                chosen == code -> {
                    teknotes.settings.setPasscode(code)
                    onDone()
                }
                else -> {
                    first = null
                    mismatch = true
                }
            }
        }
        if (allowsSkipping) {
            TextAction("Plus tard", Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(horizontal = 24.dp), color = Mute, onClick = onDone)
        }
    }
}

/** In front of the app, until the right code. */
@Composable
fun LockScreen(teknotes: Teknotes, onUnlock: () -> Unit) {
    var message by remember { mutableStateOf("Entre ton code") }
    Keypad("Tek'Notes !", message) { code ->
        if (teknotes.settings.checkPasscode(code)) {
            onUnlock()
        } else {
            val wait = teknotes.settings.passcodeWaitSeconds()
            message = if (wait > 0) "Trop d'essais. Réessaie dans $wait secondes." else "Ce n'est pas le bon code."
        }
    }
}

/** The code, from the profile: changing or removing it asks for the current one first. */
@Composable
fun CodeScreen(teknotes: Teknotes, mode: CodeMode, navigator: Navigator) {
    var checked by remember { mutableStateOf(mode == CodeMode.CHOOSE) }
    if (!checked) {
        LockScreen(teknotes, onUnlock = {
            if (mode == CodeMode.REMOVE) {
                teknotes.settings.removePasscode()
                navigator.back()
            } else {
                checked = true
            }
        })
    } else {
        PasscodeSetup(teknotes, onDone = navigator::back)
    }
}
