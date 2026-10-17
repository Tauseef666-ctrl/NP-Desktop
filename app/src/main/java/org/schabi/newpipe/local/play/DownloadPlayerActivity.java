/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.play;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioAttributes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityDownloadPlayerBinding;

/**
 * Minimal in-app playback screen for files downloaded with NewPipe.
 *
 * <p>It plays a local media file (a {@code file://} or {@code content://} URI) using the same
 * ExoPlayer engine that is used for online videos, with the standard ExoPlayer controls.</p>
 */
public class DownloadPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URI = "org.schabi.newpipe.local.play.EXTRA_URI";
    public static final String EXTRA_TITLE = "org.schabi.newpipe.local.play.EXTRA_TITLE";

    @Nullable
    private ExoPlayer player;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityDownloadPlayerBinding binding =
                ActivityDownloadPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final String uriString = getIntent().getStringExtra(EXTRA_URI);
        if (TextUtils.isEmpty(uriString)) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (!TextUtils.isEmpty(title)) {
            binding.title.setText(title);
            binding.title.setVisibility(View.VISIBLE);
        }

        player = new ExoPlayer.Builder(this).build();
        // Enable ExoPlayer automatic audio focus management (same as the online player)
        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                /* handleAudioFocus= */ true);
        binding.playerView.setPlayer(player);
        binding.playerView.setKeepScreenOn(true);

        player.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
