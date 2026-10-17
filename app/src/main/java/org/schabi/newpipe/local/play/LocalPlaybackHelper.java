/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.play;

import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.Collections;

/**
 * Plays a locally downloaded media file using the exact same player pipeline as an online stream.
 *
 * <p>A synthetic {@link StreamInfo} pointing at the local file URI is created and primed into the
 * {@link InfoCache}. The standard NewPipe main player is then opened for it, which gives the user
 * the full set of online player controls (seek bar, playback speed/quality/audio settings sheet,
 * gestures and auto-hiding controls), instead of a bare ExoPlayer {@code PlayerView}.</p>
 */
public final class LocalPlaybackHelper {
    private LocalPlaybackHelper() {
        // no instance
    }

    /**
     * Opens the standard NewPipe main player for a local media file.
     *
     * @param context  a context which can reach the hosting {@link AppCompatActivity}
     * @param uri      a readable {@code content://} (or {@code file://}) URI of the local file
     * @param title    the title to display in the player
     * @param mimeType the MIME type of the file, or {@code null} if unknown
     * @return {@code true} if the main player was opened, {@code false} if it could not be used
     *         and the caller should fall back to another player
     */
    public static boolean playLocalFile(@NonNull final Context context,
                                        @NonNull final Uri uri,
                                        @NonNull final String title,
                                        @Nullable final String mimeType) {
        final AppCompatActivity activity = findActivity(context);
        if (activity == null) {
            return false;
        }

        final String url = uri.toString();
        // Use a service which does not support comments, so that no failing comments tab is shown.
        final int serviceId = ServiceList.MediaCCC.getServiceId();
        final String id = "local:" + url;

        final boolean isAudio = mimeType != null && mimeType.startsWith("audio/");
        final StreamType streamType =
                isAudio ? StreamType.AUDIO_STREAM : StreamType.VIDEO_STREAM;

        MediaFormat mediaFormat = mimeType == null ? null : MediaFormat.getFromMimeType(mimeType);
        if (mediaFormat == null) {
            mediaFormat = MediaFormat.MPEG_4;
        }

        // Age limit 0 == NO_AGE_LIMIT, avoids the age-restricted path.
        final StreamInfo info = new StreamInfo(serviceId, url, url, streamType, id, title, 0);

        if (isAudio) {
            final AudioStream audioStream = new AudioStream.Builder()
                    .setId(id)
                    .setContent(url, true)
                    .setMediaFormat(mediaFormat)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .build();
            info.setAudioStreams(Collections.singletonList(audioStream));
        } else {
            final VideoStream videoStream = new VideoStream.Builder()
                    .setId(id)
                    .setContent(url, true)
                    .setMediaFormat(mediaFormat)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .setIsVideoOnly(false)
                    .setResolution(VideoStream.RESOLUTION_UNKNOWN)
                    .build();
            info.setVideoStreams(Collections.singletonList(videoStream));
        }

        // Prime the cache so both loading paths of the standard player pipeline, i.e. the
        // VideoDetailFragment (forceLoad=false) and PlayQueueItem.getStream(), reuse this
        // StreamInfo instead of trying to extract it from the network.
        InfoCache.getInstance().putInfo(serviceId, url, info, InfoCache.Type.STREAM);

        final PlayQueue playQueue = new SinglePlayQueue(info);
        NavigationHelper.playOnMainPlayer(activity, playQueue);
        return true;
    }

    @Nullable
    private static AppCompatActivity findActivity(@NonNull final Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof AppCompatActivity) {
                return (AppCompatActivity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof AppCompatActivity ? (AppCompatActivity) current : null;
    }
}
