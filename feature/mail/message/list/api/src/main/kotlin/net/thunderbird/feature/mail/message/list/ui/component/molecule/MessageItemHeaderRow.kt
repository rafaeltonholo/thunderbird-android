package net.thunderbird.feature.mail.message.list.ui.component.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun HeaderRow(
    modifier: Modifier = Modifier,
    headerRowContent: @Composable ((RowScope) -> Unit),
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MainTheme.spacings.default),
        modifier = modifier
            .defaultMinSize(minHeight = AccountIndicatorIcon.ACCOUNT_INDICATOR_DEFAULT_HEIGHT)
            .fillMaxWidth()
            .width(intrinsicSize = IntrinsicSize.Max),
    ) {
        headerRowContent(this)
    }
}

@Composable
internal fun HeaderRowSmall(
    modifier: Modifier = Modifier,
    headerRowContent: @Composable ((RowScope) -> Unit),
) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.Start,
        maxLines = 2,
        modifier = modifier.defaultMinSize(minHeight = AccountIndicatorIcon.ACCOUNT_INDICATOR_DEFAULT_HEIGHT),
    ) {
        headerRowContent(this)
    }
}
