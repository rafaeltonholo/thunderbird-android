package net.thunderbird.feature.debugSettings.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import app.k9mail.core.ui.compose.navigation.Navigation
import app.k9mail.core.ui.compose.navigation.deepLinkComposable
import net.thunderbird.feature.debugSettings.BuildConfig
import net.thunderbird.feature.debugSettings.SecretDebugSettingsScreen
import net.thunderbird.feature.debugSettings.navigation.SecretDebugSettingsRoute.Notification

interface SecretDebugSettingsNavigation : Navigation<SecretDebugSettingsRoute>

internal class DefaultSecretDebugSettingsNavigation : SecretDebugSettingsNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (SecretDebugSettingsRoute) -> Unit,
    ) {
        if (BuildConfig.DEBUG) {
            with(navGraphBuilder) {
                deepLinkComposable<Notification>(Notification.basePath) {
                    SecretDebugSettingsScreen(
                        onNavigateBack = onBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
