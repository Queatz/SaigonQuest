package main.components.tools

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import main.api.LessonApi
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

    Div {
        // Page 1: Scene Background
        Div(attrs = {
            style {
                property("width", "210mm"); property("height", "297mm")
                property("page-break-after", "always"); display(DisplayStyle.Flex)
                alignItems(AlignItems.Center); justifyContent(JustifyContent.Center)
                overflow("hidden")
                margin(0.px); padding(0.px)
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
                    } 
                })
            }
        }

        // Page 2: Event Cards (12 per page)
        currentScene.eventCards.chunked(12).forEach { cards ->
            Div(attrs = {
                style {
                    property("width", "210mm"); property("height", "297mm")
                    property("page-break-after", "always"); display(DisplayStyle.Grid)
                    property("grid-template-columns", "repeat(3, 1fr)")
                    property("grid-template-rows", "repeat(4, 1fr)")
                    padding(5.mm); boxSizing("border-box"); backgroundColor(Color.white)
                }
            }) {
                cards.forEach { card ->
                    Div(attrs = {
                        style {
                            border(0.1.px, LineStyle.Solid, Color.lightgray); display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column); alignItems(AlignItems.Center)
                            justifyContent(JustifyContent.Center); padding(5.mm); gap(2.mm)
                        }
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
                style {
                    property("width", "210mm"); property("height", "297mm")
                    property("page-break-after", "always"); display(DisplayStyle.Flex)
                    flexWrap(FlexWrap.Wrap); justifyContent(JustifyContent.SpaceAround)
                    padding(10.mm); boxSizing("border-box"); gap(10.mm); backgroundColor(Color.white)
                }
            }) {
                items.forEach { item ->
                    Div(attrs = { style { width(50.mm); height(50.mm); display(DisplayStyle.Flex); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center) } }) {
                        item.image?.let { Img(src = it, attrs = { style { maxWidth(100.percent); maxHeight(100.percent) } }) }
                    }
                }
            }
        }
    }
}
