package main.components.player

import Translations.str
import androidx.compose.runtime.Composable
import main.components.Translated
import models.Skill
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun SkillList(
    skillLevels: Map<String, Int>,
    allSkills: List<Skill>,
) {
    if (skillLevels.isEmpty()) return

    Div({
        style {
            marginTop(16.px)
            marginBottom(16.px)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(8.px)
        }
    }) {
        P(attrs = { style { margin(0.px); fontWeight("bold") } }) {
            Translated("Skills")
        }

        skillLevels.forEach { (skillId, level) ->
            val skill = allSkills.find { it.key == skillId }
            if (skill != null) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        gap(8.px)
                        padding(4.px)
                        property("border-bottom", "1px solid rgba(255,255,255,0.05)")
                    }
                }) {
                    if (skill.imageUrl.isNotBlank()) {
                        Img(src = skill.imageUrl, attrs = {
                            style {
                                width(32.px)
                                height(32.px)
                                property("object-fit", "cover")
                                borderRadius(8.px)
                            }
                        })
                    }
                    Span { Text(skill.title) }
                    Div(attrs = { style { property("flex-grow", "1") } })
                    Span(attrs = { style { fontWeight("bold") } }) {
                        Text("${str("Level")} $level")
                    }
                }
            }
        }
    }
}
