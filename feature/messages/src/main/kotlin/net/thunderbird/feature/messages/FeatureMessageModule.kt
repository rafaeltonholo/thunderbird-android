package net.thunderbird.feature.messages

import net.thunderbird.feature.messages.domain.DomainContract
import net.thunderbird.feature.messages.domain.usecase.CreateArchiveFolder
import net.thunderbird.feature.messages.domain.usecase.GetAccountFolders
import net.thunderbird.feature.messages.ui.dialog.SetupArchiveFolderDialogFragment
import net.thunderbird.feature.messages.ui.dialog.SetupArchiveFolderDialogFragmentFactory
import net.thunderbird.feature.messages.ui.dialog.SetupArchiveFolderDialogViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val featureMessageModule = module {
    factory<DomainContract.UseCase.GetAccountFolders> { GetAccountFolders(folderRepository = get()) }
    factory<DomainContract.UseCase.CreateArchiveFolder> {
        CreateArchiveFolder()
    }
    viewModel { parameters ->
        SetupArchiveFolderDialogViewModel(
            accountUuid = parameters.get(),
            logger = get(),
            getAccountFolders = get(),
            createArchiveFolder = get(),
        )
    }
    factory<SetupArchiveFolderDialogFragmentFactory> {
        SetupArchiveFolderDialogFragment.Companion
    }
}
