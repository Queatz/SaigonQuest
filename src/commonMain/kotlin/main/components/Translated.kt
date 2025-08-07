package main.components

import Translations.language
import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.jetbrains.compose.web.dom.Text

@Composable
fun Translated(text: String) {
    language.collectAsState().value
    Text(str(text))
}

@Composable
fun translated(text: String): String {
    language.collectAsState().value
    return str(text)
}
