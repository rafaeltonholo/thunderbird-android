package net.thunderbird.feature.mail.message.list.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import app.k9mail.legacy.mailstore.MessageDetailsAccessor
import app.k9mail.legacy.mailstore.MessageListRepository
import app.k9mail.legacy.mailstore.MessageMapper
import com.fsck.k9.mail.Address
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.profile.AccountProfile
import net.thunderbird.feature.account.profile.AccountProfileRepository
import net.thunderbird.feature.mail.account.api.AccountManager
import net.thunderbird.feature.mail.account.api.BaseAccount
import net.thunderbird.feature.mail.folder.api.domain.repository.LocalFolderRepository
import net.thunderbird.feature.mail.message.list.R
import net.thunderbird.feature.mail.message.list.domain.model.Message
import net.thunderbird.feature.mail.message.list.domain.model.MessageGroup
import net.thunderbird.feature.mail.message.list.domain.model.MessageIdentity
import net.thunderbird.feature.mail.message.list.domain.model.UserAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.createMailDisplayAccountFolderId
import net.thunderbird.feature.search.LocalMessageSearch
import net.thunderbird.feature.search.SearchAccount
import net.thunderbird.feature.search.sql.SqlWhereClause

private const val TAG = "MessageListViewModel"

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
internal class MessageListViewModel(
    private val logger: Logger,
    private val accountManager: AccountManager<BaseAccount>,
    private val messageListRepository: MessageListRepository,
    private val uiMessageMapperFactory: UiMessageMapper.Factory,
    private val stringsResourceManager: StringsResourceManager,
    private val accountProfileRepository: AccountProfileRepository,
    private val localFolderRepository: LocalFolderRepository<BaseAccount>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainImmediateDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : MessageListContract.ViewModel(
    initialState = MessageListContract.State(folderName = "Inbox"),
) {

    init {
        fetchUnifiedInbox()
    }

    override fun event(event: MessageListContract.Event) {
        when (event) {
            MessageListContract.Event.LoadMore -> TODO()
            is MessageListContract.Event.OnFavoriteClick -> TODO()
            is MessageListContract.Event.OnOpenFolderClick -> onFolderClick(event)
            is MessageListContract.Event.OnSwipeLeft -> TODO()
            is MessageListContract.Event.OnSwipeRight -> TODO()
            is MessageListContract.Event.OnOpenAccountClick -> onOpenAccountClick(event)
        }
    }

    private fun onFolderClick(event: MessageListContract.Event.OnOpenFolderClick) {
        fetchMessages(
            accountUuid = event.accountId,
            folderId = event.folderId,
        )
        emitEffect(MessageListContract.Effect.CloseDrawer)
    }

    private fun onOpenAccountClick(event: MessageListContract.Event.OnOpenAccountClick) {
        logger.debug(TAG) { "onOpenAccountClick() called with: event = $event" }
        val accountUuid = event.accountUuid
        updateState { state ->
            state.copy(
                drawerState = state.drawerState.copy(
                    selectedAccountUuid = accountUuid,
                ),
            )
        }

        when (accountUuid) {
            UnifiedDisplayAccount.UNIFIED_ACCOUNT_ID -> fetchUnifiedInbox()
            else -> fetchMessages(accountUuid = accountUuid, folderId = 2) // 2 = Inbox
        }
    }

    private fun fetchUnifiedInbox() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            fetchAccounts()
                .flowOn(ioDispatcher)
                .onEach { accounts ->
                    when (accounts.size) {
                        1 -> {
                            val account = accounts.first()
                            updateState {
                                it.copy(
                                    accountName = account.name ?: account.email,
                                    showAccountColorIndicator = false,
                                )
                            }
                        }

                        else -> updateState { it.copy(accountName = "Unified Account") }
                    }
                }
                .flatMapConcat { accounts -> fetchMessages(accounts) }
                .flowOn(ioDispatcher)
                .flatMapConcat { messages -> flowOf(createMessageGroups(messages)) }
                .flowOn(mainImmediateDispatcher)
                .onEach { groups ->
                    logger.debug(TAG) { "onEach(groups) called with: groups = $groups" }
                    updateState { it.copy(groups = groups.toPersistentList(), isLoading = false) }
                }
                .flatMapConcat { groups ->
                    fetchProfiles(
                        accountIds = groups
                            .flatMap { group ->
                                group
                                    .accounts
                                    .filterValues { it.color == Color.Transparent }
                                    .keys.map { AccountIdFactory.of(it) }
                            }
                            .toSet(),
                    )
                }
                .flowOn(ioDispatcher)
                .onEach { profiles ->
                    logger.debug(TAG) { "onEach(profiles) called with: profiles = $profiles" }
                    updateState { state ->
                        state.copy(
                            groups = state
                                .groups
                                .map { group ->
                                    val userAccounts = profiles.mapNotNull { profile ->
                                        group.accounts[profile.id.value.toString()]?.copy(
                                            color = Color(profile.color),
                                        )
                                    }

                                    when {
                                        userAccounts.isEmpty() -> group
                                        else -> group.copy(
                                            accounts = buildMap {
                                                putAll(group.accounts)
                                                putAll(userAccounts.map { it.id to it })
                                            }.toImmutableMap(),
                                        )
                                    }
                                }
                                .toPersistentList(),
                        )
                    }
                }
                .flowOn(mainImmediateDispatcher)
                .launchIn(viewModelScope)
        }
    }

    private fun fetchAccounts(): Flow<List<BaseAccount>> = flow {
        emit(accountManager.getAccounts())
    }

    private fun fetchMessages(accounts: List<BaseAccount>): Flow<List<Message>> {
        logger.debug(TAG) { "fetchMessages() called with: accounts = $accounts" }
        return flow {
            emit(
                accounts.flatMap { account ->
                    val whereClause = SqlWhereClause
                        .Builder()
                        .withConditions(
                            node = SearchAccount
                                .createUnifiedInboxAccount(
                                    unifiedInboxTitle = stringsResourceManager.stringResource(
                                        resourceId = R.string.integrated_inbox_title,
                                    ),
                                    unifiedInboxDetail = stringsResourceManager.stringResource(
                                        resourceId = R.string.integrated_inbox_detail,
                                    ),
                                )
                                .relatedSearch
                                .conditions,
                        )
                        .build()

                    logger.debug(TAG) { "fetchMessages: fetching messages for ${account.uuid}" }
                    messageListRepository.getMessages(
                        accountUuid = account.uuid,
                        selection = whereClause.selection,
                        selectionArgs = whereClause.selectionArgs.toTypedArray(),
                        sortOrder = "internal_date",
                        messageMapper = uiMessageMapperFactory.create(account, profile = null),
                    )
                },
            )
        }
    }

    private fun fetchMessages(accountUuid: String, folderId: Long) {
        logger.debug(TAG) { "fetchMessages() called with: accountUuid = $accountUuid, folderId = $folderId" }
        viewModelScope.launch(ioDispatcher) {
            val account = accountManager.getAccount(accountUuid) ?: return@launch
            val search = LocalMessageSearch().apply {
                addAccountUuid(accountUuid)
                addAllowedFolder(folderId)
            }

            logger.debug(TAG) { "fetchMessages: search = $search" }

            val whereClause = SqlWhereClause
                .Builder()
                .withConditions(node = search.conditions)
                .build()

            val messages = messageListRepository.getMessages(
                accountUuid = accountUuid,
                selection = whereClause.selection,
                selectionArgs = whereClause.selectionArgs.toTypedArray(),
                sortOrder = "internal_date",
                messageMapper = uiMessageMapperFactory.create(account, profile = null),
            )
            logger.debug(TAG) { "fetchMessages: messages = $messages" }

            val folder = localFolderRepository.getLocalFolder(account, folderId)

            logger.debug(TAG) { "fetchMessages: folder = $folder" }

            withContext(mainImmediateDispatcher) {
                val groups = createMessageGroups(messages)

                updateState { state ->
                    state.copy(
                        accountName = account.name,
                        folderName = folder?.name,
                        groups = groups.toPersistentList(),
                        drawerState = state.drawerState.copy(
                            selectedFolderId = createMailDisplayAccountFolderId(account.uuid, folderId),
                        ),
                        showAccountColorIndicator = false,
                    )
                }
            }
        }
    }

    private fun createMessageGroups(messages: List<Message>): List<MessageGroup> {
        logger.debug(TAG) { "createMessageGroups() called with: messages = $messages" }
        return messages
            .sortedByDescending { it.dateTime }
            .groupBy { it.dateTime.date }
            .map { (dateTime, messages) ->
                MessageGroup(
                    date = dateTime,
                    messages = messages.toPersistentList(),
                    accounts = messages
                        .associate { it.account.id to it.account }
                        .toImmutableMap(),
                )
            }
            .toPersistentList()
    }

    private fun fetchProfiles(accountIds: Set<AccountId>): Flow<List<AccountProfile>> {
        logger.debug(TAG) { "fetchProfile() called with: accountIds = $accountIds" }
        val map = accountIds
            .map { accountId ->
                accountProfileRepository
                    .getById(accountId = accountId)
                    .onEach { profile -> logger.debug(TAG) { "fetchMessages: profile = $profile" } }
                    .filterNotNull()
            }

        return combine(map) { it.toList() }
    }
}

internal class UiMessageMapper(
//    private val buildSwipeActions: DomainContract.UseCase.BuildSwipeActions<BaseAccount>,
    private val logger: Logger,
    private val account: BaseAccount,
    private val profile: AccountProfile?,
) : MessageMapper<Message> {
    private val addressColors = mutableMapOf<String, Color>()
    override fun map(message: MessageDetailsAccessor): Message {
        logger.debug(TAG) { "map() called with: message = $message" }
        return Message(
            account = UserAccount(
                id = account.uuid,
                name = account.name ?: account.email,
                email = account.email,
                color = profile?.color?.let(::Color) ?: Color.Transparent,
            ),
            subject = message.subject ?: "No subject",
            contentPreview = message.preview.previewText,
            dateTime = Instant
                .fromEpochMilliseconds(message.messageDate)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
            from = message.fromAddresses.map { address ->
                MessageIdentity(
                    email = address.address,
                    color = address.color,
                    avatarUrl = null,
                )
            }.toPersistentList(),
            recipients = message.toAddresses.map { address ->
                MessageIdentity(
                    email = address.address,
                    color = address.color,
                    avatarUrl = null,
                )
            }.toPersistentList(),
            read = message.isRead,
            threadCount = message.threadCount,
            starred = message.isStarred,

            //        swipeActions = buildSwipeActions(
            //            accountUuids = setOf(account.uuid),
            //            isIncomingServerPop3 = { false }, // TODO.
            //            hasArchiveFolder = { true }, // TODO.
            //        ).firstNotNullOf { it.value },
        ).also {
            logger.debug(TAG) { "map() mapped message to: $it" }
        }
    }

    private val Address.color: Color get() {
        return addressColors.getOrPut(address) {
            Color(Random.nextLong(0xFF000000, 0xFFFFFFFF))
        }
    }

    class Factory(
        private val logger: Logger,
//        private val buildSwipeActions: DomainContract.UseCase.BuildSwipeActions<BaseAccount>,
    ) {
        fun create(account: BaseAccount, profile: AccountProfile?): UiMessageMapper = UiMessageMapper(
//            buildSwipeActions = buildSwipeActions,
            logger = logger,
            account = account,
            profile = profile,
        )
    }
}
