package net.thunderbird.feature.mail.message.list.ui

import app.k9mail.core.ui.compose.common.mvi.BaseViewModel
import app.k9mail.core.ui.compose.common.mvi.UnidirectionalViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.common.action.SwipeAction
import net.thunderbird.feature.mail.message.list.domain.model.Message
import net.thunderbird.feature.mail.message.list.domain.model.MessageGroup
import net.thunderbird.feature.navigation.drawer.dropdown.FolderDrawerState

interface MessageListContract {
    abstract class ViewModel(
        initialState: State,
    ) : BaseViewModel<State, Event, Effect>(initialState), UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val drawerState: FolderDrawerState = FolderDrawerState(),
        val groups: ImmutableList<MessageGroup> = persistentListOf(),
        val isLoadingMore: Boolean = true,
    )
    sealed interface Event {
        data object LoadMore : Event
        data class OnSwipeLeft(val message: Message, val swipeAction: SwipeAction) : Event
        data class OnSwipeRight(val message: Message, val swipeAction: SwipeAction) : Event
        data class OnFavoriteClick(val message: Message) : Event
    }

    sealed interface Effect
}
