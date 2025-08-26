package main.components.common

import androidx.compose.runtime.Composable
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.UserBalanceHistory
import models.UserBalanceHistoryEventType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun BalanceEventRow(item: UserBalanceHistory) {
    Div({ classes(Styles.eventRow) }) {
        // Treat positive change as deposit, negative as withdraw
        val isDeposit = item.changeAmount >= 0
        val badgeClass = if (isDeposit) Styles.eventTypeDeposit else Styles.eventTypeWithdraw
        Div({ classes(Styles.eventTypeBadge, badgeClass) }) {
            Translated(
                when (item.eventType) {
                    UserBalanceHistoryEventType.Use -> "Use"
                    UserBalanceHistoryEventType.Send -> "Sent"
                    UserBalanceHistoryEventType.Receive -> "Received"
                    else -> if (isDeposit) "Deposit" else "Withdraw"
                }
            )
        }

        Div({ classes(Styles.eventMetaColumn) }) {
            // Amount: change only (end balance moved under timestamp, right-aligned)
            Div({ classes(Styles.eventAmounts) }) {
                val displayAmount = kotlin.math.abs(item.changeAmount)
                val prefix = if (isDeposit) "+" else "-"
                Span({ classes(if (isDeposit) Styles.amountPositive else Styles.amountNegative) }) {
                    Text("$prefix${formatAmount(displayAmount)} ${translated("VND")}")
                }
            }

            // Notes
            Div({ classes(Styles.eventNotes) }) {
                Text(item.notes.ifBlank { "" })
            }
        }

        // Timestamp and end balance stacked vertically
        Div({ classes(Styles.eventMetaColumnEnd) }) {
            Span({ classes(Styles.chatMessageTimestamp) }) {
                val tsText = try {
                    formatTimestampLikeChat(item.createdAt)
                } catch (t: Throwable) {
                    ""
                }
                Text(tsText)
            }
            Span({ classes(Styles.chatMessageTimestamp, Styles.endAmount) }) {
                Text("${formatAmount(item.endBalance)} ${translated("VND")}")
            }
        }
    }
}
