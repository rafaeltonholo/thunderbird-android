package net.thunderbird.feature.mail.message.list.ui.component.organism

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.thunderbird.feature.mail.message.list.preferences.MessageListPreferences
import net.thunderbird.feature.mail.message.list.ui.component.molecule.MessageConversationCounterBadgeDefaults
import net.thunderbird.feature.mail.message.list.ui.component.molecule.MessageItemSenderSubjectFirstLine
import net.thunderbird.feature.mail.message.list.ui.component.molecule.MessageItemSenderSubjectSecondLine
import net.thunderbird.feature.mail.message.list.ui.component.organism.MessageItemDefaults.toContentPadding
import net.thunderbird.feature.mail.message.list.ui.component.organism.MessageItemTrailingConfiguration.TrailingElement
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi

/**
 * Represents a message item in its New Message state.
 */
@Composable
fun NewMessageItem(
    state: MessageItemUi,
    preferences: MessageListPreferences,
    accountIndicator: MessageItemAccountIndicator?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onFavouriteChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    MessageItem(
        firstLine = {
            MessageItemSenderSubjectFirstLine(
                senders = state.senders,
                subject = MessageItemDefaults.buildSubjectAnnotatedString(state.subject),
                useSender = preferences.senderAboveSubject,
            )
        },
        secondaryLine = { prefix, inlineContent ->
            MessageItemSenderSubjectSecondLine(
                senders = state.senders,
                subject = MessageItemDefaults.buildSubjectAnnotatedString(state.subject),
                useSender = !preferences.senderAboveSubject,
                prefix = prefix,
                inlineContent = inlineContent,
            )
        },
        excerpt = state.excerpt,
        receivedAt = state.formattedReceivedAt,
        configuration = MessageItemDefaults.rememberConfiguration(
            messageItemUi = state,
            preferences = preferences,
            color = MessageConversationCounterBadgeDefaults.newMessageColor(),
            accountIndicator = accountIndicator,
        ),
        onClick = onClick,
        onLongClick = onLongClick,
        onAvatarClick = onAvatarClick,
        onTrailingClick = { element ->
            when (element) {
                is TrailingElement.FavouriteIconButton if preferences.showFavouriteButton ->
                    onFavouriteChange(element.favourite)

                else -> Unit
            }
        },
        modifier = modifier,
        selected = state.selected,
        colors = when {
            state.selected -> MessageItemDefaults.selectedMessageItemColors()
            state.active -> MessageItemDefaults.activeMessageItemColors()
            else -> MessageItemDefaults.newMessageItemColors()
        },
        contentPadding = preferences.density.toContentPadding(),
    )
}
