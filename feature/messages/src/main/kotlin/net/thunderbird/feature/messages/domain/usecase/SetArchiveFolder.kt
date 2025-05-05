package net.thunderbird.feature.messages.domain.usecase

import com.fsck.k9.mail.FolderType
import com.fsck.k9.mail.MessagingException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.backend.api.BackendStorageFactory
import net.thunderbird.core.account.BaseAccount
import net.thunderbird.core.account.BaseAccountManager
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.folder.api.RemoteFolder
import net.thunderbird.feature.messages.domain.DomainContract
import net.thunderbird.feature.messages.domain.SetAccountFolderOutcome

internal class SetArchiveFolder(
    private val baseAccountManager: BaseAccountManager<BaseAccount>,
    private val backendStorageFactory: BackendStorageFactory<BaseAccount>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DomainContract.UseCase.SetArchiveFolder {
    override suspend fun invoke(
        accountUuid: String,
        folder: RemoteFolder,
    ): Outcome<SetAccountFolderOutcome.Success, SetAccountFolderOutcome.Error> {
        val account = withContext(ioDispatcher) {
            baseAccountManager.getAccount(accountUuid)
        } ?: return Outcome.Failure(SetAccountFolderOutcome.Error.AccountNotFound)

        val backend = backendStorageFactory.createBackendStorage(account)
        return backend
            .createFolderUpdater()
            .use { updater ->
                try {
                    withContext(ioDispatcher) {
                        updater.changeFolder(folderServerId = folder.serverId, name = folder.name, FolderType.ARCHIVE)
                    }
                    Outcome.success(SetAccountFolderOutcome.Success)
                } catch (e: MessagingException) {
                    Outcome.Failure(SetAccountFolderOutcome.Error.UnhandledError(throwable = e))
                }
            }
    }
}
