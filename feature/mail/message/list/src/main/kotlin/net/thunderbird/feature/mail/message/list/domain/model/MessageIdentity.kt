package net.thunderbird.feature.mail.message.list.domain.model

import androidx.compose.ui.graphics.Color

data class MessageIdentity(
    val email: String,
    val color: Color,
    val avatarUrl: String?,
)
