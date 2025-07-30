package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * An inventory box.
 */
@Composable
fun InventoryBox(onClick: () -> Unit, content: String = "") {
    Div(
        attrs = {
            classes(Styles.inventoryBox)
            onClick { onClick() }
        }
    ) {
        Text(content)
    }
}
