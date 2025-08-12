package main.components

import androidx.compose.runtime.Composable
import kotlinx.browser.document
import lib.BinaryUtils
import lib.Qr
import lib.Tools
import lib.ScreenshotOptions
import main.components.SceneManager
import main.theme.Styles
import main.components.Translated
import main.components.translated
import Translations.str
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import kotlin.math.max

/**
 * A dialog that displays the printed QR code with scene.
 */
@Composable
fun PrintDialog(imageUrl: String, onClose: () -> Unit) {
    BaseDialog(
        onClose = onClose,
        title = translated("Print Your Location"),
        maxWidth = "820px",
        content = {
            // Image container
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        property("gap", "16px")
                    }
                }
            ) {
                // Display the rendered image with rounded corners
                Img(
                    src = imageUrl,
                    attrs = {
                        style {
                            width(100.percent)
                            property("max-width", "800px")
                            property("object-fit", "contain")
                            property("border-radius", "12px") // Add rounded corners to the image element
                        }
                    }
                )
                
                // Download button
                DialogButton(
                    onClick = {
                        // Create a temporary anchor element to trigger download
                        val link = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
                        link.href = imageUrl
                        link.download = "saigon-quest-location.png"
                        document.body?.appendChild(link)
                        link.click()
                        document.body?.removeChild(link)
                    },
                    isPrimary = true
                ) {
                    Translated("📥 Download Image")
                }
            }
        }
    )
}

/**
 * Function to generate a print image with the scene, title, subtitle, description, and QR code.
 * Takes a callback to handle the generated image URL.
 * Uses BabylonJS's native screenshot capability to capture the 3D scene.
 * 
 * Note: The QR code includes player position, but the displayed URL below the QR code doesn't.
 */
fun generatePrintImage(
    url: String,
    worldTitle: String,
    worldSubtitle: String,
    worldDescription: String = "",
    onComplete: (String) -> Unit
) {
    // Strip player position from URL for display only
    val displayUrl = url.split("?at=")[0]
    
    // Use the original URL with player position for QR code
    val qrData = Qr.createQR(url, "gif")
    val printQrDataUrl = BinaryUtils.uint8ArrayToDataUrl(qrData, "image/gif")
    // Create a new canvas for the print view
    val printCanvas = document.createElement("canvas") as HTMLCanvasElement
    printCanvas.width = 800
    printCanvas.height = 1067 // 4:3 ratio (800 * 4/3)
    
    val ctx = printCanvas.getContext("2d") as CanvasRenderingContext2D
    
    // Fill background with white
    ctx.fillStyle = "white"
    ctx.fillRect(0.0, 0.0, 800.0, 1067.0)
    
    // Function to draw the QR code and complete the image
    fun drawQRCode(sceneDataUrl: String = "") {
        // Draw the scene image with rounded corners if available
        if (sceneDataUrl.isNotEmpty()) {
            val sceneImg = Image()
            sceneImg.onload = {
                // Create rounded corners for scene image
                ctx.save()
                // Define the rectangle with rounded corners
                val x = 40.0
                val y = 40.0
                val width = 720.0
                val height = 405.0
                val radius = 12.0 // Corner radius
                
                // Draw rounded rectangle path
                ctx.beginPath()
                ctx.moveTo(x + radius, y)
                ctx.lineTo(x + width - radius, y)
                ctx.quadraticCurveTo(x + width, y, x + width, y + radius)
                ctx.lineTo(x + width, y + height - radius)
                ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
                ctx.lineTo(x + radius, y + height)
                ctx.quadraticCurveTo(x, y + height, x, y + height - radius)
                ctx.lineTo(x, y + radius)
                ctx.quadraticCurveTo(x, y, x + radius, y)
                ctx.closePath()
                
                // Clip to the rounded rectangle
                ctx.clip()

                // Draw the scene image with center crop (ensuring the rounded rectangle is completely covered)
                val imgWidth = sceneImg.width.toDouble()
                val imgHeight = sceneImg.height.toDouble()

                val scale = max(width / imgWidth, height / imgHeight)
                val scaledWidth = imgWidth * scale
                val scaledHeight = imgHeight * scale
                val sX = (scaledWidth - width) / 2 / scale
                val sY = (scaledHeight - height) / 2 / scale
                val sWidth = width / scale
                val sHeight = height / scale

                // TODO this is incorrect but dunno why. Is there transparency in the sceneImg or smth?
                ctx.drawImage(sceneImg, sX, sY, sWidth, sHeight, x, y, width, height)

                // Restore the context
                ctx.restore()
                
                // Add title and subtitle
                ctx.fillStyle = "black"
                ctx.font = "bold 32px 'Ysabeau Infant', sans-serif"
                ctx.asDynamic().textAlign = "center"
                ctx.fillText(worldTitle, 400.0, 490.0)
                
                ctx.fillStyle = "rgba(0, 0, 0, 0.7)"
                ctx.font = "20px 'Ysabeau Infant', sans-serif"
                ctx.fillText(worldSubtitle, 400.0, 525.0)
                
                // Add world description if available
                if (worldDescription.isNotEmpty()) {
                    ctx.fillStyle = "rgba(0, 0, 0, 0.6)"
                    ctx.font = "16px 'Ysabeau Infant', sans-serif"
                    
                    // Handle multi-line descriptions by wrapping text
                    val maxWidth = 700.0
                    val lineHeight = 22.0
                    val words = worldDescription.split(" ")
                    var line = ""
                    var y = 555.0
                    
                    for (word in words) {
                        val testLine = if (line.isEmpty()) word else "$line $word"
                        val metrics = ctx.measureText(testLine)
                        
                        if (metrics.width > maxWidth && line.isNotEmpty()) {
                            ctx.fillText(line, 400.0, y)
                            line = word
                            y += lineHeight
                        } else {
                            line = testLine
                        }
                    }
                    
                    // Draw the last line
                    if (line.isNotEmpty()) {
                        ctx.fillText(line, 400.0, y)
                    }
                }
                
                // Draw a white background for the QR code
                ctx.fillStyle = "white"
                ctx.fillRect(250.0, 570.0, 300.0, 300.0)
                
                // Add URL text (without player position)
                ctx.fillStyle = "rgba(0, 0, 0, 0.7)"
                ctx.font = "16px 'Ysabeau Infant', sans-serif"
                ctx.fillText(displayUrl, 400.0, 900.0)
                
                // Add footer
                ctx.fillStyle = "rgba(0, 0, 0, 0.5)"
                ctx.font = "14px 'Ysabeau Infant', sans-serif"
                ctx.fillText(str("Saigon.Quest"), 400.0, 1020.0)
                
                // Draw the QR code with pixelated rendering
                val qrImg = Image()
                qrImg.onload = {
                    // Set pixelated rendering for QR code
                    ctx.asDynamic().imageSmoothingEnabled = false
                    
                    // Draw the QR code
                    ctx.drawImage(qrImg, 265.0, 585.0, 270.0, 270.0)
                    
                    // Reset image smoothing to default
                    ctx.asDynamic().imageSmoothingEnabled = true
                    
                    // Call the callback with the final image URL
                    onComplete(printCanvas.toDataURL())
                }
                qrImg.src = printQrDataUrl
            }
            sceneImg.src = sceneDataUrl
        } else {
            // Fallback to placeholder if scene capture failed
            // Create rounded corners for fallback placeholder
            ctx.save()
            // Define the rectangle with rounded corners
            val x = 40.0
            val y = 40.0
            val width = 720.0
            val height = 405.0
            val radius = 12.0 // Corner radius
            
            // Draw rounded rectangle path
            ctx.beginPath()
            ctx.moveTo(x + radius, y)
            ctx.lineTo(x + width - radius, y)
            ctx.quadraticCurveTo(x + width, y, x + width, y + radius)
            ctx.lineTo(x + width, y + height - radius)
            ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
            ctx.lineTo(x + radius, y + height)
            ctx.quadraticCurveTo(x, y + height, x, y + height - radius)
            ctx.lineTo(x, y + radius)
            ctx.quadraticCurveTo(x, y, x + radius, y)
            ctx.closePath()
            
            // Fill the rounded rectangle
            ctx.fillStyle = "rgb(50, 50, 50)"
            ctx.fill()
            
            // Restore the context
            ctx.restore()
            
            // Add title and subtitle
            ctx.fillStyle = "black"
            ctx.font = "bold 32px 'Ysabeau Infant', sans-serif"
            ctx.asDynamic().textAlign = "center"
            ctx.fillText(worldTitle, 400.0, 490.0)
            
            ctx.fillStyle = "rgba(0, 0, 0, 0.7)"
            ctx.font = "20px 'Ysabeau Infant', sans-serif"
            ctx.fillText(worldSubtitle, 400.0, 525.0)
            
            // Add world description if available
            if (worldDescription.isNotEmpty()) {
                ctx.fillStyle = "rgba(0, 0, 0, 0.6)"
                ctx.font = "16px 'Ysabeau Infant', sans-serif"
                
                // Handle multi-line descriptions by wrapping text
                val maxWidth = 700.0
                val lineHeight = 22.0
                val words = worldDescription.split(" ")
                var line = ""
                var y = 555.0
                
                for (word in words) {
                    val testLine = if (line.isEmpty()) word else "$line $word"
                    val metrics = ctx.measureText(testLine)
                    
                    if (metrics.width > maxWidth && line.isNotEmpty()) {
                        ctx.fillText(line, 400.0, y)
                        line = word
                        y += lineHeight
                    } else {
                        line = testLine
                    }
                }
                
                // Draw the last line
                if (line.isNotEmpty()) {
                    ctx.fillText(line, 400.0, y)
                }
            }
            
            // Draw a white background for the QR code
            ctx.fillStyle = "white"
            ctx.fillRect(250.0, 570.0, 300.0, 300.0)
            
            // Add URL text (without player position)
            ctx.fillStyle = "rgba(0, 0, 0, 0.7)"
            ctx.font = "16px 'Ysabeau Infant', sans-serif"
            ctx.fillText(displayUrl, 400.0, 900.0)
            
            // Add footer
            ctx.fillStyle = "rgba(0, 0, 0, 0.5)"
            ctx.font = "14px 'Ysabeau Infant', sans-serif"
            ctx.fillText(str("Saigon.Quest"), 400.0, 1020.0)
            
            // Draw the QR code with pixelated rendering
            val qrImg = Image()
            qrImg.onload = {
                // Set pixelated rendering for QR code
                ctx.asDynamic().imageSmoothingEnabled = false
                
                // Draw the QR code
                ctx.drawImage(qrImg, 265.0, 585.0, 270.0, 270.0)
                
                // Reset image smoothing to default
                ctx.asDynamic().imageSmoothingEnabled = true
                
                // Call the callback with the final image URL
                onComplete(printCanvas.toDataURL())
            }
            qrImg.src = printQrDataUrl
        }
    }
    
    // Use BabylonJS to take a screenshot
    val scene = SceneManager.getScene()
    if (scene != null) {
        try {
            // Create screenshot options with width and height
            val options = object : ScreenshotOptions {
                override val width: Int = scene.getEngine().getRenderWidth()
                override val height: Int = scene.getEngine().getRenderHeight()
            }
            
            // Use the Tools class to take a screenshot
            Tools.CreateScreenshot(
                engine = scene.getEngine(),
                camera = scene.activeCamera,
                options = options,
                onSuccess = { data ->
                    drawQRCode(data)
                    // Create a temporary anchor element to trigger download
//                    val link = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
//                    link.href = data
//                    link.download = "saigon-quest-screenshot.png"
//                    document.body?.appendChild(link)
//                    link.click()
//                    document.body?.removeChild(link)
                }
            )
        } catch (e: Throwable) {
            console.error("BabylonJS Tools.CreateScreenshot error: ${e.message}")
            drawQRCode("")
        }
    } else {
        console.error("Scene not available for screenshot")
        drawQRCode("")
    }
}
