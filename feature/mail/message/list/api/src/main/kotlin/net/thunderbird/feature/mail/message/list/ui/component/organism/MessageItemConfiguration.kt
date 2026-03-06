package net.thunderbird.feature.mail.message.list.ui.component.organism

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.mail.message.list.ui.component.molecule.MessageConversationCounterBadgeColor
import net.thunderbird.feature.mail.message.list.ui.state.Avatar

/**
 * Configuration class that defines the visual presentation and layout settings for a message item.
 *
 * This data class encapsulates all configurable aspects of how a message should be displayed,
 * including text truncation, line ordering, leading/trailing elements, and account indicators.
 *
 * @property maxExcerptLines The maximum number of lines to display for the message excerpt before truncation.
 * @property swapFirstLineWithSecondLine When true, the first and second lines of the message display are swapped in their positions.
 * @property leadingConfiguration Configuration for elements displayed on the leading edge of the message item, such as badges and avatars.
 * @property accountIndicator Optional account indicator that can be displayed to distinguish messages from different accounts.
 * @property secondaryLineConfiguration Configuration for the secondary line of the message item, including any leading items to display.
 * @property excerptLineConfiguration Configuration for the excerpt/preview line of the message, including any leading items to display.
 * @property trailingConfiguration Configuration for elements displayed on the trailing edge of the message item, such as badges and action buttons.
 */
data class MessageItemConfiguration(
    val maxExcerptLines: Int = 2,
    val swapFirstLineWithSecondLine: Boolean = false,
    val leadingConfiguration: MessageItemLeadingConfiguration = MessageItemLeadingConfiguration(),
    val accountIndicator: MessageItemAccountIndicator? = null,
    val secondaryLineConfiguration: MessageItemLineConfiguration = MessageItemLineConfiguration(),
    val excerptLineConfiguration: MessageItemLineConfiguration = MessageItemLineConfiguration(),
    val trailingConfiguration: MessageItemTrailingConfiguration = MessageItemTrailingConfiguration(),
)

data class MessageItemLeadingConfiguration(
    val badgeStyle: MessageBadgeStyle? = null,
    val avatar: Avatar? = null,
    val avatarColor: Color? = null,
)

data class MessageItemTrailingConfiguration(
    val elements: ImmutableList<TrailingElement> = persistentListOf(),
) {
    @Immutable
    sealed interface TrailingElement {
        data object EncryptedBadge : TrailingElement
        data class FavouriteIconButton(val favourite: Boolean) : TrailingElement
    }
}

enum class MessageBadgeStyle { New, Unread }

data class MessageItemLineConfiguration(
    val leadingItems: ImmutableList<MessageItemLeadingItem> = persistentListOf(),
)

data class MessageItemAccountIndicator(val color: Color) : MessageItemLeadingItem

@Immutable
sealed interface MessageItemLeadingItem {
    data object AttachmentIcon : MessageItemLeadingItem
    data class ConversationCounterBadge(
        val count: Int,
        val color: MessageConversationCounterBadgeColor,
    ) : MessageItemLeadingItem
}
