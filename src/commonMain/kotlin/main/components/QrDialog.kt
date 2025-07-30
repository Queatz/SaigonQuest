package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window
import lib.BinaryUtils
import lib.Qr
import main.theme.Styles
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.StyleScope
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
 */
@Composable
fun QrDialog(onClose: () -> Unit) {
    // Remember the current URL
    val currentUrl = remember { window.location.href }
    
    // Generate QR code as data URL
    val qrDataUrl = remember(currentUrl) {
        // Generate QR code as GIF
        val qrData = Qr.createQR(currentUrl, "gif")
        
        // Convert Uint8Array to data URL
        BinaryUtils.uint8ArrayToDataUrl(qrData, "image/gif")
    }
    
    // Dialog container
    Div(
        attrs = {
            classes(Styles.overlayBase)
            // Add click outside to close
            onClick { onClose() }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                style {
                    width(90.percent)
                    property("max-width", "400px")
                    backgroundColor(rgba(30, 30, 30, 0.95))
                    property("border-radius", "8px")
                    property("box-shadow", "0 4px 16px rgba(0, 0, 0, 0.2)")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
                // Stop propagation to prevent closing when clicking inside the dialog
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        padding(16.px)
                        property("border-bottom", "1px solid rgba(255, 255, 255, 0.1)")
                    }
                }
            ) {
                // Dialog title
                H3(
                    attrs = {
                        style {
                            margin(0.px)
                            color(Color.white)
                        }
                    }
                ) {
                    Text("Share Your In-Game Location")
                }
                
                // Close button
                Button(
                    attrs = {
                        style {
                            backgroundColor(Color.transparent)
                            property("border", "0")
                            color(Color.white)
                            cursor("pointer")
                            fontSize(20.px)
                        }
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // QR code container
            Div(
                attrs = {
                    style {
                        padding(24.px)
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
                    // Display QR code as image with pixelized rendering
                    org.jetbrains.compose.web.dom.Img(
                        src = qrDataUrl,
                        attrs = {
                            style {
                                width(100.percent)
                                height(100.percent)
                                property("object-fit", "contain")
                                // Add pixelized image rendering
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
        }
    }
}
