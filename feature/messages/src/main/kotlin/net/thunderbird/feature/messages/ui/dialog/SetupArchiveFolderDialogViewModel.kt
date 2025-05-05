package net.thunderbird.feature.messages.ui.dialog

import androidx.lifecycle.viewModelScope
import app.k9mail.core.ui.compose.common.mvi.BaseViewModel
import com.fsck.k9.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.thunderbird.core.outcome.handle
import net.thunderbird.core.outcome.handleAsync
import net.thunderbird.feature.folder.api.RemoteFolder
import net.thunderbird.feature.messages.domain.CreateArchiveFolderOutcome
import net.thunderbird.feature.messages.domain.DomainContract.UseCase
import net.thunderbird.feature.messages.domain.SetAccountFolderOutcome
import net.thunderbird.feature.messages.ui.dialog.SetupArchiveFolderDialogContract.Effect
import net.thunderbird.feature.messages.ui.dialog.SetupArchiveFolderDialogContract.Event
import net.thunderbird.feature.messages.ui.dialog.SetupArchiveFolderDialogContract.State

internal class SetupArchiveFolderDialogViewModel(
    private val accountUuid: String,
    private val logger: Logger,
    private val getAccountFolders: UseCase.GetAccountFolders,
    private val createArchiveFolder: UseCase.CreateArchiveFolder,
    private val setArchiveFolder: UseCase.SetArchiveFolder,
) : BaseViewModel<State, Event, Effect>(
    initialState = State.EmailCantBeArchived(),
), SetupArchiveFolderDialogContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            Event.MoveNext -> onNext(state = state.value)

            Event.OnDoneClicked -> onDoneClicked(state = state.value)

            Event.OnDismissClicked -> onDismissClicked()

            is Event.OnDoNotShowDialogAgainChanged -> onDoNotShowDialogAgainChanged(isChecked = event.isChecked)

            is Event.OnCreateFolderClicked -> onCreateFolderClicked(newFolderName = event.newFolderName)

            is Event.OnFolderSelected -> onFolderSelected(folder = event.folder)
        }
    }

    private fun onNext(state: State) {
        when (state) {
            is State.ChooseArchiveFolder -> updateState {
                State.CreateArchiveFolder(folderName = "")
            }

            is State.EmailCantBeArchived -> {
                updateState { State.ChooseArchiveFolder(isLoadingFolders = true) }
                viewModelScope.launch {
                    getAccountFolders(accountUuid = accountUuid).handle(
                        onSuccess = { folders ->
                            updateState {
                                State.ChooseArchiveFolder(
                                    isLoadingFolders = false,
                                    folders = folders,
                                )
                            }
                        },
                        onFailure = { error ->
                            updateState {
                                State.ChooseArchiveFolder(
                                    isLoadingFolders = false,
                                    errorMessage = error.exception.message,
                                )
                            }
                        },
                    )
                }
            }

            else -> error("The '$state' state doesn't support the MoveNext event")
        }
    }

    private fun onDoneClicked(state: State) {
        check(state is State.ChooseArchiveFolder) { "The '$state' state doesn't support the OnDoneClicked event" }
        checkNotNull(state.selectedFolder) {
            "The selected folder is null. This should not happen."
        }

        viewModelScope.launch {
            setArchiveFolder(accountUuid = accountUuid, folder = state.selectedFolder).handle(
                onSuccess = {
                    updateState { State.Closed }
                    emitEffect(Effect.DismissDialog)
                },
                onFailure = { error ->
                    updateState {
                        when (error) {
                            SetAccountFolderOutcome.Error.AccountNotFound ->
                                state.copy(
                                    errorMessage = "Couldn't find an account associated to the '$accountUuid' UUID",
                                )

                            is SetAccountFolderOutcome.Error.UnhandledError -> state.copy(
                                errorMessage = "Unhandled error: ${error.throwable.message}",
                            )
                        }
                    }
                },
            )
        }
    }

    private fun onDismissClicked() {
        updateState { State.Closed }
        emitEffect(Effect.DismissDialog)
    }

    private fun onDoNotShowDialogAgainChanged(isChecked: Boolean) {
        updateState { state ->
            when (state) {
                is State.EmailCantBeArchived -> state.copy(isDoNotShowDialogAgainChecked = isChecked)
                else -> state
            }
        }
    }

    private fun onCreateFolderClicked(newFolderName: String) {
        updateState { state ->
            when (state) {
                is State.CreateArchiveFolder -> state.copy(
                    folderName = newFolderName,
                    syncingMessage = "Syncing...",
                    errorMessage = null,
                )

                else -> state
            }
        }

        createArchiveFolder(accountUuid = accountUuid, folderName = newFolderName)
            .onEach { outcome ->
                outcome.handleAsync(
                    onSuccess = ::onCreateArchiveFolderSuccess,
                    onFailure = ::onCreateArchiveFolderError,
                )
            }
            .launchIn(viewModelScope)
    }

    private suspend fun onCreateArchiveFolderSuccess(event: CreateArchiveFolderOutcome.Success) {
        when (event) {
            CreateArchiveFolderOutcome.Success.LocalFolderCreated -> {
                updateState { state ->
                    when (state) {
                        is State.CreateArchiveFolder -> state.copy(
                            syncingMessage = "Local folder created. Starting synchronization...",
                        )

                        else -> state
                    }
                }
                logger.d("Folder created")
            }

            CreateArchiveFolderOutcome.Success.Created -> {
                updateState { state ->
                    when (state) {
                        is State.CreateArchiveFolder -> state.copy(
                            syncingMessage = "Remote folder created.",
                        )

                        else -> state
                    }
                }
                delay(1.milliseconds)
                updateState { State.Closed }
                emitEffect(Effect.DismissDialog)
                logger.d("Sync finished")
            }

            is CreateArchiveFolderOutcome.Success.SyncStarted -> {
                updateState { state ->
                    when (state) {
                        is State.CreateArchiveFolder -> state.copy(
                            syncingMessage = "Creating folder on email provider...",
                        )

                        else -> state
                    }
                }
                logger.d("Started sync for ${event.serverId}")
            }
        }
    }

    private fun onCreateArchiveFolderError(error: CreateArchiveFolderOutcome.Error) {
        val errorMessage = when (error) {
            CreateArchiveFolderOutcome.Error.AccountNotFound ->
                "Account ($accountUuid) not found".also(logger::e)

            is CreateArchiveFolderOutcome.Error.SyncError.Failed ->
                "Failed sync for folder ${error.serverId}. Message: ${error.message}".also {
                    logger.e(
                        error.exception,
                        it,
                    )
                }

            is CreateArchiveFolderOutcome.Error.UnhandledError -> "Unhandled error".also {
                logger.e(error.throwable, it)
            }

            is CreateArchiveFolderOutcome.Error.InvalidFolderName -> when {
                error.folderName.isBlank() -> "Folder name cannot be blank"
                else -> "Invalid folder name '${error.folderName}'"
            }

        }

        updateState { state ->
            when (state) {
                is State.CreateArchiveFolder -> state.copy(
                    errorMessage = errorMessage,
                    syncingMessage = null,
                )

                else -> state
            }
        }
    }

    private fun onFolderSelected(folder: RemoteFolder) {
        updateState { state ->
            when (state) {
                is State.ChooseArchiveFolder -> state.copy(selectedFolder = folder)
                else -> state
            }
        }
    }
}
