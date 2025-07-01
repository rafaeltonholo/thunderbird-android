package net.thunderbird.feature.mail.message.list.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import app.k9mail.core.ui.compose.common.mvi.observe
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonIcon
import app.k9mail.core.ui.compose.designsystem.atom.icon.Icons
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleSmall
import app.k9mail.core.ui.compose.designsystem.molecule.LoadingView
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.core.ui.compose.theme2.thunderbird.ThunderbirdTheme2
import io.github.serpro69.kfaker.Faker
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import net.thunderbird.core.featureflag.FeatureFlagProvider
import net.thunderbird.core.featureflag.FeatureFlagResult
import net.thunderbird.feature.mail.message.list.domain.model.Message
import net.thunderbird.feature.mail.message.list.domain.model.MessageGroup
import net.thunderbird.feature.mail.message.list.domain.model.MessageIdentity
import net.thunderbird.feature.mail.message.list.domain.model.UserAccount
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerView
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val LAZY_COLUMN_KEY_FOLDER_NAME =
    "net.thunderbird.feature.mail.message.list.ui.LAZY_COLUMN_KEY_FOLDER_NAME"
private const val LAZY_COLUMN_KEY_ACCOUNT_NAME =
    "net.thunderbird.feature.mail.message.list.ui.LAZY_COLUMN_KEY_ACCOUNT_NAME"

@Composable
internal fun MessageList(
    modifier: Modifier = Modifier,
    viewModel: MessageListContract.ViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val (state, dispatchEvent) = viewModel.observe { effect ->
        when (effect) {
            MessageListContract.Effect.CloseDrawer -> scope.launch {
                drawerState.close()
            }
        }
    }


    MessageList(
        state = state.value,
        featureFlagProvider = koinInject(),
        drawerViewModel = koinViewModel<DrawerContract.ViewModel>(),
        modifier = modifier,
        drawerState = drawerState,
        onOpenAccount = { },
        onOpenFolder = { accountId, folderId ->
            dispatchEvent(
                MessageListContract.Event.LoadFolderMessage(
                    accountId,
                    folderId,
                ),
            )
        },
        onOpenUnifiedFolder = { },
        onOpenManageFolders = { },
        onOpenNewMessageList = { },
        onOpenSettings = { },
        onCloseDrawer = { },
    )
}

data class MessageListToolbarState(
    val shouldShowFolderName: Boolean,
    val shouldShowAccountName: Boolean,
    val isFolderNameVisible: Boolean,
    val isAccountNameVisible: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MessageList(
    state: MessageListContract.State,
    featureFlagProvider: FeatureFlagProvider,
    drawerViewModel: DrawerContract.ViewModel,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    onOpenAccount: (accountId: String) -> Unit = {},
    onOpenFolder: (accountId: String, folderId: Long) -> Unit = { _, _ -> },
    onOpenUnifiedFolder: () -> Unit = {},
    onOpenManageFolders: () -> Unit = {},
    onOpenNewMessageList: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCloseDrawer: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val toolbarState by remember(state, lazyListState) {
        derivedStateOf {
            val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
            MessageListToolbarState(
                shouldShowFolderName = state.folderName.orEmpty().isNotBlank(),
                shouldShowAccountName = state.accountName.orEmpty().isNotBlank(),
                isFolderNameVisible = visibleItemsInfo.fastAny { it.key == LAZY_COLUMN_KEY_FOLDER_NAME },
                isAccountNameVisible = visibleItemsInfo.fastAny { it.key == LAZY_COLUMN_KEY_FOLDER_NAME },
            )

        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerView(
                drawerState = state.drawerState,
                openAccount = onOpenAccount,
                openFolder = onOpenFolder,
                openUnifiedFolder = onOpenUnifiedFolder,
                openManageFolders = onOpenManageFolders,
                openNewMessageList = onOpenNewMessageList,
                openSettings = onOpenSettings,
                closeDrawer = onCloseDrawer,
                featureFlagProvider = featureFlagProvider,
                viewModel = drawerViewModel,
            )
        },
    ) {
        Scaffold(
            topBar = {
                val navigationIcon = remember {
                    movableContentOf {
                        ButtonIcon(
                            onClick = {
                                scope.launch {
                                    drawerState.apply {
                                        if (isClosed) open() else close()
                                    }
                                }
                            },
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Menu",
                        )
                    }
                }
                TopAppBar(
                    title = {
                        SharedTransitionLayout {
                            AnimatedContent(toolbarState) { toolbarState ->
                                with(toolbarState) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        when {
                                            !state.isLoading && (isFolderNameVisible || isAccountNameVisible) ->
                                                Text(
                                                    text = "Thunderbird",
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .offset(x = (-24).dp)
                                                        .sharedBounds(
                                                            sharedContentState = rememberSharedContentState(key = "toolbar_title"),
                                                            animatedVisibilityScope = this@AnimatedContent,
                                                        ),
                                                )

                                            else ->
                                                Column {
                                                    TextTitleLarge(
                                                        text = state.folderName.orEmpty(),
                                                        modifier = Modifier.sharedBounds(
                                                            sharedContentState = rememberSharedContentState(key = "toolbar_title"),
                                                            animatedVisibilityScope = this@AnimatedContent,
                                                        ),
                                                    )
                                                    if (!toolbarState.isAccountNameVisible) {
                                                        TextLabelSmall(
                                                            text = state.accountName.orEmpty(),
                                                        )
                                                    }
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = navigationIcon,
                )
            },
            modifier = modifier,
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentPadding = PaddingValues(MainTheme.spacings.double),
                state = lazyListState,
            ) {

                item(key = LAZY_COLUMN_KEY_FOLDER_NAME) {
                    AnimatedVisibility(
                        visible = toolbarState.shouldShowFolderName,
                    ) {
                        TextHeadlineLarge(
                            text = state
                                .folderName
                                .orEmpty(),

                            )
                    }
                }

                item(key = LAZY_COLUMN_KEY_ACCOUNT_NAME) {
                    AnimatedVisibility(
                        visible = toolbarState.shouldShowAccountName,
                    ) {
                        TextTitleSmall(
                            text = state.accountName.orEmpty(),
                        )
                    }
                }

                if (state.folderName.isNullOrBlank().not() || state.accountName.isNullOrBlank().not()) {
                    item {
                        Spacer(modifier = Modifier.height(MainTheme.spacings.double))
                    }
                }

                if (state.isLoading.not() && state.groups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillParentMaxHeight(fraction = 0.8f),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextHeadlineLarge(
                                text = "Nothing to show here.",
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                for (group in state.groups) {
                    item {
                        val formatter = remember {
                            LocalDate.Format {
                                monthName(MonthNames.ENGLISH_ABBREVIATED)
                                char(' ')
                                dayOfMonth()
                            }
                        }
                        val period = remember(group.date) {
                            formatter.format(group.date)
                        }
                        TextLabelMedium(
                            text = period,
                            color = MainTheme.colors.onSurface.copy(alpha = 0.5f),
                        )

                        Spacer(modifier = Modifier.height(MainTheme.spacings.default))
                    }

                    itemsIndexed(group.messages) { index, message ->
                        val roundCorner = 4.dp
                        MessageItem(
                            account = group.accounts.getValue(message.account.id),
                            subject = message.subject,
                            contentPreview = message.contentPreview,
                            dateTime = message.dateTime,
                            from = message.from,
                            shape = when (index) {
                                0 if group.messages.size == 1 -> RoundedCornerShape(size = MainTheme.sizes.smaller)

                                0 -> RoundedCornerShape(
                                    topStart = MainTheme.sizes.smaller,
                                    topEnd = MainTheme.sizes.smaller,
                                    bottomStart = roundCorner,
                                    bottomEnd = roundCorner,
                                )

                                group.messages.lastIndex -> RoundedCornerShape(
                                    topStart = roundCorner,
                                    topEnd = roundCorner,
                                    bottomStart = MainTheme.sizes.smaller,
                                    bottomEnd = MainTheme.sizes.smaller,
                                )

                                else -> RoundedCornerShape(roundCorner)
                            },
                            read = message.read,
                            favourite = message.starred,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(MainTheme.spacings.double))
                    }
                }

                item {
                    Crossfade(state.isLoading) { isLoading ->
                        if (isLoading) {
                            LoadingView()
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun Preview() {
    ThunderbirdTheme2 {
        val faker = remember { Faker() }
        val messages = remember {
            List(size = 100) {
                Message(
                    account = if (Random.nextBoolean()) {
                        UserAccount(
                            id = "id",
                            name = "Rafael",
                            email = "rtonholo@dev.com",
                            color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                        )
                    } else {
                        UserAccount(
                            id = "id2",
                            name = "Tonholo",
                            email = "tonholo@rtonholo.com",
                            color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                        )
                    },
                    subject = faker.space.galaxy(),
                    contentPreview = faker.lorem.words(),
                    dateTime = run {
                        val todayInstant = Clock.System.now()
                        val minRange = (todayInstant - 30.days)
                        val randomEpoch = Random.nextLong(
                            minRange.toEpochMilliseconds()..todayInstant.toEpochMilliseconds(),
                        )
                        Instant
                            .fromEpochMilliseconds(randomEpoch)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                    },
                    from = persistentListOf(
                        MessageIdentity(
                            email = faker.internet.email(),
                            color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                            avatarUrl = null,
                        ),
                    ),
                    recipients = persistentListOf(
                        MessageIdentity(
                            email = faker.internet.email(),
                            color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                            avatarUrl = null,
                        ),
                    ),
                    read = Random.nextBoolean(),
                    threadCount = Random.nextInt(10),
                    starred = Random.nextBoolean(),
//                    swipeActions = SwipeActions(
//                        leftAction = SwipeAction.Archive,
//                        rightAction = SwipeAction.Spam,
//                    ),
                )
            }
        }
        val groups = remember {
            messages
                .sortedByDescending { it.dateTime }
                .groupBy { it.dateTime.date }
                .map { (key, value) ->
                    MessageGroup(
                        date = key,
                        messages = value.toPersistentList(),
                        accounts = messages
                            .associate { it.account.id to it.account }
                            .toPersistentMap(),
                    )
                }
                .toPersistentList()
        }
        MessageList(
            state = MessageListContract.State(
                isLoading = false,
                folderName = "Inbox",
                accountName = "Unified Account",
                groups = persistentListOf(),
//                groups = groups,
            ),
            featureFlagProvider = {
                FeatureFlagResult.Enabled
            },
            drawerViewModel = object : DrawerContract.ViewModel(initialState = DrawerContract.State()) {
                override fun event(event: DrawerContract.Event) = Unit
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
