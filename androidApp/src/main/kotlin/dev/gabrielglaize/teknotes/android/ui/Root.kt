package dev.gabrielglaize.teknotes.android.ui

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Teknotes

private val TabLabels = listOf("Carnet", "Annuaire", "Profil")

/** Screens opened on top of the tabs. */
sealed interface Route {
    data class Night(val id: String) : Route

    /** [personal]: what the user noted. Otherwise what everyone shares, with no rating. */
    data class SoundsystemDetail(val id: String, val personal: Boolean = false) : Route
    data class ArtistDetail(val id: String, val personal: Boolean = false) : Route

    /** Creation form; when opened from a night, what is created is added to it. */
    data class Create(val kind: DirectoryHit.Kind, val name: String, val nightId: String?) : Route
    data class Edit(val kind: DirectoryHit.Kind, val id: String) : Route
    data class History(val kind: DirectoryHit.Kind, val id: String) : Route
    data object Blocked : Route

    /** Creating an account or signing in: two separate paths. */
    data class Auth(val signUp: Boolean) : Route
    data object Tutorial : Route
    data class Code(val mode: CodeMode) : Route
    data class Legal(val privacy: Boolean) : Route
}

private fun Route.encode(): String = when (this) {
    is Route.Night -> "n|$id"
    is Route.SoundsystemDetail -> "s|$id|$personal"
    is Route.ArtistDetail -> "a|$id|$personal"
    Route.Tutorial -> "t"
    is Route.Code -> "k|${mode.name}"
    is Route.Create -> "c|${kind.name}|${nightId.orEmpty()}|$name"
    is Route.Edit -> "e|${kind.name}|$id"
    is Route.History -> "h|${kind.name}|$id"
    Route.Blocked -> "b"
    is Route.Auth -> "u|$signUp"
    is Route.Legal -> "l|$privacy"
}

private fun decode(text: String): Route {
    val parts = text.split("|", limit = 4)
    return when (parts[0]) {
        "n" -> Route.Night(parts[1])
        "s" -> Route.SoundsystemDetail(parts[1], parts[2].toBoolean())
        "a" -> Route.ArtistDetail(parts[1], parts[2].toBoolean())
        "t" -> Route.Tutorial
        "k" -> Route.Code(CodeMode.valueOf(parts[1]))
        "e" -> Route.Edit(DirectoryHit.Kind.valueOf(parts[1]), parts[2])
        "h" -> Route.History(DirectoryHit.Kind.valueOf(parts[1]), parts[2])
        "b" -> Route.Blocked
        "u" -> Route.Auth(parts[1].toBoolean())
        "l" -> Route.Legal(parts[1].toBoolean())
        else -> Route.Create(DirectoryHit.Kind.valueOf(parts[1]), parts[3], parts[2].ifEmpty { null })
    }
}

/** Navigation state: what is open, kept across rotation. */
class Navigator(val stack: MutableList<Route>) {
    fun open(route: Route) {
        stack += route
    }

    fun back() {
        stack.removeAt(stack.lastIndex)
    }

    /** Replaces the top screen (e.g. the creation form by the night it came from). */
    fun replace(route: Route?) {
        back()
        route?.let { open(it) }
    }
}

/**
 * Gives a screen its own view models, cleared when it closes. Without this they live as long
 * as the activity: a form or a history opened again would show old data.
 */
@Composable
private fun OwnViewModels(route: Route, content: @Composable () -> Unit) {
    key(route) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(Unit) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
    }
}

@Composable
fun TeknotesRoot(teknotes: Teknotes) {
    var tutorialDone by rememberSaveable { mutableStateOf(teknotes.settings.tutorialDone) }
    var locked by rememberSaveable { mutableStateOf(teknotes.settings.hasPasscode) }
    // Leaving the app locks it again.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (teknotes.settings.hasPasscode) locked = true }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).closesKeyboard()) {
        when {
            !tutorialDone -> IntroScreen(teknotes, onDone = {
                teknotes.settings.setTutorialDone()
                tutorialDone = true
            })
            else -> {
                Screens(teknotes)
                if (locked) LockScreen(teknotes, onUnlock = { locked = false })
            }
        }
    }
}

@Composable
private fun Screens(teknotes: Teknotes) {
    val stack = rememberSaveable(
        saver = listSaver(save = { list -> list.map { it.encode() } }, restore = { saved -> saved.map(::decode).toMutableStateList() }),
    ) { mutableListOf<Route>().toMutableStateList() }
    val navigator = Navigator(stack)
    val pager = rememberPagerState(pageCount = { TabLabels.size })

    if (stack.isNotEmpty()) BackHandler { navigator.back() }

    // A screen opened on top slides in; going back slides it out.
    AnimatedContent(
        targetState = stack.toList(),
        contentKey = { it.lastOrNull() },
        transitionSpec = {
            val forward = targetState.size >= initialState.size
            val motion = tween<IntOffset>(300, easing = FastOutSlowInEasing)
            (slideInHorizontally(motion) { if (forward) it else -it / 3 })
                .togetherWith(slideOutHorizontally(motion) { if (forward) -it / 3 else it })
        },
        label = "screens",
    ) { shown ->
        val route = shown.lastOrNull()
        if (route == null) {
            Tabs(teknotes, pager, navigator)
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                OwnViewModels(route) {
                    when (route) {
                        is Route.Night -> NightScreen(teknotes, route.id, navigator)
                        is Route.SoundsystemDetail -> FicheScreen(teknotes, DirectoryHit.Kind.SOUNDSYSTEM, route.id, route.personal, navigator)
                        is Route.ArtistDetail -> FicheScreen(teknotes, DirectoryHit.Kind.ARTIST, route.id, route.personal, navigator)
                        is Route.Create -> CreateScreen(teknotes, route, navigator)
                        is Route.Edit -> EditScreen(teknotes, route, navigator)
                        is Route.History -> HistoryScreen(teknotes, route, navigator)
                        Route.Blocked -> BlockedScreen(teknotes, navigator)
                        is Route.Auth -> AuthScreen(teknotes, route, navigator)
                        is Route.Legal -> LegalScreen(route.privacy, navigator)
                        Route.Tutorial -> IntroScreen(teknotes, replay = true, onDone = navigator::back)
                        is Route.Code -> CodeScreen(teknotes, route.mode, navigator)
                    }
                }
            }
        }
    }
}

/** The tabs: the screen follows the finger from one to the next, under a floating bar. */
@Composable
private fun Tabs(teknotes: Teknotes, pager: PagerState, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    val firstNote = { navigator.open(Route.Night(teknotes.notebook.checkIn(null).id)) }
    val bottom = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 96.dp)
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> NotebookScreen(teknotes, bottom, navigator, firstNote)
                1 -> DirectoryScreen(teknotes, bottom, navigator, firstNote)
                else -> ProfileScreen(teknotes, bottom, navigator)
            }
        }
        val paper = MaterialTheme.colorScheme.background
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(14.dp))
                .background(paper, RoundedCornerShape(14.dp))
                .border(1.5.dp, Ink, RoundedCornerShape(14.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TabLabels.forEachIndexed { index, label ->
                val on = pager.currentPage == index
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp)
                        .background(if (on) Ink else Color.Transparent, RoundedCornerShape(9.dp))
                        .clickable { scope.launch { pager.animateScrollToPage(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = textStyle(14.sp, 700), color = if (on) paper else Ink)
                }
            }
        }
    }
}
