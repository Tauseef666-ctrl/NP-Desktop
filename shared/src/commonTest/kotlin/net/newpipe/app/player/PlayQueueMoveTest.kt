/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayQueueMoveTest {

    private fun item(id: String) = MediaItem(id = id, title = "Track $id")

    private fun sample() = listOf(item("a"), item("b"), item("c"), item("d"))

    @Test
    fun moveRejectsOutOfBoundsWithoutMutating() {
        val queue = PlayQueue(sample())

        assertFalse(queue.move(-1, 0))
        assertFalse(queue.move(0, queue.size))
        assertFalse(queue.move(queue.size, 0))

        assertEquals(listOf("a", "b", "c", "d"), queue.items.map { it.id })
        assertEquals("a", queue.current?.id)
    }

    @Test
    fun moveRejectsEqualIndexesAsNoOp() {
        val queue = PlayQueue(sample())

        assertFalse(queue.move(2, 2))

        assertEquals(listOf("a", "b", "c", "d"), queue.items.map { it.id })
    }

    @Test
    fun moveReordersAndKeepsCurrentSelected() {
        val queue = PlayQueue(sample())
        queue.jumpTo(2)

        assertTrue(queue.move(0, 3))

        assertEquals(listOf("b", "c", "d", "a"), queue.items.map { it.id })
        assertEquals("c", queue.current?.id)
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun movingCurrentItemKeepsItSelected() {
        val queue = PlayQueue(sample())
        queue.jumpTo(1)

        assertTrue(queue.move(1, 3))

        assertEquals(listOf("a", "c", "d", "b"), queue.items.map { it.id })
        assertEquals("b", queue.current?.id)
        assertEquals(3, queue.currentIndex)
    }

    @Test
    fun movingItemAcrossCurrentAdjustsIndexWithoutChangingItem() {
        val queue = PlayQueue(sample())
        queue.jumpTo(2)

        assertTrue(queue.move(3, 0))

        assertEquals(listOf("d", "a", "b", "c"), queue.items.map { it.id })
        assertEquals("c", queue.current?.id)
        assertEquals(3, queue.currentIndex)
    }
}
