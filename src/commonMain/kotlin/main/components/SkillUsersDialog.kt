package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.SkillApi
import main.components.friends.SelectFriendDialog
import main.theme.Styles
import models.Skill
import models.User
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun SkillUsersDialog(
    skill: Skill,
    onClose: () -> Unit,
) {
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var settingLevelForUser by remember { mutableStateOf<User?>(null) }
    var newLevelInput by remember { mutableStateOf("") }
    var isSettingLevel by remember { mutableStateOf(false) }
    var showSelectFriendDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadUsers() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                users = SkillApi.getUsersBySkill(skill.key)
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Failed to load users"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadUsers() }

    fun setLevel(user: User) {
        val level = newLevelInput.toIntOrNull() ?: return
        isSettingLevel = true
        scope.launch {
            try {
                SkillApi.setUserSkillLevel(user.key, skill.key, level)
                settingLevelForUser = null
                loadUsers()
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Failed to set level"
            } finally {
                isSettingLevel = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = "${translated("Users with this skill")}: ${skill.title}",
        maxWidth = "600px",
        headerActions = {
            DialogButton(
                style = DialogButtonStyle.Primary,
                onClick = { showSelectFriendDialog = true }
            ) { Translated("Add friend") }
        },
        content = {
            if (errorMessage != null) {
                P { Text(errorMessage!!) }
            }
            if (isLoading) {
                P { Translated("Loading...") }
            } else if (users.isEmpty()) {
                P { Translated("No users found with this skill") }
            } else {
                // Header
                Div(attrs = {
                    style {
                        display(DisplayStyle.Grid)
                        gridTemplateColumns("1fr 100px 100px")
                        gap(16.px)
                        padding(8.px)
                        fontWeight("bold")
                        property("border-bottom", "1px solid rgba(255,255,255,0.1)")
                    }
                }) {
                    Span { Translated("Username") }
                    Span { Translated("Level") }
                    Span { Translated("Actions") }
                }

                // Rows
                for (user in users) {
                    Div(attrs = {
                        style {
                            display(DisplayStyle.Grid)
                            gridTemplateColumns("1fr 100px 100px")
                            gap(16.px)
                            padding(8.px)
                            property("border-bottom", "1px solid rgba(255,255,255,0.1)")
                            alignItems(AlignItems.Center)
                        }
                    }) {
                        Span { Text(user.username) }
                        Span { Text(user.skillLevels[skill.key]?.toString() ?: "0") }
                        DialogButton(
                            style = DialogButtonStyle.Secondary,
                            onClick = { 
                                settingLevelForUser = user
                                newLevelInput = (user.skillLevels[skill.key] ?: 0).toString()
                            }
                        ) { Translated("Set level") }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )

    settingLevelForUser?.let { user ->
        BaseDialog(
            onClose = { settingLevelForUser = null },
            title = "${translated("Set level")}: ${user.username}",
            content = {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        gap(16.px)
                    }
                }) {
                    DialogInput(
                        label = translated("Level"),
                        value = newLevelInput,
                        onInput = { newLevelInput = it },
                        type = org.jetbrains.compose.web.attributes.InputType.Number
                    )
                }
            },
            actions = {
                DialogButton(
                    onClick = { settingLevelForUser = null },
                    style = DialogButtonStyle.Secondary,
                    isDisabled = isSettingLevel
                ) { Translated("Cancel") }
                
                DialogButton(
                    onClick = { setLevel(user) },
                    style = DialogButtonStyle.Primary,
                    isDisabled = isSettingLevel
                ) { 
                    if (isSettingLevel) Translated("Saving...") else Translated("Set level")
                }
            }
        )
    }

    if (showSelectFriendDialog) {
        SelectFriendDialog(
            onSelect = { friend ->
                showSelectFriendDialog = false
                settingLevelForUser = User(
                    username = friend.friendUsername.ifBlank { friend.friendId },
                ).apply {
                    key = friend.friendId
                }
                newLevelInput = "1"
            },
            onClose = { showSelectFriendDialog = false }
        )
    }
}
