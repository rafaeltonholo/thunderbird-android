package net.thunderbird.feature.mail.message.list.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.core.ui.compose.theme2.thunderbird.ThunderbirdTheme2
import io.github.serpro69.kfaker.Faker
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import net.thunderbird.feature.mail.message.list.domain.model.MessageIdentity
import net.thunderbird.feature.mail.message.list.domain.model.UserAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageList(
    groups: List<MessageGroup>,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Thunderbird")
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
            for (group in groups) {
                stickyHeader {
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
                    MessageItem(
                        account = message.account,
                        subject = message.subject,
                        contentPreview = message.contentPreview,
                        date = message.date,
                        from = message.from,
                        shape = when (index) {
                            0 if group.messages.size == 1 -> RoundedCornerShape(size = MainTheme.sizes.smaller)

                            0 -> RoundedCornerShape(
                                topStart = MainTheme.sizes.smaller,
                                topEnd = MainTheme.sizes.smaller,
                                bottomStart = 2.dp,
                                bottomEnd = 2.dp,
                            )

                            group.messages.lastIndex -> RoundedCornerShape(
                                topStart = 2.dp,
                                topEnd = 2.dp,
                                bottomStart = MainTheme.sizes.smaller,
                                bottomEnd = MainTheme.sizes.smaller,
                            )

                            else -> RoundedCornerShape(2.dp)
                        },
                        read = Random.nextBoolean(),
                        modifier = Modifier.padding(vertical = .5.dp),
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(MainTheme.spacings.double))
                }
            }
        }
    }
}

data class MessageGroup(
    val date: LocalDate,
    val messages: List<Message>,
)

data class Message(
    val account: UserAccount,
    val subject: String,
    val contentPreview: String,
    val date: LocalDate,
    val from: MessageIdentity,
    val recipients: List<MessageIdentity>,
)

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
                            name = "Rafael",
                            email = "rtonholo@dev.com",
                            color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                        )
                    } else {
                        UserAccount(
                            name = "Tonholo",
                            email = "tonholo@rtonholo.com",
                            color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                        )
                    },
                    subject = faker.space.galaxy(),
                    contentPreview = faker.lorem.words(),
                    date = run {
                        val todayInstant = Clock.System.now()
                        val today = todayInstant.toLocalDateTime(TimeZone.UTC).date
                        val minRange = (todayInstant - 30.days).toLocalDateTime(TimeZone.UTC).date
                        val randomEpochDay = Random.nextInt(minRange.toEpochDays()..today.toEpochDays())
                        val randomDate = LocalDate.fromEpochDays(randomEpochDay.toInt())
                        randomDate
                    },
                    from = MessageIdentity(
                        email = faker.internet.email(),
                        color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                        avatarUrl = null,
                    ),
                    recipients = listOf(
                        MessageIdentity(
                            email = faker.internet.email(),
                            color = Color(Random.nextLong(0xFF000000, 0xFFFFFFFF)),
                            avatarUrl = null,
                        ),
                    ),
                )
            }
        }
        val groups = remember {
            messages
                .sortedByDescending { it.date }
                .groupBy { it.date }
                .map { (key, value) -> MessageGroup(date = key, messages = value) }
        }
        MessageList(
            groups = groups,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
