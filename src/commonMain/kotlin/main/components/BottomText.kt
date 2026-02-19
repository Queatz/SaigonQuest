package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Ul
import org.jetbrains.compose.web.dom.Li
import main.components.Translated

/**
 * A component that displays the copyright text at the bottom left of the screen.
 * This text is positioned absolutely at the bottom left corner with a small font size.
 */
@Composable
fun BottomText() {
    var showAbout by remember { mutableStateOf(false) }

    Div(
        attrs = {
            classes(Styles.bottomText)
            onClick { showAbout = true }
        }
    ) {
        Translated("© 2026 Saigon.Quest v0.1")
    }

    if (showAbout) {
        BaseDialog(
            onClose = { showAbout = false },
            title = translated("About"),
            maxWidth = "560px",
            content = {
                Div(
                    attrs = {
                        classes(Styles.aboutDialogContent)
                    }
                ) {
                    P(attrs = { classes(Styles.aboutParagraph) }) {
                        Translated("Saigon.Quest allows anyone to create mini worlds that can be virtual versions of real locations, or simply extra virtual rooms for your house or coffeeshop.")
                    }
                    H4(attrs = { classes(Styles.aboutHeading) }) {
                        Translated("Creating your own world")
                    }
                    Ul(attrs = { classes(Styles.aboutList) }) {
                        Li(attrs = { classes(Styles.aboutListItem) }) { Translated("Hosted worlds: Simply navigate to the URL of your choice") }
                        Li(attrs = { classes(Styles.aboutListItem) }) { Translated("Self-hosted worlds: Please contact Zalo 0708739214") }
                    }
                    H4(attrs = { classes(Styles.aboutHeading) }) {
                        Translated("Credits")
                    }
                    P(attrs = { classes(Styles.aboutParagraph) }) {
                        Translated("Saigon.Quest is created by Jacob Ferrero")
                    }
                }
            },
            actions = {
                DialogButton(
                    onClick = { showAbout = false },
                    isPrimary = true,
                ) { Translated("Close") }
            },
        )
    }
}
