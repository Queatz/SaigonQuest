package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window
import lib.BinaryUtils
import lib.Qr
import lib.Vector3
import main.theme.Styles
import main.components.generatePrintImage
import main.components.Translated
import main.components.translated
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * A dialog that displays a QR code of the current location.
 * 
 * @param onClose Callback when the dialog is closed
 * @param onPrint Callback when the print button is clicked, with the generated image URL
 * @param playerPosition The current position of the player in the world (optional)
 * @param worldTitle The title of the current world
 * @param worldSubtitle The subtitle of the current world
 * @param worldDescription The description of the current world (optional)
 */
@Composable
fun QrDialog(
    onClose: () -> Unit, 
    onPrint: (String) -> Unit,
    playerPosition: Vector3? = null,
    worldTitle: String = translated("Saigon.Quest"),
    worldSubtitle: String = translated("Virtual World"),
    worldDescription: String = ""
) {
    // Get the current URL and append player position if available
    val currentUrl = remember(playerPosition) { 
        val baseUrl = window.location.href
        
        // If player position is provided, append it to the URL
        if (playerPosition != null) {
            // Truncate coordinates to 2 decimal places
            val x = (playerPosition.x * 100).toInt() / 100.0
            val y = (playerPosition.y * 100).toInt() / 100.0
            val z = (playerPosition.z * 100).toInt() / 100.0
            
            // Log for testing
            console.log("Adding player position to QR URL: $x,$y,$z")
            
            // Append position to URL
            "$baseUrl?at=$x,$y,$z"
        } else {
            console.log("No player position available for QR URL")
            baseUrl
        }
    }
    
    // Generate QR code as data URL
    val qrDataUrl = remember(currentUrl) {
        // Generate QR code as GIF
        val qrData = Qr.createQR(currentUrl, "gif")
        
        // Convert Uint8Array to data URL
        BinaryUtils.uint8ArrayToDataUrl(qrData, "image/gif")
    }
    
    // Use the BaseDialog component with print button in header
    BaseDialog(
        onClose = onClose,
        title = translated("Share this world"),
        showCloseButton = true, // Show the standard close button
        headerActions = {
            // Print button in header
            Button(
                attrs = {
                    classes(Styles.baseDialogCloseButton)
                    style {
                        fontSize(16.px)
                        padding(4.px, 8.px)
                    }
                    onClick { 
                        // Generate the print image and show dialog
                        generatePrintImage(
                            url = currentUrl,
                            worldTitle = worldTitle,
                            worldSubtitle = worldSubtitle,
                            worldDescription = worldDescription,
                            onComplete = { imageUrl ->
                                onPrint(imageUrl)
                                onClose() // Close the QR dialog when print is clicked
                            }
                        )
                    }
                }
            ) {
                Translated("🖨️ Print")
            }
        },
        content = {
            
            // QR code container
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        property("gap", "16px")
                    }
                }
            ) {
                // QR code container
                Div(
                    attrs = {
                        style {
                            backgroundColor(Color.white)
                            padding(16.px)
                            property("border-radius", "8px")
                            width(100.percent)
                            property("max-width", "300px")
                            property("aspect-ratio", "1/1")
                            display(DisplayStyle.Flex)
                            justifyContent(JustifyContent.Center)
                            alignItems(AlignItems.Center)
                        }
                    }
                ) {
                    // Display QR code as image with pixelated rendering
                    Img(
                        src = qrDataUrl,
                        attrs = {
                            style {
                                width(100.percent)
                                height(100.percent)
                                property("object-fit", "contain")
                                // Add pixelated image rendering (linear interpolation disabled)
                                property("image-rendering", "pixelated")
                                property("image-rendering", "-moz-crisp-edges")
                                property("image-rendering", "crisp-edges")
                            }
                        }
                    )
                }
                
                // URL text
                P(
                    attrs = {
                        style {
                            color(rgba(255, 255, 255, 0.7))
                            property("word-break", "break-all")
                            property("text-align", "center")
                            margin(0.px)
                        }
                    }
                ) {
                    Text(currentUrl)
                }
            }
        },
        actions = {
            // Close button as an action
            DialogButton(
                onClick = onClose,
                isPrimary = false
            ) {
                Translated("Close")
            }
        }
    )
}
