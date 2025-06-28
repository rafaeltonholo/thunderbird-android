package net.thunderbird.feature.mail.message.list.domain.model

import androidx.compose.ui.graphics.Color

data class UserAccount(
    val name: String,
    val email: String,
    val color: Color,
)
