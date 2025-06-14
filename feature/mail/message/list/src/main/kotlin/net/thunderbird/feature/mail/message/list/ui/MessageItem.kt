package net.thunderbird.feature.mail.message.list.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.icon.Icons
import app.k9mail.core.ui.compose.designsystem.atom.image.RemoteImage
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleSmall
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.core.ui.compose.theme2.thunderbird.ThunderbirdTheme2
import kotlin.time.ExperimentalTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

private const val MAX_DISPLAYABLE_ATTACHMENTS = 2

@Composable
fun MessageItem(
    subject: String,
    contentPreview: String,
    date: LocalDate,
    account: MessageAccount,
    modifier: Modifier = Modifier,
    attachments: ImmutableList<MessageAttachment> = persistentListOf(),
    read: Boolean = false,
    favourite: Boolean = false,
    important: Boolean = false,
    threadCount: Int = 0,
    onClick: () -> Unit = {},
    onFavouriteClick: () -> Unit = {},
) {
    val containerColor = if (read) MainTheme.colors.surfaceContainerHighest else MainTheme.colors.surfaceContainerLowest
    val readAlfa = if (read) 0.5f else 1f
    val textColor = MainTheme.colors.onSurface.copy(alpha = readAlfa)
    OutlinedCard(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
        ),
        shape = RoundedCornerShape(size = MainTheme.sizes.smaller),
    ) {
        Row(
            modifier = Modifier.padding(top = MainTheme.spacings.double),
            horizontalArrangement = Arrangement.spacedBy(MainTheme.spacings.default),
        ) {
            Avatar(
                account = account,
                selected = false,
                onClick = {},
                modifier = Modifier
                    .padding(start = MainTheme.spacings.oneHalf)
                    .size(MainTheme.sizes.iconLarge),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.half),
            ) {
                Row(
                    modifier = Modifier.padding(end = MainTheme.spacings.oneHalf),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MainTheme.spacings.half),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (important) {
                            Icon(
                                imageVector = Icons.Filled.Label,
                                contentDescription = null,
                                tint = MainTheme.colors.warning,
                                modifier = Modifier.size(MainTheme.sizes.iconSmall),
                            )
                        }
                        TextTitleSmall(text = account.email, color = textColor)
                        TextLabelSmall(text = "$threadCount", color = textColor)
                    }
                    val formatter = remember {
                        LocalDate.Format {
                            monthName(MonthNames.ENGLISH_ABBREVIATED)
                            char(' ')
                            dayOfMonth()
                        }
                    }
                    TextTitleSmall(text = date.format(formatter), color = textColor)
                }
                Row(
                    modifier = Modifier
                        .height(intrinsicSize = IntrinsicSize.Min)
                        .padding(end = MainTheme.spacings.default),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    ) {
                        TextTitleSmall(
                            text = subject,
                            color = textColor,
                        )
                        TextBodyMedium(
                            text = contentPreview,
                            color = textColor,
                            modifier = Modifier.fillMaxSize(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onFavouriteClick,
                        modifier = Modifier.size(MainTheme.sizes.iconLarge),
                    ) {
                        Icon(
                            imageVector = if (favourite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(MainTheme.spacings.default))

        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 52.dp),
                horizontalArrangement = Arrangement.spacedBy(MainTheme.spacings.half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val displayableAttachments = remember(attachments) {
                    attachments.take(MAX_DISPLAYABLE_ATTACHMENTS)
                }
                displayableAttachments.forEach { attachment ->
                    Surface(
                        shape = RoundedCornerShape(percent = 100),
                        border = BorderStroke(width = 1.dp, color = MainTheme.colors.outline),
                        color = containerColor,
                        contentColor = contentColorFor(containerColor),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MainTheme.spacings.half),
                            modifier = Modifier
                                .widthIn(max = 120.dp)
                                .padding(
                                    horizontal = MainTheme.spacings.default,
                                    vertical = MainTheme.spacings.half,
                                ),
                        ) {
                            Icon(
                                imageVector = when (attachment.type) {
                                    MessageAttachment.Type.Document -> Icons.Filled.Document
                                    MessageAttachment.Type.Image -> Icons.Filled.Image
                                    MessageAttachment.Type.Pdf -> Icons.Filled.Pdf
                                    MessageAttachment.Type.Other -> Icons.Filled.Attachment
                                },
                                contentDescription = null,
                                modifier = Modifier.size(MainTheme.sizes.iconSmall),
                            )
                            TextLabelMedium(
                                text = attachment.name,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (attachments.size > MAX_DISPLAYABLE_ATTACHMENTS) {
                    TextLabelSmall(
                        text = when (val extraAttachments = attachments.size - MAX_DISPLAYABLE_ATTACHMENTS) {
                            in 1000..Int.MAX_VALUE -> "+ >1K"
                            else -> "+$extraAttachments"
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(MainTheme.spacings.double))
    }
}

@Composable
fun Avatar(
    account: MessageAccount,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = account.color,
        ),
    ) {
        when {
            selected ->
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MainTheme.colors.surface,
                )

            account.avatarUrl.isNullOrBlank() ->
                TextTitleMedium(
                    text = account.email.first().uppercase(),
                )

            else -> RemoteImage(
                url = account.avatarUrl,
            )
        }
    }
}

data class MessageAccount(
    val email: String,
    val color: Color,
    val avatarUrl: String?,
)

data class MessageAttachment(
    val name: String,
    val type: Type,
) {
    enum class Type { Document, Image, Pdf, Other }
}

private data class PreviewParam(
    val subject: String,
    val contentPreview: String,
    val date: LocalDate,
    val account: MessageAccount,
    val attachments: ImmutableList<MessageAttachment> = persistentListOf(),
    val read: Boolean = false,
    val favourite: Boolean = false,
    val important: Boolean = false,
    val threadCount: Int = 0,
)

private class PreviewParamsCollection : CollectionPreviewParameterProvider<PreviewParam>(
    buildList {
        fun addReadNotRead(
            favourite: Boolean,
            important: Boolean,
            threadCount: Int,
            attachments: ImmutableList<MessageAttachment> = persistentListOf(),
        ) {
            add(
                PreviewParam(
                    subject = "That is a nice email",
                    contentPreview = "The message's content preview",
                    date = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
                    account = MessageAccount(
                        email = "rafael@tonholo.com",
                        color = Color.Red,
                        avatarUrl = null,
                    ),
                    attachments = attachments,
                    read = true,
                    favourite = favourite,
                    important = important,
                    threadCount = threadCount,
                ),
            )
            add(
                PreviewParam(
                    subject = "That is a nice email",
                    contentPreview = "The message's content preview",
                    date = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
                    account = MessageAccount(
                        email = "rafael@tonholo.com",
                        color = Color.Red,
                        avatarUrl = null,
                    ),
                    attachments = attachments,
                    read = false,
                    favourite = favourite,
                    important = important,
                    threadCount = threadCount,
                ),
            )
        }

        addReadNotRead(favourite = false, important = false, threadCount = 0)
        addReadNotRead(favourite = true, important = false, threadCount = 0)
        addReadNotRead(favourite = true, important = true, threadCount = 0)
        addReadNotRead(favourite = false, important = true, threadCount = 0)

        addReadNotRead(favourite = false, important = false, threadCount = 10)
        addReadNotRead(favourite = true, important = false, threadCount = 10)
        addReadNotRead(favourite = true, important = true, threadCount = 10)
        addReadNotRead(favourite = false, important = true, threadCount = 10)

        addReadNotRead(favourite = false, important = false, threadCount = 100)
        addReadNotRead(favourite = true, important = false, threadCount = 100)
        addReadNotRead(favourite = true, important = true, threadCount = 100)
        addReadNotRead(favourite = false, important = true, threadCount = 100)

        addReadNotRead(favourite = false, important = false, threadCount = 1000)
        addReadNotRead(favourite = true, important = false, threadCount = 1000)
        addReadNotRead(favourite = true, important = true, threadCount = 1000)
        addReadNotRead(favourite = false, important = true, threadCount = 1000)

        addReadNotRead(favourite = false, important = false, threadCount = 10000)
        addReadNotRead(favourite = true, important = false, threadCount = 10000)
        addReadNotRead(favourite = true, important = true, threadCount = 10000)
        addReadNotRead(favourite = false, important = true, threadCount = 10000)

        addReadNotRead(
            favourite = false,
            important = false,
            threadCount = 0,
            attachments = persistentListOf(
                MessageAttachment(
                    name = "Small name",
                    type = MessageAttachment.Type.entries.random(),
                ),
                MessageAttachment(
                    name = "Long ".repeat(10),
                    type = MessageAttachment.Type.entries.random(),
                ),
            ),
        )
        addReadNotRead(
            favourite = false,
            important = false,
            threadCount = 0,
            attachments = List(size = 10) {
                MessageAttachment(
                    name = "Attachment $it",
                    type = MessageAttachment.Type.entries.random(),
                )
            }.toPersistentList(),
        )

        addReadNotRead(
            favourite = false,
            important = false,
            threadCount = 0,
            attachments = List(size = 10) {
                MessageAttachment(
                    name = "Long ".repeat(10 + it) + "Attachment",
                    type = MessageAttachment.Type.entries.random(),
                )
            }.toPersistentList(),
        )

        addReadNotRead(
            favourite = false,
            important = false,
            threadCount = 0,
            attachments = List(size = 100) {
                MessageAttachment(
                    name = "Long ".repeat(10 + it) + "Attachment",
                    type = MessageAttachment.Type.entries.random(),
                )
            }.toPersistentList(),
        )

        addReadNotRead(
            favourite = false,
            important = false,
            threadCount = 0,
            attachments = List(size = 1000) {
                MessageAttachment(
                    name = "Long ".repeat(10 + it) + "Attachment",
                    type = MessageAttachment.Type.entries.random(),
                )
            }.toPersistentList(),
        )

        addReadNotRead(
            favourite = false,
            important = false,
            threadCount = 0,
            attachments = List(size = 10000) {
                MessageAttachment(
                    name = "Long ".repeat(10 + it) + "Attachment",
                    type = MessageAttachment.Type.entries.random(),
                )
            }.toPersistentList(),
        )
    },
)

@OptIn(ExperimentalTime::class)
@PreviewLightDark
@Composable
private fun Preview(
    @PreviewParameter(PreviewParamsCollection::class) param: PreviewParam,
) {
    ThunderbirdTheme2 {
        MessageItem(
            subject = param.subject,
            contentPreview = param.contentPreview,
            date = param.date,
            account = param.account,
            attachments = param.attachments,
            read = param.read,
            favourite = param.favourite,
            important = param.important,
            threadCount = param.threadCount,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        )
    }
}
