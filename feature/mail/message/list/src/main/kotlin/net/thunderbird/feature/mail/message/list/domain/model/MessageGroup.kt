package net.thunderbird.feature.mail.message.list.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.datetime.LocalDate

data class MessageGroup(
    val date: LocalDate,
    val messages: ImmutableList<Message>,
    val accounts: ImmutableMap<String, UserAccount>,
)
