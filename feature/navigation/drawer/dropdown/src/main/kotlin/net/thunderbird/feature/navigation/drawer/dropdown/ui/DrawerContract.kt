package net.thunderbird.feature.navigation.drawer.dropdown.ui

import androidx.compose.runtime.Stable
import app.k9mail.core.ui.compose.common.mvi.BaseViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.navigation.drawer.api.NavigationDrawerExternalContract.DrawerConfig
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayTreeFolder

interface DrawerContract {

    abstract class ViewModel(initialState: State) : BaseViewModel<State, Event, Effect>(initialState = initialState)

    @Stable
    data class State(
        val config: DrawerConfig = DrawerConfig(
            showUnifiedFolders = false,
            showStarredCount = false,
            showAccountSelector = true,
        ),
        val accounts: ImmutableList<DisplayAccount> = persistentListOf(),
        val selectedAccountId: String? = null,
        val rootFolder: DisplayTreeFolder = DisplayTreeFolder(
            displayFolder = null,
            displayName = null,
            totalUnreadCount = 0,
            totalStarredCount = 0,
            children = persistentListOf(),
        ),
        val folders: ImmutableList<DisplayFolder> = persistentListOf(),
        val selectedFolderId: String? = null,
        val selectedFolder: DisplayFolder? = null,
        val showAccountSelection: Boolean = false,
        val isLoading: Boolean = false,
    )

    sealed interface Event {
        data class SelectAccount(val accountId: String?) : Event
        data class SelectFolder(val folderId: String?) : Event
        data class OnAccountClick(val account: DisplayAccount) : Event
        data class OnAccountViewClick(val account: DisplayAccount) : Event
        data class OnFolderClick(val folder: DisplayFolder) : Event
        data object OnAccountSelectorClick : Event
        data object OnManageFoldersClick : Event
        data object OnNewMessageListClick : Event
        data object OnSettingsClick : Event
        data object OnSyncAccount : Event
        data object OnSyncAllAccounts : Event
        data object OnAddAccountClick : Event
    }

    sealed interface Effect {
        data class OpenAccount(val accountId: String) : Effect
        data class OpenFolder(val accountId: String, val folderId: Long) : Effect
        data object OpenUnifiedFolder : Effect
        data object OpenManageFolders : Effect
        data object OpenNewMessageList : Effect
        data object OpenSettings : Effect
        data object OpenAddAccount : Effect
        data object CloseDrawer : Effect
    }
}
