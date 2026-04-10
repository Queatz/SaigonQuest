package main.components.worlddocs

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import main.api.DocApi
import main.components.Md
import models.Doc
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Text

@Composable
fun PrintDocPage(docId: String) {
    var doc by remember { mutableStateOf<Doc?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(docId) {
        try {
            loading = true
            doc = DocApi.getDoc(docId)
        } catch (t: Throwable) {
            error = t.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(doc) {
        if (doc != null) {
            delay(500)
            window.print()
        }
    }

    DisposableEffect(Unit) {
        window.document.body?.classList?.add("printable")
        window.document.documentElement?.classList?.add("printable")
        onDispose {
            window.document.body?.classList?.remove("printable")
            window.document.documentElement?.classList?.remove("printable")
        }
    }

    Div(attrs = {
        style {
            padding(2.cssRem)
            maxWidth(800.px)
            property("margin", "0 auto")
            backgroundColor(Color.white)
            color(Color.black)
            minHeight(100.vh)
            property("font-family", "sans-serif")
        }
    }) {
        when {
            loading -> Div { Text("Loading...") }
            error != null -> Div { Text("Error: $error") }
            doc != null -> {
                H1 { Text(doc!!.title) }
                Md(
                    content = doc!!.content,
                    attrs = {
                        style {
                            color(Color.black)
                        }
                    }
                )
            }
        }
    }
}
