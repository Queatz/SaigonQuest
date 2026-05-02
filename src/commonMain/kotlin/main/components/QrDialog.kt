package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import lib.BinaryUtils
import lib.Qr
import lib.Vector3
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
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
    worldSubtitle: String = translated("Local Worlds"),
    worldDescription: String = ""
) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    // Get the current URL and append player position if available
    val currentUrl = remember(playerPosition) { 
        val baseUrl = window.location.origin + router.currentPath.path
        
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
            DialogButton(
                onClick = { 
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
                },
                style = DialogButtonStyle.Secondary,
                small = true
            ) {
                Translated("Print")
            }
        },
        content = {
            Div(
                attrs = { classes(Styles.qrContainer) }
            ) {
                Div(
                    attrs = { classes(Styles.qrSquare) }
                ) {
                    QrImage(src = qrDataUrl)
                }

                P(
                    attrs = { classes(Styles.qrUrlText) }
                ) { Text(currentUrl) }
            }
        },
        actions = {
            // Copy link button
            DialogButton(
                onClick = {
                    scope.launch {
                        try {
                            window.navigator.clipboard.writeText(currentUrl).await()
                            isCopied = true
                            delay(2.seconds)
                            isCopied = false
                        } catch (t: Throwable) {
                            console.error("Failed to copy to clipboard", t)
                        }
                    }
                },
                style = DialogButtonStyle.Primary
            ) {
                if (isCopied) {
                    Translated("Copied!")
                } else {
                    Translated("Copy link")
                }
            }

            // Close button as an action
            DialogButton(
                onClick = onClose,
                style = DialogButtonStyle.Secondary
            ) {
                Translated("Close")
            }
        }
    )
}
