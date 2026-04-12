package main.components

import androidx.compose.runtime.Composable
import main.components.common.Spinner
import main.theme.Styles
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.w3c.dom.HTMLDivElement

enum class DialogButtonStyle {
    Primary,
    Secondary,
    Alt,
    Danger,
    Good
}

/**
 * A reusable dialog button component with consistent styling.
 *
 * @param onClick Callback when the button is clicked
 * @param style The style of the button (default: Secondary)
 * @param isDisabled Whether this button is disabled (default: false)
 * @param small Whether this button is small (default: false)
 * @param color The color of the button (default: null)
 * @param attrs Additional attributes for the button container
 * @param content The content of the button
 */
@Composable
fun DialogButton(
    onClick: () -> Unit,
    style: DialogButtonStyle = DialogButtonStyle.Secondary,
    isDisabled: Boolean = false,
    small: Boolean = false,
    isLoading: Boolean = false,
    color: String? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    content: @Composable () -> Unit
) {
    val borderImage = when (style) {
        DialogButtonStyle.Primary -> "/raw/data/ui/borders/button.png"
        DialogButtonStyle.Alt -> "/raw/data/ui/borders/button_alt.png"
        DialogButtonStyle.Danger -> "/raw/data/ui/borders/button_danger.png"
        DialogButtonStyle.Secondary -> "/raw/data/ui/borders/button_secondary.png"
        DialogButtonStyle.Good -> "/raw/data/ui/borders/button_good.png"
    }
    val backgroundImage = when (style) {
        DialogButtonStyle.Primary -> "/raw/data/ui/backgrounds/button.png"
        DialogButtonStyle.Alt -> "/raw/data/ui/backgrounds/button_alt.png"
        DialogButtonStyle.Danger -> "/raw/data/ui/backgrounds/button_danger.png"
        DialogButtonStyle.Secondary -> "/raw/data/ui/backgrounds/button_secondary.png"
        DialogButtonStyle.Good -> "/raw/data/ui/backgrounds/button_good.png"
    }

    FramedBorder(
        borderImage = borderImage,
        backgroundImage = backgroundImage,
        attrs = {
            when (style) {
                DialogButtonStyle.Primary -> classes(Styles.baseDialogButtonPrimary)
                DialogButtonStyle.Danger -> classes(Styles.baseDialogButtonDangerPrimary)
                else -> classes(Styles.baseDialogButtonSecondary)
            }

            val effectivelyDisabled = isDisabled || isLoading

            // Ensure the framed border stays visible despite button class defaults
            style {
                if (small) {
                    padding(2.px, 8.px)
                    property("border-width", "4px")
                    property("border-image-width", "4px")
                } else {
                    padding(6.px, 18.px)
                    property("border-width", "6px")
                    property("border-image-width", "6px")
                }
                property("border-style", "solid")
                property("border-radius", "0")
                property("box-shadow", "none")
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                property("cursor", if (effectivelyDisabled) "default" else "pointer")
            }

            if (color != null) {
                style {
                    backgroundColor(Color(color))
                }
            }

            if (effectivelyDisabled) {
                style {
                    opacity(0.5)
                    property("pointer-events", "none")
                }
            } else {
                onClick { onClick() }
            }

            attrs?.invoke(this)
        }
    ) {
        if (isLoading) {
            Spinner(
                spinnerClass = Styles.smallLoadingSpinner,
            )
        } else {
            content()
        }
    }
}
