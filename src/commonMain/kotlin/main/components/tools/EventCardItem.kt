package main.components.tools

import androidx.compose.runtime.Composable
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import models.EventCard
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import main.components.DialogInput
import main.components.Paper

@Composable
fun EventCardItem(
    card: EventCard,
    darkMode: Boolean,
    generatingCards: Set<String>,
    onUpdateText: (String) -> Unit,
    onGenerateImage: () -> Unit,
    onRemove: () -> Unit
) {
    Paper(attrs = {
        style {
            display(DisplayStyle.Flex); gap(16.px); padding(16.px)
            alignItems(AlignItems.Center)
            color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface)
        }
    }) {
        card.image?.let {
            Img(src = it, attrs = { style { width(80.px); height(80.px); property("object-fit", "cover"); borderRadius(8.px); } })
        }
        Div(attrs = { style { display(DisplayStyle.Flex); flexDirection(FlexDirection.Column); flexGrow(1); gap(8.px) } }) {
            DialogInput(
                value = card.text,
                onInput = onUpdateText
            )
            DialogButton(
                small = true,
                isLoading = generatingCards.contains(card.id),
                onClick = onGenerateImage
            ) {
                Translated("Generate card")
            }
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
