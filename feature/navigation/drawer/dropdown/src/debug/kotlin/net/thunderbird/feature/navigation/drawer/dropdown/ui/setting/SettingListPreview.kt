package net.thunderbird.feature.navigation.drawer.dropdown.ui.setting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.k9mail.core.ui.compose.designsystem.PreviewWithTheme

@Composable
@Preview(showBackground = true)
internal fun SettingListPreview() {
    PreviewWithTheme {
        FolderSettingList(
            onManageFoldersClick = {},
            onSettingsClick = {},
            onNewMessageListClick = {},
            isUnifiedAccount = false,
        )
    }
}

@Composable
@Preview(showBackground = true)
internal fun SettingListWithUnifiedAccountPreview() {
    PreviewWithTheme {
        FolderSettingList(
            onManageFoldersClick = {},
            onSettingsClick = {},
            onNewMessageListClick = {},
            isUnifiedAccount = true,
        )
    }
}
