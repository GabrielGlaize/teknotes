package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gabrielglaize.teknotes.Departments
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.DirectoryRepository
import dev.gabrielglaize.teknotes.Style
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun dates(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.FRENCH)

private val shortDate = dates("d MMM yyyy")
private val longDate = dates("EEEE d MMMM")
private val monthDate = dates("MMMM yyyy")
private val dayDate = dates("d")
private val weekdayDate = dates("EEE")

fun formatNightDate(isoDate: String): String = LocalDate.parse(isoDate).format(shortDate)

fun nightTitle(isoDate: String): String = LocalDate.parse(isoDate).format(longDate).replaceFirstChar { it.uppercase() }

fun monthTitle(isoDate: String): String = LocalDate.parse(isoDate).format(monthDate).replaceFirstChar { it.uppercase() }

fun dayNumber(isoDate: String): String = LocalDate.parse(isoDate).format(dayDate)

fun weekdayName(isoDate: String): String = LocalDate.parse(isoDate).format(weekdayDate)

/** Closes the keyboard on a tap anywhere outside a text field; what's underneath still gets the tap. */
fun Modifier.closesKeyboard(): Modifier = composed {
    val focus = LocalFocusManager.current
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) focus.clearFocus()
        }
    }
}

val Mute: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Line: Color @Composable get() = MaterialTheme.colorScheme.outline
val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val Card: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(
    navigator: Navigator,
    title: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = textStyle(16.sp, 700)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            content = content,
        )
    }
}

@Composable
fun BigTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = textStyle(38.sp, 800).copy(letterSpacing = (-1).sp, lineHeight = 42.sp))
}

/** Opens a part of a screen, like a heading on a printed programme. */
@Composable
fun SectionHead(text: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = textStyle(15.sp, 700))
            trailing()
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Line))
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = textStyle(14.sp), color = Mute)
}

@Composable
fun ActionButton(title: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val base = modifier.fillMaxWidth().heightIn(min = 52.dp)
    val shape = if (primary) base.background(if (enabled) Accent else Accent.copy(alpha = 0.35f)) else base.border(1.dp, if (enabled) Ink else Line)
    Box(shape.clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            title,
            style = textStyle(16.sp, 700),
            color = if (primary) MaterialTheme.colorScheme.onPrimary else if (enabled) Ink else Mute,
        )
    }
}

/** A word in the signal colour, for what is possible but not the main thing. */
@Composable
fun TextAction(title: String, modifier: Modifier = Modifier, color: Color = Accent, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(title, style = textStyle(15.sp, 600), color = color)
    }
}

/** A line of a list that does something or leads somewhere. */
@Composable
fun RowLine(title: String, detail: String? = null, color: Color = Ink, chevron: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = textStyle(16.sp, 500), color = color)
            detail?.let { Text(it, Modifier.padding(start = 8.dp), style = textStyle(14.sp), color = Mute, maxLines = 1) }
            if (chevron) Text("  ›", style = textStyle(18.sp, 500), color = Mute)
        }
        Hairline()
    }
}

/** Text input on a rule, its name above it. */
@Composable
fun LineField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secure: Boolean = false,
    singleLine: Boolean = true,
) {
    var revealed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = textStyle(13.sp, 600), color = Mute)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).heightIn(min = 36.dp).padding(vertical = 8.dp)
                    .semantics { contentDescription = label }
                    .onFocusChanged { focused = it.isFocused },
                textStyle = textStyle(17.sp).copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                visualTransformation = if (secure && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            )
            if (secure) {
                TextAction(if (revealed) "Masquer" else "Afficher", color = Mute) { revealed = !revealed }
            }
        }
        Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(if (focused) Accent else Line))
    }
}

/** Search input, with what to look for written in it. */
@Composable
fun SearchLine(prompt: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).background(Line.copy(alpha = 0.45f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Mute, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(prompt, style = textStyle(16.sp), color = Mute, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = prompt },
                textStyle = textStyle(16.sp).copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Effacer", tint = Mute, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A set's note: five big digits, the one chosen in ink. */
@Composable
fun NumberRate(level: Int, onSet: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        (1..5).forEach { digit ->
            Box(
                Modifier.weight(1f).height(48.dp)
                    .clickable { onSet(digit) }
                    .semantics { contentDescription = "Note $digit sur 5" + if (digit == level) ", choisie" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Text("$digit", style = textStyle(34.sp, 800), color = if (digit == level) Ink else Line)
            }
        }
    }
}

fun formatScore(value: Double): String = String.format(Locale.FRENCH, "%.1f", value)

/** A score as a big number, "4,5", or a dash when nothing is rated. */
@Composable
fun ScoreText(value: Double?, size: Int = 26, modifier: Modifier = Modifier) {
    Text(
        value?.let(::formatScore) ?: "–",
        modifier,
        style = textStyle(size.sp, 800).copy(letterSpacing = (-0.5).sp),
        color = if (value == null) Mute else Ink,
    )
}

/** Between two views of the same screen. */
@Composable
fun <T> Segments(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(9.dp)).padding(3.dp)) {
        items.forEach { item ->
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp)
                    .background(if (item == selected) MaterialTheme.colorScheme.background else Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(item), style = textStyle(14.sp, 600), color = if (item == selected) Ink else Mute)
            }
        }
    }
}

/** A small rounded choice, filled in ink when on. */
@Composable
fun Chip(text: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 34.dp).background(if (on) Ink else Card, RoundedCornerShape(99.dp)).clip(RoundedCornerShape(99.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), style = textStyle(14.sp, 600), color = if (on) MaterialTheme.colorScheme.background else Ink)
    }
}

/** The hint shown while there is nothing noted yet. */
@Composable
fun TipCard(text: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(8.dp)).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
        Text("Astuce", style = textStyle(15.sp, 800))
        Text(text, Modifier.padding(top = 4.dp), style = textStyle(15.sp))
        TextAction("Créer une note", onClick = onClick)
    }
}

/** Choice among a few words: the one selected is underlined in the signal colour. */
@Composable
fun <T> WordTabs(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, size: Int = 20) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        items.forEach { item ->
            Column(Modifier.width(IntrinsicSize.Max).heightIn(min = 44.dp).clickable { onSelect(item) }, verticalArrangement = Arrangement.Center) {
                val text = label(item)
                Text(text, style = textStyle(size.sp, 700), color = if (item == selected) Ink else Mute)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (item == selected) Accent else Color.Transparent))
            }
        }
    }
}

/** A short word in a thin frame: a style, a filter. */
@Composable
fun Tag(text: String, selected: Boolean = false, trailing: String? = null, onClick: (() -> Unit)? = null) {
    val base = Modifier.heightIn(min = 32.dp).background(if (selected) Ink else Color.Transparent).border(1.dp, if (selected) Ink else Line)
    Box(if (onClick != null) base.clickable(onClick = onClick) else base, contentAlignment = Alignment.Center) {
        Text(
            if (trailing != null) "$text  $trailing" else text,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = textStyle(14.sp, 600),
            color = if (selected) MaterialTheme.colorScheme.background else Ink,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Tags(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** "Rouille (Caisson Noir)": the artist stands out, the soundsystem follows. */
@Composable
fun SetName(artist: String?, soundsystem: String?, fallback: String = "", size: Int = 17, modifier: Modifier = Modifier) {
    val mute = Mute
    Text(
        buildAnnotatedString {
            if (artist != null && soundsystem != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(artist) }
                withStyle(SpanStyle(color = mute)) { append(" ($soundsystem)") }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(artist ?: soundsystem ?: fallback) }
            }
        },
        modifier,
        style = textStyle(size.sp).copy(color = Ink),
    )
}

@Composable
fun HitRow(hit: DirectoryHit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hit.name, style = textStyle(16.sp, 600))
                hit.matchedAlias?.let { Text("aussi appelé $it", style = textStyle(13.sp), color = Mute) }
            }
            Text(if (hit.kind == DirectoryHit.Kind.SOUNDSYSTEM) "soundsystem" else "artiste", style = textStyle(13.sp), color = Mute)
        }
        Hairline()
    }
}

/** A full-screen choice on top of the current screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceDialog(title: String, closeLabel: String = "Annuler", onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).closesKeyboard().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = textStyle(16.sp, 700))
                TextButton(onClick = onClose) { Text(closeLabel) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), content = content)
        }
    }
}

/** Picks one thing in a long list: type to narrow it down, touch a line to choose. */
@Composable
fun <T> SearchPicker(
    title: String,
    prompt: String,
    items: (String) -> List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onClose: () -> Unit,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    ChoiceDialog(title, onClose = onClose) {
        SearchLine(prompt, query, { query = it }, Modifier.padding(vertical = 8.dp))
        if (clearLabel != null && onClear != null) {
            RowLine(clearLabel, color = Mute) {
                onClear()
                onClose()
            }
        }
        items(query).forEach { item ->
            RowLine(label(item)) {
                onPick(item)
                onClose()
            }
        }
    }
}

@Composable
fun DepartmentPicker(title: String, onPick: (String?) -> Unit, onClose: () -> Unit) {
    SearchPicker(
        title = title,
        prompt = "Nom ou numéro",
        items = { Departments.search(it) },
        label = { it.label },
        onPick = { onPick(it.code) },
        onClose = onClose,
        clearLabel = "Non précisé",
        onClear = { onPick(null) },
    )
}

/** Styles to choose from: the most used first, any new one in a word. [filtering]: one at a time, only what the directory has. */
@Composable
fun StylePicker(directory: DirectoryRepository, selected: Set<Style>, onChange: (Set<Style>) -> Unit, onClose: () -> Unit, filtering: Boolean = false) {
    var query by remember { mutableStateOf("") }
    ChoiceDialog("Styles", closeLabel = "OK", onClose = onClose) {
        SearchLine(if (filtering) "Chercher un style" else "Chercher ou ajouter un style", query, { query = it }, Modifier.padding(vertical = 8.dp))
        val choices = directory.styleChoices(query, usedOnly = filtering)
        // A style just created isn't in the directory yet.
        val shown = (if (query.isBlank()) selected.filter { it !in choices }.sortedBy { it.label } else emptyList()) + choices
        shown.forEach { style ->
            val on = style in selected
            Column(
                Modifier.fillMaxWidth().clickable {
                    if (filtering) {
                        onChange(if (on) emptySet() else setOf(style))
                        onClose()
                    } else {
                        onChange(if (on) selected - style else selected + style)
                    }
                },
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(style.label, Modifier.weight(1f), style = textStyle(16.sp, if (on) 700 else 400))
                    if (on) Icon(Icons.Filled.Check, contentDescription = "Choisi", tint = Accent, modifier = Modifier.size(18.dp))
                }
                Hairline()
            }
        }
        val creatable = if (filtering) null else directory.styleNamed(query)?.takeIf { it !in shown }
        if (creatable != null) {
            RowLine("Ajouter « ${creatable.label} »", color = Accent) {
                onChange(selected + creatable)
                query = ""
            }
        } else if (shown.isEmpty()) {
            Note("Aucun style ne correspond.", Modifier.padding(top = 12.dp))
        }
    }
}
