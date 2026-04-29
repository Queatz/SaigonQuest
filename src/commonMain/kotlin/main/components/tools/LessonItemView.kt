package main.components.tools

import androidx.compose.runtime.Composable
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import models.LessonItem
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import main.components.DialogInput
import main.components.Paper

@Composable
fun LessonItemView(
    item: LessonItem,
    darkMode: Boolean,
    generatingItems: Set<String>,
    onUpdateName: (String) -> Unit,
    onGenerateImage: () -> Unit,
    onRemove: () -> Unit
) {
    Paper(attrs = {
        style {
            width(120.px); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column)
            gap(8.px); padding(8.px)
            color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface)
        }
    }) {
        item.image?.let {
            Img(src = it, attrs = { style { width(100.percent); backgroundColor(Color.white); borderRadius(8.px); } })
        }
        DialogInput(
            value = item.name,
            onInput = onUpdateName
        )
        DialogButton(
            small = true,
            isLoading = generatingItems.contains(item.id),
            onClick = onGenerateImage
        ) {
            Translated("Generate item")
        }
        DialogButton(
            small = true,
            style = DialogButtonStyle.Danger,
            onClick = onRemove
        ) {
            Translated("Remove")
        }
    }
}
