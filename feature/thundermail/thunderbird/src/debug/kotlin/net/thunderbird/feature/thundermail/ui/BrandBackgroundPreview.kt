package net.thunderbird.feature.thundermail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.k9mail.core.ui.compose.common.annotation.PreviewDevices
import app.k9mail.core.ui.compose.common.koin.koinPreview
import app.k9mail.core.ui.compose.designsystem.PreviewWithThemes
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import net.thunderbird.feature.thundermail.thunderbird.ui.ThunderbirdBrandBackgroundProvider

@Composable
@PreviewLightDark
@PreviewDevices
private fun BrandBackgroundPreview() {
    koinPreview {
        single<BrandBackgroundModifierProvider> { ThunderbirdBrandBackgroundProvider }
    } WithContent {
        PreviewWithThemes {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .brandBackground(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextBodyLarge("This is the body")
            }
        }
    }
}
