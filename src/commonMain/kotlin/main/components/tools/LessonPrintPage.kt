package main.components.tools

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import main.api.LessonApi
import main.theme.PrintStyles
import models.LessonScene
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun LessonPrintPage(id: String) {
    var scene by remember { mutableStateOf<LessonScene?>(null) }
    
    LaunchedEffect(id) {
        scene = LessonApi.getScene(id)
    }

    DisposableEffect(Unit) {
        window.document.body?.classList?.add("printable")
        window.document.documentElement?.classList?.add("printable")
        onDispose {
            window.document.body?.classList?.remove("printable")
            window.document.documentElement?.classList?.remove("printable")
        }
    }

    LaunchedEffect(scene) {
        if (scene != null) {
            delay(2000) // Increased delay to ensure images load
            window.print()
        }
    }

    if (scene == null) return
    val currentScene = scene!!

    Div(attrs = {
        classes(PrintStyles.printContainer)
    }) {
        // Page 1: Scene Background
        Div(attrs = {
            classes(PrintStyles.a4Page)
            style {
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center); justifyContent(JustifyContent.Center)
                backgroundColor(Color.black)
            }
        }) {
            currentScene.sceneImage?.let {
                Img(src = it, attrs = { 
                    style { 
                        width(297.mm); height(210.mm); 
                        property("transform", "rotate(90deg)")
                        property("object-fit", "cover")
                        property("flex-shrink", "0")
                        property("display", "block")
                    } 
                })
            }
        }

        // Page 2: Event Cards (12 per page)
        currentScene.eventCards.chunked(12).forEach { cards ->
            Div(attrs = {
                classes(PrintStyles.a4Page, PrintStyles.cardsGrid)
            }) {
                cards.forEach { card ->
                    Div(attrs = {
                        classes(PrintStyles.cardItem)
                    }) {
                        card.image?.let { 
                            Div(attrs = {
                                style {
                                    width(45.mm); height(60.mm)
                                    overflow("hidden"); borderRadius(2.mm)
                                }
                            }) {
                                Img(src = it, attrs = { style { width(100.percent); height(100.percent); property("object-fit", "cover") } }) 
                            }
                        }
                        Span(attrs = { style { fontSize(12.pt); textAlign("center"); color(Color.black); fontWeight("bold") } }) { Text(card.text) }
                    }
                }
            }
        }

        // Page 3: Items
        currentScene.items.chunked(15).forEach { items ->
            Div(attrs = {
                classes(PrintStyles.a4Page, PrintStyles.itemsGrid)
            }) {
                items.forEach { item ->
                    Div(attrs = {
                        classes(PrintStyles.itemContainer)
                    }) {
                        item.image?.let { Img(src = it, attrs = { style { maxWidth(100.percent); maxHeight(100.percent); property("display", "block") } }) }
                    }
                }
            }
        }

        // Page 4: Reference Pages
        currentScene.referencePages.forEach { page ->
            Div(attrs = {
                classes(PrintStyles.a4Page)
                style { padding(15.mm); boxSizing("border-box") }
            }) {
                Div(attrs = { classes(PrintStyles.referenceTitle) }) { Text(page.title) }

                page.image?.let {
                    Img(src = it, attrs = {
                        style {
                            width(100.percent)
                            maxHeight(148.mm) // ~50% of 297mm
                            property("object-fit", "cover")
                            borderRadius(2.mm)
                            marginBottom(5.mm)
                        }
                    })
                }

                Table(attrs = { classes(PrintStyles.referenceTable) }) {
                    Tbody {
                        page.rows.forEach { row ->
                            Tr(attrs = {
                                style { property("break-inside", "avoid") }
                            }) {
                                Td(attrs = {
                                    classes(PrintStyles.referenceCell)
                                    style { width(50.mm) }
                                }) {
                                    row.image?.let {
                                        Img(src = it, attrs = { style { width(100.percent); display(DisplayStyle.Block); borderRadius(2.mm) } })
                                    }
                                }
                                Td(attrs = { classes(PrintStyles.referenceCell) }) {
                                    Span(attrs = { style { fontSize(14.pt); color(Color.black) } }) { Text(row.text) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
