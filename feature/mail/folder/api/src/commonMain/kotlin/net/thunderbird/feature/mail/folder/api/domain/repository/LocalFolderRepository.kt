package net.thunderbird.feature.mail.folder.api.domain.repository

import net.thunderbird.feature.mail.account.api.BaseAccount
import net.thunderbird.feature.mail.folder.api.Folder
import net.thunderbird.feature.mail.folder.api.FolderDetails

interface LocalFolderRepository<TAccount : BaseAccount> {
    @Deprecated(
        message = "Use getLocalFolder instead",
        replaceWith = ReplaceWith(expression = "getLocalFolder(account, folderId)"),
    )
    fun getFolder(account: TAccount, folderId: Long): Folder?
    fun getLocalFolder(account: TAccount, folderId: Long): Folder? = getFolder(account, folderId)

    @Deprecated(
        message = "Use getLocalFolderDetails instead",
        replaceWith = ReplaceWith(expression = "getLocalFolderDetails(account, folderId)"),
    )
    fun getFolderDetails(account: TAccount, folderId: Long): FolderDetails?
    fun getLocalFolderDetails(account: TAccount, folderId: Long): FolderDetails? =
        getFolderDetails(account, folderId)

    @Deprecated(
        message = "Use getLocalFolderId instead",
        replaceWith = ReplaceWith(expression = "getLocalFolderId(account, folderServerId)"),
    )
    fun getFolderId(account: TAccount, folderServerId: String): Long?
    fun getLocalFolderId(account: TAccount, folderServerId: String): Long? = getFolderId(account, folderServerId)
}
