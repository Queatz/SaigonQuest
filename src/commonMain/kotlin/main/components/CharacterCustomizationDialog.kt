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
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.RangeInput

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
        title = "Customize Character",
        content = {
            // Character renderer
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                        display(DisplayStyle.Companion.Flex)
                        alignItems(AlignItems.Companion.Center)
                        justifyContent(JustifyContent.Companion.Center)
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
            Div(
                attrs = {
                    classes(Styles.settingsSectionContainer)
                }
            ) {
                // Body Type
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
                        Text("Body")
                    }
                    
                    Select(
                        attrs = {
                            classes(Styles.inputField)
                            attr("value", bodyType)
                            onChange { event -> 
                                event.target.value?.let { bodyType = it }
                            }
                        }
                    ) {
                        listOf("Skinny", "Normal", "Fat").forEach { option ->
                            Option(
                                value = option,
                                attrs = {
                                    if (option == bodyType) {
                                        attr("selected", "true")
                                    }
                                }
                            ) {
                                Text(option)
                            }
                        }
                    }
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
                        Text("Skin Tone")
                    }
                    
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Companion.Flex)
                                alignItems(AlignItems.Companion.Center)
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
                                    } catch (e: Exception) {
                                        // Fallback to default if parsing fails
                                        skinTone = 0.5f
                                    }
                                }
                            }
                        )
                        
                        Span(
                            attrs = {
                                style {
                                    marginLeft(8.px)
                                }
                            }
                        ) {
                            // Format to 2 decimal places
                            val formattedValue = (skinTone * 100).toInt() / 100.0
                            Text("$formattedValue")
                        }
                    }
                }

                // Eye Color
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
                        Text("Eyes")
                    }
                    
                    Select(
                        attrs = {
                            classes(Styles.inputField)
                            attr("value", eyeColor)
                            onChange { event -> 
                                event.target.value?.let { eyeColor = it }
                            }
                        }
                    ) {
                        listOf("Black", "Blue", "Brown", "Green", "Gray").forEach { option ->
                            Option(
                                value = option,
                                attrs = {
                                    if (option == eyeColor) {
                                        attr("selected", "true")
                                    }
                                }
                            ) {
                                Text(option)
                            }
                        }
                    }
                }

                // Hair Style
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
                        Text("Hair")
                    }
                    
                    Select(
                        attrs = {
                            classes(Styles.inputField)
                            attr("value", hairStyle)
                            onChange { event -> 
                                event.target.value?.let { hairStyle = it }
                            }
                        }
                    ) {
                        listOf("Short", "Medium", "Long").forEach { option ->
                            Option(
                                value = option,
                                attrs = {
                                    if (option == hairStyle) {
                                        attr("selected", "true")
                                    }
                                }
                            ) {
                                Text(option)
                            }
                        }
                    }
                }
            }
        },
        actions = {
            // Cancel button
            DialogButton(
                onClick = onClose,
                isPrimary = false
            ) {
                Text("Cancel")
            }
            
            // Save button
            DialogButton(
                onClick = {
                    saveCustomizations()
                    onClose()
                },
                isPrimary = true
            ) {
                Text("Save")
            }
        }
    )
}
