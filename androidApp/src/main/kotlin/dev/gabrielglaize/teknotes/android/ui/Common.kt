package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gabrielglaize.teknotes.DirectoryHit
import dev.gabrielglaize.teknotes.Style
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRENCH)

fun formatNightDate(isoDate: String): String = LocalDate.parse(isoDate).format(dateFormat)

@Composable
fun ScreenTitle(label: String, title: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Text(label.uppercase(), style = flyerLabel().copy(color = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(8.dp))
        Text(title.uppercase(), style = chromeTitle())
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp), style = flyerLabel())
}

@Composable
fun HitRow(hit: DirectoryHit, onClick: () -> Unit) {
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

/** Horizontal row of style chips; [selected] are highlighted, a tap toggles one. */
@Composable
fun StyleChips(selected: Set<Style>, onToggle: (Style) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Style.entries.forEach { style ->
            FilterChip(selected = style in selected, onClick = { onToggle(style) }, label = { Text(style.label) })
        }
    }
}
