@file:OptIn(kotlin.time.ExperimentalTime::class)
package main.components.common

import androidx.compose.runtime.Composable
import main.theme.Styles
import models.UserBalanceHistory
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import main.components.common.formatAmount
import main.components.common.formatTimestampLikeChat

@Composable
fun BalanceEventRow(item: UserBalanceHistory) {
    Div({ classes(Styles.eventRow) }) {
        // Treat positive change as deposit, negative as withdraw
        val isDeposit = item.changeAmount >= 0
        val badgeClass = if (isDeposit) Styles.eventTypeDeposit else Styles.eventTypeWithdraw
        Div({ classes(Styles.eventTypeBadge, badgeClass) }) {
            Text(if (isDeposit) "Deposit" else "Withdraw")
        }

        // Amounts: change and end balance
        Div({ classes(Styles.eventAmounts) }) {
            val displayAmount = kotlin.math.abs(item.changeAmount)
            val prefix = if (isDeposit) "+" else "-"
            Span({ classes(if (isDeposit) Styles.amountPositive else Styles.amountNegative) }) {
                Text("$prefix${formatAmount(displayAmount)} VND")
            }
            Span({ }) { Text("→ ${formatAmount(item.endBalance)} VND") }
        }

        // Notes
        P({ classes(Styles.eventNotes) }) {
            Text(item.notes.ifBlank { "" })
        }

        // Timestamp (similar to ChatMessages)
        Span({ classes(main.theme.Styles.chatMessageTimestamp) }) {
            val tsText = try {
                formatTimestampLikeChat(item.createdAt)
            } catch (t: Throwable) {
                ""
            }
            Text(tsText)
        }
    }
}
