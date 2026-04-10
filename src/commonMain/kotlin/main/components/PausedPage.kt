package main.components

import androidx.compose.runtime.Composable
import app.softwork.routingcompose.Router
import main.PausedState
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun PausedPage() {
    val router = Router.current

    Div(attrs = { classes(Styles.byePageContainer) }) {
        Div { Translated("Paused.") }
        Div {
            val worldName = PausedState.lastWorldName.value.ifBlank { null }
            val buttonText = if (worldName == null) translated("Resume") else "${translated("Resume to")} \"$worldName\""
            DialogButton(
                style = DialogButtonStyle.Primary,
                onClick = {
                    val url = PausedState.lastWorldUrl.value
                    router.navigate("/${url}")
                }
            ) { Text(buttonText) }
        }
    }
}
