package net.thunderbird.feature.mail.message.list.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.common.mvi.observe
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonIcon
import app.k9mail.core.ui.compose.designsystem.atom.icon.Icons
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
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

@Composable
internal fun MessageList(
    modifier: Modifier = Modifier,
    viewModel: MessageListContract.ViewModel = koinViewModel(),
) {
    val (state, dispatchEvent) = viewModel.observe { effect ->
        // TODO.
    }

    MessageList(
        state = state.value,
        featureFlagProvider = koinInject(),
        drawerViewModel = koinViewModel<DrawerContract.ViewModel>(),
        modifier = modifier,
        onOpenAccount = { },
        onOpenFolder = { accountId, folderId -> },
        onOpenUnifiedFolder = { },
        onOpenManageFolders = { },
        onOpenNewMessageList = { },
        onOpenSettings = { },
        onCloseDrawer = { },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageList(
    state: MessageListContract.State,
    featureFlagProvider: FeatureFlagProvider,
    drawerViewModel: DrawerContract.ViewModel,
    modifier: Modifier = Modifier,
    onOpenAccount: (accountId: String) -> Unit = {},
    onOpenFolder: (accountId: String, folderId: Long) -> Unit = { _, _ -> },
    onOpenUnifiedFolder: () -> Unit = {},
    onOpenManageFolders: () -> Unit = {},
    onOpenNewMessageList: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCloseDrawer: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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
                CenterAlignedTopAppBar(
                    title = {
                        Text("Thunderbird")
                    },
                    navigationIcon = {
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
                    },
                )
            },
            modifier = modifier,
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentPadding = PaddingValues(MainTheme.spacings.double),
            ) {
                if (state.groups.isEmpty()) {
                    item {
                        TextHeadlineLarge("Nothing to show here.")
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
                            read = message.isRead,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(MainTheme.spacings.double))
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
                    isRead = Random.nextBoolean(),
                    threadCount = Random.nextInt(10),
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
                groups = groups,
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
