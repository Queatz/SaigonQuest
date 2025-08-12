package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div

@Composable
fun ByePage() {
    Div(
        attrs = {
            classes(Styles.byePageContainer)
        }
    ) {
        Div {
            Translated("Bye")
        }
        Div {
            Translated("Please close this window")
        }
    }
}
