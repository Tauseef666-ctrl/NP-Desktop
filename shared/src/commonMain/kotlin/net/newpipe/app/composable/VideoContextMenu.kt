/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The outcome of waiting for a primary press to end.
 */
private enum class PrimaryPressEnd {

    /** The user released the button before the long press timeout; an ordinary click or drag. */
    Done,

    /** Another gesture consumed the events; the press belongs to them. */
    Taken,

    /** The press lasted until the timeout; a long press requesting the context menu. */
    LongPress
}

/**
 * Runs [onContextMenu] when the user performs a secondary click (right-click) or presses and
 * holds the primary button, which is how a context menu is requested on the platforms the app
 * runs on.
 *
 * Ordinary taps and scrolls are passed through, so the click action of the surrounding element
 * still works: the menu only opens when the press was secondary or outlived the long press
 * timeout. Unlike `waitForUpOrCancellation`, a click whose up event is consumed by the
 * surrounding click handling is treated as an ordinary press and not a long press.
 */
fun Modifier.videoContextMenu(onContextMenu: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        if (currentEvent.buttons.isSecondaryPressed) {
            onContextMenu()
            waitForAllButtonsUp()
            return@awaitEachGesture
        }
        val end = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            classifyPrimaryPress()
        } ?: PrimaryPressEnd.LongPress
        if (end == PrimaryPressEnd.LongPress) {
            onContextMenu()
            waitForAllButtonsUp()
        }
    }
}

/**
 * Classifies how the primary press that is currently active ends, without treating an up event
 * consumed by the surrounding click handling as a cancellation.
 */
private suspend fun AwaitPointerEventScope.classifyPrimaryPress(): PrimaryPressEnd {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) return PrimaryPressEnd.Done
        if (event.changes.any { it.isConsumed }) return PrimaryPressEnd.Taken
    }
}

/**
 * Waits until every button of the gesture is released again.
 */
private suspend fun AwaitPointerEventScope.waitForAllButtonsUp() {
    while (currentEvent.changes.any { it.pressed }) {
        awaitPointerEvent()
    }
}
