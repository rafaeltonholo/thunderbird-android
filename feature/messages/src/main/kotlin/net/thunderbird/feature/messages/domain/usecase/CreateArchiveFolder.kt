package net.thunderbird.feature.messages.domain.usecase

import com.fsck.k9.backend.api.FolderInfo
import com.fsck.k9.backend.api.updateFolders
import com.fsck.k9.mail.FolderType
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mail.folders.FolderServerId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.thunderbird.backend.api.BackendStorageFactory
import net.thunderbird.backend.api.folder.RemoteFolderCreator
import net.thunderbird.core.account.BaseAccount
import net.thunderbird.core.account.BaseAccountManager
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.core.outcome.handleAsync
import net.thunderbird.feature.messages.domain.CreateArchiveFolderOutcome
import net.thunderbird.feature.messages.domain.DomainContract

class CreateArchiveFolder(
    private val baseAccountManager: BaseAccountManager<BaseAccount>,
    private val backendStorageFactory: BackendStorageFactory<BaseAccount>,
    private val remoteFolderCreatorFactory: RemoteFolderCreator.Factory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DomainContract.UseCase.CreateArchiveFolder {
    override fun invoke(
        accountUuid: String,
        folderName: String,
    ): Flow<Outcome<CreateArchiveFolderOutcome.Success, CreateArchiveFolderOutcome.Error>> = flow {
        if (folderName.isBlank()) {
            emit(Outcome.failure(CreateArchiveFolderOutcome.Error.InvalidFolderName(folderName = folderName)))
            return@flow
        }

        val account = withContext(ioDispatcher) {
            baseAccountManager.getAccount(accountUuid)
        } ?: run {
            emit(Outcome.failure(CreateArchiveFolderOutcome.Error.AccountNotFound))
            return@flow
        }

        val backendStorage = backendStorageFactory.createBackendStorage(account)
        val folderInfo = FolderInfo(
            serverId = folderName,
            name = folderName,
            type = FolderType.ARCHIVE,
        )

        try {
            withContext(ioDispatcher) {
                backendStorage.updateFolders {
                    createFolders(listOf(folderInfo))
                }
            }
            emit(Outcome.success(CreateArchiveFolderOutcome.Success.LocalFolderCreated))
            val serverId = FolderServerId(folderInfo.serverId)
            emit(Outcome.success(CreateArchiveFolderOutcome.Success.SyncStarted(serverId = serverId)))
            val remoteFolderCreator = remoteFolderCreatorFactory.create(account)
            remoteFolderCreator
                .create(folderServerId = serverId, mustCreate = false, folderType = FolderType.ARCHIVE)
                .handleAsync(
                    onSuccess = { result ->
                        emit(Outcome.success(CreateArchiveFolderOutcome.Success.Created))
                    },
                    onFailure = { error ->
                        emit(
                            Outcome.failure(
                                CreateArchiveFolderOutcome.Error.SyncError.Failed(
                                    serverId = serverId,
                                    message = error.toString(),
                                    exception = null,
                                ),
                            ),
                        )
                    },
                )
        } catch (e: MessagingException) {
            emit(Outcome.failure(CreateArchiveFolderOutcome.Error.UnhandledError(throwable = e)))
        }
    }
}
