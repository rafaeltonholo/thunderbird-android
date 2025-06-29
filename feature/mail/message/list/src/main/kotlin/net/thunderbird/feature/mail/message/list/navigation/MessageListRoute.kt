package net.thunderbird.feature.mail.message.list.navigation

import app.k9mail.core.ui.compose.navigation.Route
import kotlinx.serialization.Serializable

sealed interface MessageListRoute : Route {
    @Serializable
    data object Home : MessageListRoute {
        override val basePath: String = "app://mail/messages/list"

        override fun route(): String = basePath
    }
}
