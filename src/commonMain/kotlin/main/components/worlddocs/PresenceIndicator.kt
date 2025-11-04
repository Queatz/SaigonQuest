package main.components.worlddocs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.WorldDocsStyles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Presence indicator with compact colored dots and user IDs.
 * Keeps single @Composable per file and uses theme styles only.
 */
@Composable
fun PresenceIndicator(
    users: Set<String>,
) {
    val maxShown = 5
    val list = users.take(maxShown)
    var expanded by remember { mutableStateOf(false) }

    Div(attrs = { classes(WorldDocsStyles.presenceRow) }) {
        // Colored dots for each shown user
        list.forEach { uid ->
            val dotClass = when (uid.hashCode().absoluteValue() % 6) {
                0 -> WorldDocsStyles.presenceDot0
                1 -> WorldDocsStyles.presenceDot1
                2 -> WorldDocsStyles.presenceDot2
                3 -> WorldDocsStyles.presenceDot3
                4 -> WorldDocsStyles.presenceDot4
                else -> WorldDocsStyles.presenceDot5
            }
            Div(attrs = { classes(dotClass) }) {}
        }
        // Compact list of (possibly shortened) IDs
        if (!expanded) {
            Span(attrs = { classes(WorldDocsStyles.presenceUser) }) { Text(list.joinToString(", ") { shortId(it) }) }
            if (users.size > maxShown) {
                Span { Text(" …") }
            }
        } else {
            Span(attrs = { classes(WorldDocsStyles.presenceUser) }) { Text(users.joinToString(", ")) }
        }
    }
}

private fun Int.absoluteValue(): Int = if (this < 0) -this else this
private fun shortId(id: String): String = if (id.length <= 6) id else id.take(3) + "…" + id.takeLast(3)
