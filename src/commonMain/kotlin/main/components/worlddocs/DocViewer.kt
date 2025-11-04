package main.components.worlddocs

import androidx.compose.runtime.Composable
import main.components.Md
import models.Doc
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text

@Composable
fun DocViewer(
    doc: Doc,
) {
    Div {
        H3 { Text(doc.title) }
        Md(content = doc.content)
    }
}
