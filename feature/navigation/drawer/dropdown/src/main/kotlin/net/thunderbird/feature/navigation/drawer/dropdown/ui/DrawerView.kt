package net.thunderbird.feature.navigation.drawer.dropdown.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.k9mail.core.ui.compose.common.mvi.observe
import app.k9mail.core.ui.compose.designsystem.molecule.PullToRefreshBox
import net.thunderbird.core.featureflag.FeatureFlagKey
import net.thunderbird.core.featureflag.FeatureFlagProvider
import net.thunderbird.core.featureflag.FeatureFlagResult
import net.thunderbird.feature.navigation.drawer.dropdown.FolderDrawerState
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.Effect
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.Event
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.ViewModel
import net.thunderbird.feature.navigation.drawer.siderail.ui.SideRailDrawerContent
import org.koin.androidx.compose.koinViewModel

@Suppress("LongParameterList")
@Composable
fun DrawerView(
    drawerState: FolderDrawerState,
    openAccount: (accountId: String) -> Unit,
    openFolder: (accountId: String, folderId: Long) -> Unit,
    openUnifiedFolder: () -> Unit,
    openManageFolders: () -> Unit,
    openNewMessageList: () -> Unit,
    openSettings: () -> Unit,
    openAddAccount: () -> Unit,
    closeDrawer: () -> Unit,
    featureFlagProvider: FeatureFlagProvider,
    viewModel: ViewModel = koinViewModel<ViewModel>(),
) {
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            is Effect.OpenAccount -> openAccount(effect.accountId)
            is Effect.OpenFolder -> openFolder(
                effect.accountId,
                effect.folderId,
            )

            Effect.OpenUnifiedFolder -> openUnifiedFolder()
            is Effect.OpenManageFolders -> openManageFolders()
            is Effect.OpenNewMessageList -> openNewMessageList()
            is Effect.OpenSettings -> openSettings()
            Effect.OpenAddAccount -> openAddAccount()
            Effect.CloseDrawer -> closeDrawer()
        }
    }

    val isDropdownDrawerEnabled = remember {
        featureFlagProvider.provide(FeatureFlagKey("enable_dropdown_drawer_ui")) == FeatureFlagResult.Enabled
    }

    LaunchedEffect(drawerState.selectedAccountUuid) {
        dispatch(Event.SelectAccount(drawerState.selectedAccountUuid))
    }

    LaunchedEffect(drawerState.selectedFolderId) {
        dispatch(Event.SelectFolder(drawerState.selectedFolderId))
    }

    PullToRefreshBox(
        isRefreshing = state.value.isLoading,
        onRefresh = { dispatch(Event.OnSyncAccount) },
    ) {
        if (isDropdownDrawerEnabled) {
            DrawerContent(
                state = state.value,
                onEvent = { dispatch(it) },
            )
        } else {
            SideRailDrawerContent(
                state = state.value,
                onEvent = { dispatch(it) },
            )
        }
    }
}
