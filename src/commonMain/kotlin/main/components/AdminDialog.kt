package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.AdminBalanceApi
import main.components.inventory.AdminItemsDialog
import models.Asset
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

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
    var showItemsDialog by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var adminAuthError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            pendingCount = AdminBalanceApi.getPendingCount()
        } catch (t: Throwable) {
            t.printStackTrace()
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
                DialogButton(
                    onClick = { showRegisterAssetDialog = true },
                    isPrimary = true,
                ) { Translated("Register new asset") }

                // System notification
                DialogButton(
                    onClick = { showSendNotificationDialog = true },
                    isPrimary = true,
                ) { Translated("Send system notification") }

                // Manage items
                DialogButton(
                    onClick = { showItemsDialog = true },
                    isPrimary = true,
                ) { Translated("Manage items") }

                // Balance requests section
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
                    Translated("Pending balance requests")
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
        AdminBalanceRequestsDialog(
            baseUrl = main.api.ApiConstants.API_BASE_URL,
            bearerToken = main.api.UserApi.getToken() ?: "",
            onClose = {
                showBalanceRequestsDialog = false
                scope.launch {
                    try { pendingCount = AdminBalanceApi.getPendingCount() } catch (_: Throwable) {}
                }
            }
        )
    }

    if (showItemsDialog) {
        AdminItemsDialog(
            onClose = { showItemsDialog = false }
        )
    }
}
