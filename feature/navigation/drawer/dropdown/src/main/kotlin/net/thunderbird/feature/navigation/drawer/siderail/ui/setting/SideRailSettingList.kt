package net.thunderbird.feature.navigation.drawer.siderail.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.k9mail.core.ui.compose.designsystem.atom.icon.Icons
import app.k9mail.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.navigation.drawer.dropdown.R
import net.thunderbird.feature.navigation.drawer.dropdown.ui.setting.SettingListItem

@Composable
internal fun SideRailSettingList(
    onAccountSelectorClick: () -> Unit,
    onManageFoldersClick: () -> Unit,
    onNewMessageListClick: () -> Unit,
    showAccountSelector: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(vertical = MainTheme.spacings.default)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .fillMaxWidth(),
    ) {
        SettingListItem(
            label = "New Message List",
            onClick = onNewMessageListClick,
            icon = Icons.Outlined.Mail,
        )
        SettingListItem(
            label = stringResource(R.string.navigation_drawer_dropdown_action_manage_folders),
            onClick = onManageFoldersClick,
            icon = Icons.Outlined.FolderManaged,
        )
        SettingListItem(
            label = if (showAccountSelector) {
                stringResource(R.string.navigation_drawer_dropdown_action_hide_accounts)
            } else {
                stringResource(R.string.navigation_drawer_dropdown_action_show_accounts)
            },
            onClick = onAccountSelectorClick,
            icon = if (showAccountSelector) {
                Icons.Outlined.ChevronLeft
            } else {
                Icons.Outlined.ChevronRight
            },
        )
    }
}
