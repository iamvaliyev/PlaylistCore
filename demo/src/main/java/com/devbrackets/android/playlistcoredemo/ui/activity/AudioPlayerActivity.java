package com.devbrackets.android.playlistcoredemo.ui.activity;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteButton;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.devbrackets.android.playlistcore.data.MediaProgress;
import com.devbrackets.android.playlistcore.data.PlaybackState;
import com.devbrackets.android.playlistcore.data.PlaylistItemChange;
import com.devbrackets.android.playlistcore.listener.PlaylistListener;
import com.devbrackets.android.playlistcore.listener.ProgressListener;
import com.devbrackets.android.playlistcoredemo.App;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.devbrackets.android.playlistcoredemo.data.Samples;
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;
import com.google.android.gms.cast.framework.CastButtonFactory;

import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * An example activity to show how to implement and audio UI
 * that interacts with the {@link com.devbrackets.android.playlistcore.service.BasePlaylistService}
 * and {@link com.devbrackets.android.playlistcore.manager.ListPlaylistManager}
 * classes.
 */
public class AudioPlayerActivity extends AppCompatActivity implements PlaylistListener<MediaItem>, ProgressListener {
    public static final String EXTRA_INDEX = "EXTRA_INDEX";
    public static final int PLAYLIST_ID = 4; //Arbitrary, for the example

    private static StringBuilder formatBuilder = new StringBuilder();
    private static Formatter formatter = new Formatter(formatBuilder, Locale.getDefault());

    private ProgressBar loadingBar;
    private ImageView artworkView;

    private TextView currentPositionView;
    private TextView durationView;

    private SeekBar seekBar;
    private boolean shouldSetDuration;
    private boolean userInteracting;

    private TextView titleTextView;
    private TextView subtitleTextView;
    private TextView descriptionTextView;

    private ImageButton previousButton;
    private ImageButton playPauseButton;
    private ImageButton nextButton;

    private MediaRouteButton castButton;

    private PlaylistManager playlistManager;
    private int selectedPosition = 0;

    private RequestManager glide;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_player_activity);

        retrieveExtras();
        init();
    }

    @Override
    protected void onPause() {
        super.onPause();
        playlistManager.unRegisterPlaylistListener(this);
        playlistManager.unRegisterProgressListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        playlistManager = App.getPlaylistManager();
        playlistManager.registerPlaylistListener(this);
        playlistManager.registerProgressListener(this);

        //Makes sure to retrieve the current playback information
        updateCurrentPlaybackInformation();
    }

    @Override
    public boolean onPlaylistItemChanged(@Nullable MediaItem currentItem, boolean hasNext, boolean hasPrevious) {
        shouldSetDuration = true;

        //Updates the button states
        nextButton.setEnabled(hasNext);
        previousButton.setEnabled(hasPrevious);

        //Loads the new image
        if (currentItem != null) {
            glide.load(currentItem.getArtworkUrl()).into(artworkView);
        }

        // Updates the title, subtitle, and description
        titleTextView.setText(currentItem != null ? currentItem.getTitle() : "");
        subtitleTextView.setText(currentItem != null ? currentItem.getAlbum() : "");
        descriptionTextView.setText(currentItem != null ? currentItem.getArtist() : "");

        return true;
    }

    @Override
    public boolean onPlaybackStateChanged(@NonNull PlaybackState playbackState) {
        switch (playbackState) {
            case STOPPED:
                finish();
                break;

            case RETRIEVING:
            case PREPARING:
            case SEEKING:
                restartLoading();
                break;

            case PLAYING:
                doneLoading(true);
                break;

            case PAUSED:
                doneLoading(false);
                break;

            default:
                break;
        }

        return true;
    }

    @Override
    public boolean onProgressUpdated(@NonNull MediaProgress progress) {
        if (shouldSetDuration && progress.getDuration() > 0) {
            shouldSetDuration = false;
            setDuration(progress.getDuration());
        }

        if (!userInteracting) {
            seekBar.setSecondaryProgress((int) (progress.getDuration() * progress.getBufferPercentFloat()));
            seekBar.setProgress((int)progress.getPosition());
            currentPositionView.setText(formatMs(progress.getPosition()));
        }

        return true;
    }

    /**
     * Makes sure to update the UI to the current playback item.
     */
    private void updateCurrentPlaybackInformation() {
        PlaylistItemChange<MediaItem> itemChange = playlistManager.getCurrentItemChange();
        if (itemChange != null) {
            onPlaylistItemChanged(itemChange.getCurrentItem(), itemChange.getHasNext(), itemChange.getHasPrevious());
        }

        PlaybackState currentPlaybackState = playlistManager.getCurrentPlaybackState();
        if (currentPlaybackState != PlaybackState.STOPPED) {
            onPlaybackStateChanged(currentPlaybackState);
        }

        MediaProgress mediaProgress = playlistManager.getCurrentProgress();
        if (mediaProgress != null) {
            onProgressUpdated(mediaProgress);
        }
    }

    /**
     * Retrieves the extra associated with the selected playlist index
     * so that we can start playing the correct item.
     */
    private void retrieveExtras() {
        Bundle extras = getIntent().getExtras();
        selectedPosition = extras.getInt(EXTRA_INDEX, 0);
    }

    /**
     * Performs the initialization of the views and any other
     * general setup
     */
    private void init() {
        retrieveViews();
        setupListeners();

        glide = Glide.with(this);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), castButton);

        boolean generatedPlaylist = setupPlaylistManager();
        startPlayback(generatedPlaylist);
    }


    /**
     * Called when we receive a notification that the current item is
     * done loading.  This will then update the view visibilities and
     * states accordingly.
     *
     * @param isPlaying True if the audio item is currently playing
     */
    private void doneLoading(boolean isPlaying) {
        loadCompleted();
        updatePlayPauseImage(isPlaying);
    }

    /**
     * Updates the Play/Pause image to represent the correct playback state
     *
     * @param isPlaying True if the audio item is currently playing
     */
    private void updatePlayPauseImage(boolean isPlaying) {
        int resId = isPlaying ? R.drawable.ic_pause_black_24dp : R.drawable.ic_play_black_24dp;
        playPauseButton.setImageResource(resId);
    }

    /**
     * Used to inform the controls to finalize their setup.  This
     * means replacing the loading animation with the PlayPause button
     */
    public void loadCompleted() {
        playPauseButton.setVisibility(View.VISIBLE);
        previousButton.setVisibility(View.VISIBLE);
        nextButton.setVisibility(View.VISIBLE );

        loadingBar.setVisibility(View.INVISIBLE);
    }

    /**
     * Used to inform the controls to return to the loading stage.
     * This is the opposite of {@link #loadCompleted()}
     */
    public void restartLoading() {
        playPauseButton.setVisibility(View.INVISIBLE);
        previousButton.setVisibility(View.INVISIBLE);
        nextButton.setVisibility(View.INVISIBLE );

        loadingBar.setVisibility(View.VISIBLE);
    }

    /**
     * Sets the {@link #seekBar}s max and updates the duration text
     *
     * @param duration The duration of the media item in milliseconds
     */
    private void setDuration(long duration) {
        seekBar.setMax((int)duration);
        durationView.setText(formatMs(duration));
    }

    /**
     * Retrieves the playlist instance and performs any generation
     * of content if it hasn't already been performed.
     *
     * @return True if the content was generated
     */
    private boolean setupPlaylistManager() {
        playlistManager = App.getPlaylistManager();

        //There is nothing to do if the currently playing values are the same
        if (playlistManager.getId() == PLAYLIST_ID) {
            return false;
        }

        List<MediaItem> mediaItems = new LinkedList<>();
        for (Samples.Sample sample : Samples.getAudioSamples()) {
            MediaItem mediaItem = new MediaItem(sample, true);
            mediaItems.add(mediaItem);
        }

        playlistManager.setParameters(mediaItems, selectedPosition);
        playlistManager.setId(PLAYLIST_ID);

        return true;
    }

    /**
     * Populates the class variables with the views created from the
     * xml layout file.
     */
    private void retrieveViews() {
        loadingBar = findViewById(R.id.audio_player_loading);
        artworkView = findViewById(R.id.audio_player_image);

        currentPositionView = findViewById(R.id.audio_player_position);
        durationView = findViewById(R.id.audio_player_duration);

        seekBar = findViewById(R.id.audio_player_seek);

        titleTextView = findViewById(R.id.title_text_view);
        subtitleTextView = findViewById(R.id.subtitle_text_view);
        descriptionTextView = findViewById(R.id.description_text_view);

        previousButton = findViewById(R.id.audio_player_previous);
        playPauseButton = findViewById(R.id.audio_player_play_pause);
        nextButton = findViewById(R.id.audio_player_next);

        castButton = findViewById(R.id.media_route_button);
    }

    /**
     * Links the SeekBarChanged to the {@link #seekBar} and
     * onClickListeners to the media buttons that call the appropriate
     * invoke methods in the {@link #playlistManager}
     */
    private void setupListeners() {
        seekBar.setOnSeekBarChangeListener(new SeekBarChanged());

        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistManager.invokePrevious();
            }
        });

        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistManager.invokePausePlay();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistManager.invokeNext();
            }
        });
    }

    /**
     * Starts the audio playback if necessary.
     *
     * @param forceStart True if the audio should be started from the beginning even if it is currently playing
     */
    private void startPlayback(boolean forceStart) {
        //If we are changing audio files, or we haven't played before then start the playback
        if (forceStart || playlistManager.getCurrentPosition() != selectedPosition) {
            playlistManager.setCurrentPosition(selectedPosition);
            playlistManager.play(0, false);
        }
    }

    /**
     * Formats the specified milliseconds to a human readable format
     * in the form of (Hours : Minutes : Seconds).  If the specified
     * milliseconds is less than 0 the resulting format will be
     * "--:--" to represent an unknown time
     *
     * @param milliseconds The time in milliseconds to format
     * @return The human readable time
     */
    public static String formatMs(long milliseconds) {
        if (milliseconds < 0) {
            return "--:--";
        }

        long seconds = (milliseconds % DateUtils.MINUTE_IN_MILLIS) / DateUtils.SECOND_IN_MILLIS;
        long minutes = (milliseconds % DateUtils.HOUR_IN_MILLIS) / DateUtils.MINUTE_IN_MILLIS;
        long hours = (milliseconds % DateUtils.DAY_IN_MILLIS) / DateUtils.HOUR_IN_MILLIS;

        formatBuilder.setLength(0);
        if (hours > 0) {
            return formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        }

        return formatter.format("%02d:%02d", minutes, seconds).toString();
    }

    /**
     * Listens to the seek bar change events and correctly handles the changes
     */
    private class SeekBarChanged implements SeekBar.OnSeekBarChangeListener {
        private int seekPosition = -1;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                return;
            }

            seekPosition = progress;
            currentPositionView.setText(formatMs(progress));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            userInteracting = true;

            seekPosition = seekBar.getProgress();
            playlistManager.invokeSeekStarted();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            userInteracting = false;

            //noinspection Range - seekPosition won't be less than 0
            playlistManager.invokeSeekEnded(seekPosition);
            seekPosition = -1;
        }
    }
}
