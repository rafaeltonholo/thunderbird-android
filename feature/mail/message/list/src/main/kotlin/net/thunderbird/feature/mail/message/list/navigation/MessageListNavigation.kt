package net.thunderbird.feature.mail.message.list.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import app.k9mail.core.ui.compose.navigation.Navigation
import app.k9mail.core.ui.compose.navigation.deepLinkComposable
import net.thunderbird.feature.mail.message.list.ui.MessageList

interface MessageListNavigation : Navigation<MessageListRoute>

internal class DefaultMessageListNavigation : MessageListNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (MessageListRoute) -> Unit,
    ) = with(navGraphBuilder) {
        deepLinkComposable<MessageListRoute.Home>(MessageListRoute.Home.basePath) {
            BackHandler {
                onBack()
            }
            MessageList(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
