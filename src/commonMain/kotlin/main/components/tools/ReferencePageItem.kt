package main.components.tools

import androidx.compose.runtime.*
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import models.ReferencePage
import models.ReferenceRow
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import main.components.DialogInput
import main.components.Paper
import main.components.media.UploadMediaDialog
import models.MediaType
import org.jetbrains.compose.web.attributes.InputType
import Translations.str
import kotlin.random.Random

@Composable
fun ReferencePageItem(
    page: ReferencePage,
    darkMode: Boolean,
    sceneTitle: String,
    suggestingRowsForPages: Set<String>,
    generatingRows: Set<String>,
    generatingPages: Set<String>,
    onUpdatePage: (ReferencePage) -> Unit,
    onRemovePage: () -> Unit,
    onSuggestRows: (String, Int) -> Unit,
    onGenerateRowImage: (ReferenceRow) -> Unit
) {
    var rowBulkCount by remember { mutableStateOf(6) }
    var showUploadDialog by remember { mutableStateOf(false) }

    if (showUploadDialog) {
        UploadMediaDialog(
            onClose = { showUploadDialog = false },
            onSelect = { url, _, _ ->
                onUpdatePage(page.copy(image = url))
                showUploadDialog = false
            },
            filter = MediaType.Image,
            initialPrompt = "A beautiful cover photo for a reference page titled '${page.title}' in the context of $sceneTitle. High quality, cinematic."
        )
    }

    Paper(attrs = {
        style {
            display(DisplayStyle.Flex); flexDirection(FlexDirection.Column); gap(16.px); padding(16.px)
            color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface)
        }
    }) {
        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px); alignItems(AlignItems.Center) } }) {
            DialogInput(
                label = "Reference page title",
                value = page.title,
                onInput = { newValue ->
                    onUpdatePage(page.copy(title = newValue))
                },
                containerAttrs = { style { flexGrow(1) } }
            )
            DialogButton(
                small = true,
                style = DialogButtonStyle.Danger,
                onClick = onRemovePage
            ) {
                Translated("Remove")
            }
        }

        page.image?.let {
            Img(src = it, attrs = { style { width(100.percent); maxHeight(300.px); property("object-fit", "cover"); borderRadius(8.px) } })
        }

        if (page.title.isNotBlank()) {
            DialogButton(
                small = true,
                isLoading = generatingPages.contains(page.id),
                onClick = { showUploadDialog = true }
            ) {
                Translated("Set cover photo")
            }
        }

        page.rows.forEachIndexed { rowIndex, row ->
            Paper(attrs = {
                style {
                    display(DisplayStyle.Flex); gap(16.px); padding(12.px); alignItems(AlignItems.Center)
                    backgroundColor(if (darkMode) main.theme.Theme.colors.white05 else Color("rgba(0, 0, 0, 0.02)"))
                }
            }) {
                row.image?.let {
                    Img(src = it, attrs = { style { width(60.px); height(60.px); property("object-fit", "cover"); borderRadius(4.px); } })
                }
                Div(attrs = { style { display(DisplayStyle.Flex); flexDirection(FlexDirection.Column); flexGrow(1); gap(8.px) } }) {
                    DialogInput(
                        placeholder = str("Reference row text"),
                        value = row.text,
                        onInput = { newValue ->
                            val newRows = page.rows.toMutableList()
                            newRows[rowIndex] = newRows[rowIndex].copy(text = newValue)
                            onUpdatePage(page.copy(rows = newRows))
                        }
                    )
                    DialogButton(
                        small = true,
                        isLoading = generatingRows.contains(row.id),
                        onClick = { onGenerateRowImage(row) }
                    ) {
                        Translated("Generate row image")
                    }
                }
                DialogButton(
                    small = true,
                    style = DialogButtonStyle.Danger,
                    onClick = {
                        val newRows = page.rows.toMutableList()
                        newRows.removeAt(rowIndex)
                        onUpdatePage(page.copy(rows = newRows))
                    }
                ) {
                    Translated("Remove")
                }
            }
        }

        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px); alignItems(AlignItems.Center) } }) {
            DialogButton(
                small = true,
                onClick = {
                    val newRows = page.rows.toMutableList()
                    newRows.add(ReferenceRow(id = "row_${Random.nextInt(1000000)}"))
                    onUpdatePage(page.copy(rows = newRows))
                }
            ) {
                Translated("Add reference row")
            }

            if (page.title.isNotBlank()) {
                DialogInput(
                    type = InputType.Number,
                    value = rowBulkCount.toString(),
                    onInput = { v -> rowBulkCount = v.toIntOrNull() ?: 1 },
                    containerAttrs = { style { width(5.cssRem) } }
                )

                DialogButton(
                    small = true,
                    isLoading = suggestingRowsForPages.contains(page.id),
                    onClick = { onSuggestRows("steps", rowBulkCount) }
                ) {
                    Translated("Suggest steps")
                }

                DialogButton(
                    small = true,
                    isLoading = suggestingRowsForPages.contains(page.id),
                    onClick = { onSuggestRows("vocabulary", rowBulkCount) }
                ) {
                    Translated("Suggest vocabulary")
                }

                DialogButton(
                    small = true,
                    isLoading = suggestingRowsForPages.contains(page.id),
                    onClick = { onSuggestRows("grammar", rowBulkCount) }
                ) {
                    Translated("Suggest grammar")
                }
            }
        }
    }
}
