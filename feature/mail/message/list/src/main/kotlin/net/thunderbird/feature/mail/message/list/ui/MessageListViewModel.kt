package net.thunderbird.feature.mail.message.list.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import app.k9mail.legacy.mailstore.MessageDetailsAccessor
import app.k9mail.legacy.mailstore.MessageListRepository
import app.k9mail.legacy.mailstore.MessageMapper
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
import net.thunderbird.feature.mail.message.list.R
import net.thunderbird.feature.mail.message.list.domain.model.Message
import net.thunderbird.feature.mail.message.list.domain.model.MessageGroup
import net.thunderbird.feature.mail.message.list.domain.model.MessageIdentity
import net.thunderbird.feature.mail.message.list.domain.model.UserAccount
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainImmediateDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : MessageListContract.ViewModel(
    initialState = MessageListContract.State(),
) {

    init {
        viewModelScope.launch {
            updateState { it.copy(isLoadingMore = true) }
            fetchAccounts()
                .flowOn(ioDispatcher)
                .flatMapConcat { accounts -> fetchMessages(accounts) }
                .flowOn(ioDispatcher)
                .flatMapConcat { messages -> createMessageGroups(messages) }
                .flowOn(mainImmediateDispatcher)
                .onEach { groups ->
                    logger.debug(TAG) { "onEach(groups) called with: groups = $groups" }
                    updateState { it.copy(groups = groups.toPersistentList(), isLoadingMore = false) }
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

    override fun event(event: MessageListContract.Event) {
        TODO("Not yet implemented")
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
                        messageMapper = uiMessageMapperFactory.create(account, null),
                    )
                },
            )
        }
    }

    private fun createMessageGroups(messages: List<Message>): Flow<List<MessageGroup>> {
        logger.debug(TAG) { "createMessageGroups() called with: messages = $messages" }
        return flowOf(
            messages
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
                .toPersistentList(),
        )
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
                    color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)), // TODO.
                    avatarUrl = null,
                )
            }.toPersistentList(),
            recipients = message.toAddresses.map { address ->
                MessageIdentity(
                    email = address.address,
                    color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)), // TODO.
                    avatarUrl = null,
                )
            }.toPersistentList(),
            isRead = message.isRead,
            threadCount = message.threadCount,
            //        swipeActions = buildSwipeActions(
            //            accountUuids = setOf(account.uuid),
            //            isIncomingServerPop3 = { false }, // TODO.
            //            hasArchiveFolder = { true }, // TODO.
            //        ).firstNotNullOf { it.value },
        ).also {
            logger.debug(TAG) { "map() mapped message to: $it" }
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
