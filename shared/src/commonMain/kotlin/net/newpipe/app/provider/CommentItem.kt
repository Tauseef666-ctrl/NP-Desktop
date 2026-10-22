/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * A top-level comment on a stream page, as exposed by the provider layer.
 */
data class CommentItem(
    val text: String,
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val likeCount: Long? = null,
    val replyCount: Long? = null,
    val textualDate: String? = null
)
