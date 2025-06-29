package net.thunderbird.feature.mail.message.list.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDateTime
import net.thunderbird.core.common.action.SwipeActions

data class Message(
    val account: UserAccount,
    val subject: String,
    val contentPreview: String,
    val dateTime: LocalDateTime,
    val from: ImmutableList<MessageIdentity>,
    val recipients: ImmutableList<MessageIdentity>,
    val isRead: Boolean,
    val threadCount: Int,
//    val swipeActions: SwipeActions,
)
