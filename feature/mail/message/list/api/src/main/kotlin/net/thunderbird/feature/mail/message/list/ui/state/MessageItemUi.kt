package net.thunderbird.feature.mail.message.list.ui.state

import androidx.compose.runtime.Immutable

/**
 * Represents the UI state of a single message item in a message list.
 *
 * This immutable data class encapsulates all the information needed to display a message
 * in the message list UI, including its read/unread state, sender information, content
 * preview, metadata flags (starred, encrypted, etc.), and selection state. It supports
 * both single messages and threaded conversations through the threadCount property.
 *
 * @property state The current visual state of the message (New, Read, or Unread).
 * @property id The unique identifier for this message.
 * @property account The account associated with this message, containing its identifier
 *  and display color.
 * @property senders The composed representation of the message sender(s) with display name,
 *  styling, and avatar.
 * @property subject The subject line of the message.
 * @property excerpt A preview or snippet of the message body content.
 * @property formattedReceivedAt A human-readable string representing when the message was
 *  received.
 * @property hasAttachments Whether the message contains one or more attachments.
 * @property starred Whether the user has marked the message as starred/important.
 * @property encrypted Whether the message content is encrypted.
 * @property answered Whether the message has been replied to.
 * @property forwarded Whether the message has been forwarded.
 * @property selected Whether the message is currently selected in the UI (e.g., for bulk actions).
 * @property threadCount The number of messages in the thread. A value of 0-1 indicates a single
 *  message (not threaded).
 * @property active Whether the message is currently the active/focused item in the UI.
 *  **NOTE:** [active] is only used when Home Screen is on Split mode.
 */
@Immutable
data class MessageItemUi(
    val state: State,
    val id: String,
    val account: Account,
    val senders: ComposedAddressUi,
    val subject: String,
    val excerpt: String,
    val formattedReceivedAt: String,
    val hasAttachments: Boolean,
    val starred: Boolean,
    val encrypted: Boolean,
    val answered: Boolean,
    val forwarded: Boolean,
    val selected: Boolean,
    val threadCount: Int = 0,
    val active: Boolean = false,
) {
    /**
     * Represents the visual and interactive state of a `MessageItem`.
     *
     * This enum defines the different states a message can be in within the message item,
     * which dictates its styling and available actions.
     */
    enum class State {
        /** The message has just been added to the message list. **/
        New,

        /** The message has been read by the user. */
        Read,

        /** The message has not yet been read by the user. */
        Unread,
    }
}
