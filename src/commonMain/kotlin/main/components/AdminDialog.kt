package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import main.api.BalanceApi
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
    // Admin modal states
    var showRegisterAssetDialog by remember { mutableStateOf(false) }
    var showSendNotificationDialog by remember { mutableStateOf(false) }
    var showBalanceRequestsDialog by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var adminAuthError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            pendingCount = BalanceApi.getRequestsCount("Received")
        } catch (t: Throwable) {
            pendingCount = null
        }
    }
    
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
                // Asset actions
                H3 {
                    Translated("Register new asset")
                }
                DialogButton(
                    onClick = { showRegisterAssetDialog = true },
                    isPrimary = true,
                ) { Translated("Open") }

                // Divider spacing
                Div(attrs = { style { margin(16.px) } }) {}

                // System notification
                H3 { Translated("Send system notification") }
                DialogButton(
                    onClick = { showSendNotificationDialog = true },
                    isPrimary = true,
                ) { Translated("Open") }

                // Divider spacing
                Div(attrs = { style { margin(16.px) } }) {}

                // Balance requests section
                H3 { Translated("Pending Balance Requests") }
                Div(attrs = { style { marginBottom(8.px) } }) {
                    Text(
                        pendingCount?.let { "You have $it pending requests" } ?: "Unable to load pending requests"
                    )
                }
                DialogButton(
                    onClick = {
                        val token = main.api.UserApi.getToken()
                        if (token.isNullOrEmpty()) {
                            adminAuthError = "Please sign in to access admin tools"
                        } else {
                            adminAuthError = null
                            showBalanceRequestsDialog = true
                        }
                    },
                    isPrimary = ((pendingCount ?: 0) > 0)
                ) {
                    Text("Open (" + (pendingCount?.toString() ?: "?") + ")")
                }
                adminAuthError?.let { msg ->
                    Div { Text(msg) }
                }
            }
        },
        actions = {
            DialogButton(
                onClick = onClose,
                isPrimary = false,
            ) {
                Translated("Close")
            }
        }
    )

    if (showRegisterAssetDialog) {
        AdminRegisterAssetDialog(
            onClose = { showRegisterAssetDialog = false },
            onRegisterAsset = {
                onRegisterAsset(it)
                showRegisterAssetDialog = false
            },
        )
    }

    if (showSendNotificationDialog) {
        AdminSendNotificationDialog(
            onClose = { showSendNotificationDialog = false },
        )
    }

    if (showBalanceRequestsDialog) {
        main.components.AdminBalanceRequestsDialog(
            baseUrl = main.api.ApiConstants.API_BASE_URL,
            bearerToken = main.api.UserApi.getToken() ?: "",
            onClose = {
                showBalanceRequestsDialog = false
                scope.launch {
                    try { pendingCount = BalanceApi.getRequestsCount("Received") } catch (_: Throwable) {}
                }
            }
        )
    }
}
