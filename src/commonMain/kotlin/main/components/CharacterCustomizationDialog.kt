package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.player.CharacterRenderer
import main.theme.Styles
import models.User
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.attributes.InputType

/**
 * Dialog for customizing player character appearance
 */
@Composable
fun CharacterCustomizationDialog(
    user: User,
    onClose: () -> Unit,
    onSave: (User) -> Unit
) {
    // Create local copies of customization properties to avoid modifying the user directly
    var bodyType by remember { mutableStateOf(user.bodyType) }
    var skinTone by remember { mutableStateOf(user.skinTone) }
    var eyeColor by remember { mutableStateOf(user.eyeColor) }
    var hairStyle by remember { mutableStateOf(user.hairStyle) }

    // Function to save customizations
    val saveCustomizations = {
        // Update user with new customization values
        user.bodyType = bodyType
        user.skinTone = skinTone
        user.eyeColor = eyeColor
        user.hairStyle = hairStyle
        
        // Call the onSave callback with the updated user
        onSave(user)
    }

    // Use the BaseDialog component
    BaseDialog(
        onClose = onClose,
        title = translated("Customize character"),
        content = {
            // Character renderer
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        justifyContent(JustifyContent.Center)
                    }
                }
            ) {
                // Add the character renderer component
                CharacterRenderer(
                    bodyType = bodyType,
                    skinTone = skinTone,
                    eyeColor = eyeColor,
                    hairStyle = hairStyle
                )
            }

            // Customization options
            SettingsSection {
                if (false) {
                    // Body Type
                    DialogSelect(
                        label = "Body",
                        options = listOf("Skinny", "Normal", "Fat").map { it to it },
                        value = bodyType,
                        onInput = { bodyType = it },
                        containerAttrs = {
                            style {
                                marginTop(16.px)
                            }
                        },
                    )
                }

                // Skin Tone
                Div(
                    attrs = {
                        style {
                            marginTop(16.px)
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            classes(Styles.settingsSectionLabel)
                        }
                    ) {
                        Translated("Skin Tone")
                    }
                    
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                alignItems(AlignItems.Center)
                                marginTop(8.px)
                            }
                        }
                    ) {
                        RangeInput(
                            value = skinTone,
                            min = 0f,
                            max = 1f,
                            step = 0.01f,
                            attrs = {
                                style {
                                    width(200.px)
                                }
                                onInput { event ->
                                    try {
                                        val value = event.target.value
                                        skinTone = value.toFloat()
                                    } catch (e: Throwable) {
                                        // Fallback to default if parsing fails
                                        skinTone = 0.5f
                                    }
                                }
                            }
                        )
                    }
                }

                // Eye Color
                DialogSelect(
                    label = "Eyes",
                    options = listOf("Black", "Blue", "Brown", "Green", "Gray").map { it to it },
                    value = eyeColor,
                    onInput = { eyeColor = it },
                    containerAttrs = {
                        style {
                            marginTop(16.px)
                        }
                    },
                )

                // Hair Style
                DialogSelect(
                    label = "Hair",
                    options = listOf("None", "Short", "Medium", "Long").map { 
                        (if (it == "None") "" else it) to it 
                    },
                    value = hairStyle,
                    onInput = { hairStyle = it },
                    containerAttrs = {
                        style {
                            marginTop(16.px)
                        }
                    },
                )
            }
        },
        actions = {
            // Cancel button
            DialogButton(
                onClick = onClose,
                style = DialogButtonStyle.Secondary
            ) {
                Translated("Cancel")
            }
            
            // Save button
            DialogButton(
                onClick = {
                    saveCustomizations()
                    onClose()
                },
                style = DialogButtonStyle.Primary
            ) {
                Translated("Save")
            }
        }
    )
}
