/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.play;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.ActivityDownloadPlayerBinding;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.player.helper.PlayerHelper;

import java.util.Collections;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * In-app playback screen for files downloaded with NewPipe.
 *
 * <p>Plays a local media file (a {@code file://} or {@code content://} URI) with the same ExoPlayer
 * engine that is used for online videos. Next to the standard ExoPlayer controls it offers the
 * options a video player is expected to have: playback speed, screen-rotation lock, fullscreen,
 * adding the original stream to a playlist and a real error state with a retry action. Audio keeps
 * playing when the screen is left, so a download can be listened to in the background while the
 * process is alive.</p>
 */
public class DownloadPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URI = "org.schabi.newpipe.local.play.EXTRA_URI";
    public static final String EXTRA_TITLE = "org.schabi.newpipe.local.play.EXTRA_TITLE";
    public static final String EXTRA_SOURCE = "org.schabi.newpipe.local.play.EXTRA_SOURCE";

    /** Same speed steps as the online player, so both screens behave identically. */
    private static final float[] PLAYBACK_SPEEDS =
            {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private static final int SPEED_MENU_GROUP = 1;
    private static final int MENU_FULLSCREEN = 1001;
    private static final int MENU_ROTATE = 1002;
    private static final int MENU_ADD_TO_PLAYLIST = 1003;

    private static final String STATE_RESUME_POSITION = "resume_position";
    private static final String STATE_PLAY_WHEN_READY = "play_when_ready";

    private ActivityDownloadPlayerBinding binding;

    @Nullable
    private ExoPlayer player;

    @Nullable
    private Disposable playlistDisposable;

    private String playbackUri;

    private float playbackSpeed = 1.0f;

    private boolean fullscreen;

    private long resumePosition;

    private boolean resumePlayWhenReady = true;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDownloadPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final String uriString = getIntent().getStringExtra(EXTRA_URI);
        if (TextUtils.isEmpty(uriString)) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        playbackUri = uriString;

        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (!TextUtils.isEmpty(title)) {
            binding.title.setText(title);
            binding.title.setVisibility(View.VISIBLE);
        }

        if (savedInstanceState != null) {
            resumePosition = savedInstanceState.getLong(STATE_RESUME_POSITION, 0L);
            resumePlayWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY, true);
        }

        binding.options.setOnClickListener(v -> showOptionsMenu(v));
        binding.errorRetry.setOnClickListener(v -> retryPlayback());
        binding.playerView.setKeepScreenOn(true);

        createPlayer(uriString);
    }

    private void createPlayer(@NonNull final String uriString) {
        player = new ExoPlayer.Builder(this).build();
        // Enable ExoPlayer automatic audio focus management (same as the online player)
        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                /* handleAudioFocus= */ true);
        player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull final PlaybackException error) {
                showError(error);
            }
        });

        binding.playerView.setPlayer(player);
        binding.playerView.setKeepScreenOn(true);

        player.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)));
        player.prepare();
        if (resumePosition > 0L) {
            player.seekTo(resumePosition);
        }
        player.setPlayWhenReady(resumePlayWhenReady);
    }

    /**
     * Builds the options popup: speed, fullscreen, rotation lock and playlist. Every entry is
     * reachable with a D-pad, so the screen works on phones, tablets and Android TV alike.
     *
     * @param anchor view the popup opens against (the options button)
     */
    private void showOptionsMenu(@NonNull final View anchor) {
        final PopupMenu popup = new PopupMenu(this, anchor);
        final Menu menu = popup.getMenu();

        final SubMenu speedItems = menu.addSubMenu(R.string.playback_speed_control);
        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            final float speed = PLAYBACK_SPEEDS[i];
            final MenuItem speedItem = speedItems.add(SPEED_MENU_GROUP, i + 1, Menu.NONE,
                    PlayerHelper.formatSpeed(speed));
            speedItem.setCheckable(true);
            speedItem.setChecked(Math.abs(speed - playbackSpeed) < 0.01f);
        }

        menu.add(Menu.NONE, MENU_FULLSCREEN, Menu.NONE, R.string.toggle_fullscreen);
        menu.add(Menu.NONE, MENU_ROTATE, Menu.NONE, R.string.toggle_screen_orientation);

        final String source = getIntent().getStringExtra(EXTRA_SOURCE);
        menu.add(Menu.NONE, MENU_ADD_TO_PLAYLIST, Menu.NONE, R.string.add_to_playlist)
                .setVisible(!TextUtils.isEmpty(source));

        popup.setOnMenuItemClickListener(item -> {
            if (item.getGroupId() == SPEED_MENU_GROUP) {
                applySpeed(PLAYBACK_SPEEDS[item.getItemId() - 1]);
                return true;
            }
            final int id = item.getItemId();
            if (id == MENU_FULLSCREEN) {
                toggleFullscreen();
                return true;
            } else if (id == MENU_ROTATE) {
                toggleOrientation();
                return true;
            } else if (id == MENU_ADD_TO_PLAYLIST) {
                addToPlaylist();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void applySpeed(final float speed) {
        playbackSpeed = speed;
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(speed));
        }
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        final WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void toggleOrientation() {
        final boolean landscape =
                getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
        setRequestedOrientation(landscape
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    /**
     * Adds the original page of the download to a playlist. The local file itself is deliberately
     * not stored: a playlist entry has to resolve again later, which only works for the source URL.
     */
    private void addToPlaylist() {
        final String source = getIntent().getStringExtra(EXTRA_SOURCE);
        if (TextUtils.isEmpty(source)) {
            return;
        }
        final String fileTitle = getIntent().getStringExtra(EXTRA_TITLE);
        final String entryTitle = TextUtils.isEmpty(fileTitle) ? source : fileTitle;

        try {
            final int serviceId = NewPipe.getServiceByUrl(source).getServiceId();
            final StreamInfoItem info = new StreamInfoItem(serviceId, source, entryTitle,
                    StreamType.VIDEO_STREAM);
            final StreamEntity entity = new StreamEntity(info);
            playlistDisposable = PlaylistDialog.createCorrespondingDialog(this,
                    Collections.singletonList(entity),
                    dialog -> dialog.show(getSupportFragmentManager(), "PlaylistDialog"));
        } catch (final Exception e) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(@NonNull final PlaybackException error) {
        final String detail = error.getMessage();
        binding.errorMessage.setText(getString(R.string.error_occurred_detail,
                TextUtils.isEmpty(detail) ? getString(R.string.player_stream_failure) : detail));
        binding.errorContainer.setVisibility(View.VISIBLE);
    }

    private void retryPlayback() {
        binding.errorContainer.setVisibility(View.GONE);
        final ExoPlayer current = player;
        if (current == null || playbackUri == null) {
            return;
        }
        current.clearMediaItems();
        current.setMediaItem(MediaItem.fromUri(Uri.parse(playbackUri)));
        current.prepare();
        current.setPlayWhenReady(true);
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        final ExoPlayer current = player;
        if (current != null) {
            outState.putLong(STATE_RESUME_POSITION, current.getCurrentPosition());
            outState.putBoolean(STATE_PLAY_WHEN_READY, current.getPlayWhenReady());
        }
    }

    // No onStop() pause on purpose: audio is meant to keep playing when the screen is left.
    @Override
    protected void onDestroy() {
        if (playlistDisposable != null) {
            playlistDisposable.dispose();
            playlistDisposable = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
