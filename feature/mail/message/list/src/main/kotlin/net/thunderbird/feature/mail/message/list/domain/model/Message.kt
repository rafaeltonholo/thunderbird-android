package net.thunderbird.feature.mail.message.list.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDateTime

data class Message(
    val account: UserAccount,
    val subject: String,
    val contentPreview: String,
    val dateTime: LocalDateTime,
    val from: ImmutableList<MessageIdentity>,
    val recipients: ImmutableList<MessageIdentity>,
    val read: Boolean,
    val threadCount: Int,
    val starred: Boolean,
//    val swipeActions: SwipeActions,
)
