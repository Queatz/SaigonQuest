package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import main.theme.Styles
import models.Asset
import models.AssetType
import main.api.NotificationApi
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

/**
 * A dialog for admin operations
 * 
 * @param onClose Callback when the dialog is closed
 * @param onRegisterAsset Callback when an asset is registered
 */
@Composable
fun AdminDialog(
    onClose: () -> Unit,
    onRegisterAsset: (Asset) -> Unit
) {
    // State for the asset registration form
    var selectedAssetType by remember { mutableStateOf(AssetType.Items) }
    var assetFileName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // State for system notification form
    var targetUsername by remember { mutableStateOf("") }
    var notificationTitle by remember { mutableStateOf("") }
    var notificationMessage by remember { mutableStateOf("") }
    var showBroadcastConfirm by remember { mutableStateOf(false) }
    var showBalanceRequestsDialog by remember { mutableStateOf(false) }
    
    // Use the BaseDialog component
    BaseDialog(
        onClose = onClose,
        title = translated("Admin panel"),
        showCloseButton = true,
        content = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        property("gap", "16px")
                        width(100.percent)
                    }
                }
            ) {
                // Asset registration form
                H3 {
                    Translated("Register new asset")
                }
                
                // Asset type selection
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            width(100.percent)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Translated("Asset type")
                    Select(
                        attrs = {
                            classes(Styles.inputField)

                            style {
                                width(100.percent)
                                padding(8.px)
                                margin(8.px, 0.px)
                            }
                            onInput { event ->
                                val value = event.value
                                if (value != null) {
                                    selectedAssetType = AssetType.valueOf(value)
                                }
                            }
                        }
                    ) {
                        AssetType.entries.forEach { type ->
                            Option(
                                value = type.name,
                                attrs = {
                                    if (type == selectedAssetType) {
                                        attr("selected", "true")
                                    }
                                }
                            ) {
                                Text(type.name)
                            }
                        }
                    }
                }
                
                // Asset filename input
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            width(100.percent)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Translated("File name (without extension)")
                    TextInput(
                        value = assetFileName,
                        attrs = {
                            classes(Styles.inputField)

                            onInput { event ->
                                assetFileName = event.value
                            }
                        }
                    )
                }

                // Divider spacing
                Div(attrs = { style { margin(16.px) } }) {}

                // System notification form
                H3 {
                    Translated("Send system notification")
                }
                // Target username input (leave blank to broadcast)
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            width(100.percent)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Translated("Target username (leave blank to broadcast)")
                    TextInput(
                        value = targetUsername,
                        attrs = {
                            classes(Styles.inputField)
                            onInput { event -> targetUsername = event.value }
                        }
                    )
                }
                // Notification title input
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            width(100.percent)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Translated("Title")
                    TextInput(
                        value = notificationTitle,
                        attrs = {
                            classes(Styles.inputField)
                            onInput { event -> notificationTitle = event.value }
                        }
                    )
                }
                // Notification message input
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            width(100.percent)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Translated("Message")
                    TextInput(
                        value = notificationMessage,
                        attrs = {
                            classes(Styles.inputField)
                            onInput { event -> notificationMessage = event.value }
                        }
                    )
                }

                // Divider spacing
                Div(attrs = { style { margin(16.px) } }) {}

                // Balance requests section
                H3 { Translated("Pending Balance Requests") }
                DialogButton(
                    onClick = { showBalanceRequestsDialog = true },
                    isPrimary = true
                ) { Translated("Open") }
            }
        },
        actions = {
            // Register asset button
            DialogButton(
                onClick = {
                    if (assetFileName.isNotBlank()) {
                        val asset = Asset(
                            type = selectedAssetType,
                            file = assetFileName
                        )
                        onRegisterAsset(asset)
                        onClose()
                    }
                },
                isPrimary = true
            ) {
                Translated("Register asset")
            }

            // Send system notification button
            DialogButton(
                onClick = {
                    if (notificationTitle.isNotBlank() && notificationMessage.isNotBlank()) {
                        if (targetUsername.trim().isBlank()) {
                            showBroadcastConfirm = true
                        } else {
                            scope.launch {
                                val ok = NotificationApi.sendSystemNotification(
                                    username = targetUsername,
                                    title = notificationTitle,
                                    description = notificationMessage
                                )
                                if (ok) {
                                    notificationTitle = ""
                                    notificationMessage = ""
                                }
                            }
                        }
                    }
                },
                isPrimary = true
            ) {
                Translated("Send notification")
            }
            
            // Close button
            DialogButton(
                onClick = onClose,
                isPrimary = false
            ) {
                Translated("Close")
            }
        }
    )

    // Broadcast confirmation dialog
    if (showBroadcastConfirm) {
        BasicConfirmDialog(
            message = translated("Send this notification to all users?"),
            onConfirm = {
                showBroadcastConfirm = false
                scope.launch {
                    val ok = NotificationApi.sendSystemNotification(
                        username = "",
                        title = notificationTitle,
                        description = notificationMessage
                    )
                    if (ok) {
                        notificationTitle = ""
                        notificationMessage = ""
                    }
                }
            },
            onCancel = { showBroadcastConfirm = false }
        )
    }

    if (showBalanceRequestsDialog) {
        main.components.AdminBalanceRequestsDialog(
            baseUrl = main.api.ApiConstants.API_BASE_URL,
            bearerToken = main.api.UserApi.getToken() ?: "",
            onClose = { showBalanceRequestsDialog = false }
        )
    }
}
