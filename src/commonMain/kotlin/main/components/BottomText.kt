package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Ul
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Text
import main.components.Translated
import main.components.world.WorldLoader
import kotlin.math.roundToInt

/**
 * A component that displays the copyright text at the bottom left of the screen.
 * This text is positioned absolutely at the bottom left corner with a small font size.
 */
@Composable
fun BottomText() {
    var showAbout by remember { mutableStateOf(false) }
    var fps by remember { mutableStateOf(0) }
    var visibleMeshes by remember { mutableStateOf(0) }
    var totalMeshes by remember { mutableStateOf(0) }

    val debugFps = false

    LaunchedEffect(Unit) {
        while (true) {
            val scene = SceneManager.getScene()
            if (scene != null) {
                fps = scene.getEngine().getFps().roundToInt()
            }


            if (debugFps) {
                val perfEngine = WorldLoader.getCurrentPerformanceEngine()
                if (perfEngine != null) {
                    visibleMeshes = perfEngine.getVisibleCount()
                    totalMeshes = perfEngine.getTotalTrackedCount()
                } else {
                    visibleMeshes = 0
                    totalMeshes = 0
                }
            }

            delay(500)
        }
    }

    Div(
        attrs = {
            classes(Styles.bottomText)
            onClick { showAbout = true }
        }
    ) {
        val baseText = translated("© 2026 Saigon.Quest v0.1")
        val fpsText = if (fps > 0) " • $fps FPS" else ""
        val meshText = if (debugFps && totalMeshes > 0) " • ${translated("Visible")}: $visibleMeshes/$totalMeshes" else ""
        
        Text("$baseText$fpsText$meshText")
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
                    style = DialogButtonStyle.Primary,
                ) { Translated("Close") }
            },
        )
    }
}
