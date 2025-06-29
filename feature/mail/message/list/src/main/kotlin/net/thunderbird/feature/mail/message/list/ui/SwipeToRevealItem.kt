package net.thunderbird.feature.mail.message.list.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.icon.Icon
import app.k9mail.core.ui.compose.designsystem.atom.icon.Icons
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.core.ui.compose.theme2.thunderbird.ThunderbirdTheme2
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SwipeToRevealItem(
    revealed: Boolean,
    content: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MainTheme.spacings.double),
    swipeDirection: SwipeDirection = SwipeDirection.Left,
    onRevealChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val layoutDirection = LocalLayoutDirection.current
    val swipeActionsAlignment = remember(layoutDirection, swipeDirection) {
        when (swipeDirection) {
            SwipeDirection.Left if layoutDirection == LayoutDirection.Ltr -> Alignment.CenterEnd
            SwipeDirection.Right if layoutDirection == LayoutDirection.Ltr -> Alignment.CenterStart
            SwipeDirection.Left -> Alignment.CenterStart
            SwipeDirection.Right -> Alignment.CenterEnd
        }
    }

    var actionsSize by remember { mutableFloatStateOf(0f) }
    val directionMultiplier = remember(layoutDirection, swipeDirection) {
        when (swipeDirection) {
            SwipeDirection.Left if layoutDirection == LayoutDirection.Ltr -> -1
            SwipeDirection.Right if layoutDirection == LayoutDirection.Ltr -> 1
            SwipeDirection.Left -> 1
            SwipeDirection.Right -> -1
        }
    }
    var dragStarted by remember { mutableStateOf(false) }

    LaunchedEffect(revealed, actionsSize) {
        if (revealed) {
            offset.animateTo(actionsSize * directionMultiplier)
        } else {
            offset.animateTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(intrinsicSize = IntrinsicSize.Min),
    ) {
        Surface(
            modifier = Modifier
                .align(swipeActionsAlignment),
            color = MainTheme.colors.surfaceContainerLowest,
        ) {
            Row(
                modifier = Modifier
                    .onSizeChanged {
                        actionsSize = it.width.toFloat()
                    }
                    .padding(contentPadding),
            ) {
                actions()
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
//                        x = when {
//                            revealed && !dragStarted -> {
//                                actionsSize.roundToInt() * directionMultiplier
//                            }
//
//                            else -> {
//                                offset.value.roundToInt()
//                            }
//                        },
                        x = offset.value.roundToInt(),
                        y = 0,
                    )
                }
                .pointerInput(layoutDirection) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            val dragAmountX = normalizeDragAmount(dragAmount, layoutDirection)
                            val newOffset = calculateNewOffset(
                                offset = offset.value,
                                dragAmountX = dragAmountX,
                                swipeDirection = swipeDirection,
                                layoutDirection = layoutDirection,
                                actionsSize = actionsSize,
                                directionMultiplier = directionMultiplier,
                            )

                            scope.launch {
                                offset.snapTo(newOffset)
                            }
                        },
                        onDragStart = { offset -> dragStarted = true },
                        onDragEnd = {
                            when {
                                abs(offset.value) >= (actionsSize / 2) -> scope.launch {
                                    offset.animateTo(actionsSize * directionMultiplier)
                                }

                                else -> scope.launch {
                                    offset.animateTo(0f)
                                }
                            }

                            onRevealChange(abs(offset.value) >= (actionsSize / 2))
                            dragStarted = false
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                content()
//                Text(
//                    "offset = ${offset.value}, actionsSize = $actionsSize",
//                    modifier = Modifier.align(Alignment.Center),
//                )
            }
        }
    }
}

private fun normalizeDragAmount(dragAmount: Float, layoutDirection: LayoutDirection): Float =
    dragAmount * when (layoutDirection) {
        LayoutDirection.Ltr -> 1
        LayoutDirection.Rtl -> -1
    }

private fun calculateNewOffset(
    offset: Float,
    dragAmountX: Float,
    swipeDirection: SwipeDirection,
    layoutDirection: LayoutDirection,
    actionsSize: Float,
    directionMultiplier: Int,
): Float = (offset + dragAmountX * 2).let {
    when (swipeDirection) {
        SwipeDirection.Left if layoutDirection == LayoutDirection.Ltr -> it.coerceIn(
            actionsSize * directionMultiplier,
            0f,
        )

        SwipeDirection.Right if layoutDirection == LayoutDirection.Ltr -> it.coerceIn(
            0f,
            actionsSize,
        )

        SwipeDirection.Left -> it.coerceIn(
            0f,
            actionsSize,
        )

        SwipeDirection.Right -> it.coerceIn(
            actionsSize * directionMultiplier,
            0f,
        )
    }
}

enum class SwipeDirection { Left, Right }

private data class SwipableItemPreviewParam(
    val revealed: Boolean,
    val layoutDirection: LayoutDirection,
    val swipeDirection: SwipeDirection,
)

private class SwipableItemPreviewParamCol : CollectionPreviewParameterProvider<SwipableItemPreviewParam>(
    listOf(
        SwipableItemPreviewParam(
            revealed = false,
            layoutDirection = LayoutDirection.Ltr,
            swipeDirection = SwipeDirection.Left,
        ),
        SwipableItemPreviewParam(
            revealed = true,
            layoutDirection = LayoutDirection.Ltr,
            swipeDirection = SwipeDirection.Left,
        ),
        SwipableItemPreviewParam(
            revealed = false,
            layoutDirection = LayoutDirection.Rtl,
            swipeDirection = SwipeDirection.Left,
        ),
        SwipableItemPreviewParam(
            revealed = true,
            layoutDirection = LayoutDirection.Rtl,
            swipeDirection = SwipeDirection.Left,
        ),
        SwipableItemPreviewParam(
            revealed = false,
            layoutDirection = LayoutDirection.Ltr,
            swipeDirection = SwipeDirection.Right,
        ),
        SwipableItemPreviewParam(
            revealed = true,
            layoutDirection = LayoutDirection.Ltr,
            swipeDirection = SwipeDirection.Right,
        ),
        SwipableItemPreviewParam(
            revealed = false,
            layoutDirection = LayoutDirection.Rtl,
            swipeDirection = SwipeDirection.Right,
        ),
        SwipableItemPreviewParam(
            revealed = true,
            layoutDirection = LayoutDirection.Rtl,
            swipeDirection = SwipeDirection.Right,
        ),
    ),
)

@PreviewLightDark
@Composable
private fun Preview(
    @PreviewParameter(SwipableItemPreviewParamCol::class) param: SwipableItemPreviewParam,
) {
    ThunderbirdTheme2 {
        CompositionLocalProvider(LocalLayoutDirection provides param.layoutDirection) {
            var revealed by remember { mutableStateOf(param.revealed) }
            var called by remember { mutableStateOf(false) }
            SwipeToRevealItem(
                revealed = revealed,
                content = {
                    TextBodyLarge("called = $called,  revealed = $revealed")
                },
                actions = {
                    Icon(imageVector = Icons.Outlined.Archive)
                    Icon(imageVector = Icons.Outlined.Delete)
                },
                modifier = Modifier.padding(16.dp),
                swipeDirection = param.swipeDirection,
                onRevealChange = {
                    called = true
                    revealed = it
                },
            )
        }
    }
}
