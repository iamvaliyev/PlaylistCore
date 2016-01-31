/*
 * Copyright (C) 2016 Brian Wernick
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devbrackets.android.playlistcore.helper;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.DrawableRes;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.devbrackets.android.playlistcore.service.RemoteActions;
import com.devbrackets.android.playlistcore.receiver.MediaControlsReceiver;

/**
 * A class to help simplify playback control and artwork display for
 * remote views such as Android Wear, Bluetooth devices, Lock Screens, etc.
 * similar to how the {@link NotificationHelper} simplifies notifications
 */
public class MediaControlsHelper {
    private static final String TAG = "MediaControlsHelper";
    public static final String SESSION_TAG = "MediaControlsHelper.Session";
    public static final String RECEIVER_EXTRA_CLASS = "com.devbrackets.android.playlistcore.RECEIVER_EXTRA_CLASS";

    private Context context;
    private Class<? extends Service> mediaServiceClass;

    private boolean enabled = true;

    private Bitmap appIconBitmap;
    private MediaSessionCompat mediaSession;

    /**
     * Creates a new MediaControlsHelper object
     *
     * @param context The context to use for holding a MediaSession and sending action intents
     * @param mediaServiceClass The class for the service that owns the backing MediaService and to notify of playback actions
     */
    public MediaControlsHelper(Context context, Class<? extends Service> mediaServiceClass) {
        this.context = context;
        this.mediaServiceClass = mediaServiceClass;

        ComponentName componentName = new ComponentName(context, MediaControlsReceiver.class.getName());

        mediaSession = new MediaSessionCompat(context, SESSION_TAG, componentName, getMediaButtonReceiverPendingIntent(componentName));
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new SessionCallback());
    }

    public void release() {
        if (mediaSession != null) {
            mediaSession.release();
        }

        context = null;
        appIconBitmap = null;
        mediaServiceClass = null;
        mediaServiceClass = null;
    }

    /**
     * Sets weather the RemoteViews and controls are shown when media is playing or
     * ready for playback (e.g. paused).  The information
     * will need to be updated by calling {@link #setBaseInformation(int)}
     * and {@link #update(String, String, String, Bitmap, NotificationHelper.NotificationMediaState)}
     *
     * @param enabled True if the RemoteViews and controls should be shown
     */
    public void setMediaControlsEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;

        //Remove the remote views and controls when disabling
        if (!enabled) {
            mediaSession.setActive(false);
        }
    }

    /**
     * Sets the basic information for the remote views and controls that don't need to be updated.  Additionally, when
     * the mediaServiceClass is set the big notification will send intents to that service to notify of
     * button clicks.  These intents will have an action from
     * <ul>
     *     <li>{@link RemoteActions#ACTION_PLAY_PAUSE}</li>
     *     <li>{@link RemoteActions#ACTION_PREVIOUS}</li>
     *     <li>{@link RemoteActions#ACTION_NEXT}</li>
     * </ul>
     *
     * @param appIcon The applications icon resource
     */
    public void setBaseInformation(@DrawableRes int appIcon) {
        appIconBitmap = BitmapFactory.decodeResource(context.getResources(), appIcon);
    }

    /**
     * Sets the volatile information for the remote views and controls.  This information is expected to
     * change frequently.
     *
     * @param title The title to display for the notification (e.g. A song name)
     * @param album The name of the album the media is found in
     * @param artist The name of the artist for the media item
     * @param notificationMediaState The current media state for the expanded (big) notification
     */
    @SuppressWarnings("ResourceType") //getPlaybackOptions() and getPlaybackState() return the correctly annotated items
    public void update(String title, String album, String artist, Bitmap mediaArtwork, NotificationHelper.NotificationMediaState notificationMediaState) {
        //Updates the current media MetaData
        MediaMetadataCompat.Builder metaDataBuilder = new MediaMetadataCompat.Builder();
        metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, appIconBitmap);
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);

        if (mediaArtwork != null) {
            metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaArtwork);
        }

        mediaSession.setMetadata(metaDataBuilder.build());


        //Updates the available playback controls
        PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
        playbackStateBuilder.setActions(getPlaybackOptions(notificationMediaState));
        playbackStateBuilder.setState(getPlaybackState(notificationMediaState.isPlaying()), PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
        Log.d(TAG, "update, controller is null ? " + (mediaSession.getController() == null ? "true" : "false"));

        if (enabled && !mediaSession.isActive()) {
            mediaSession.setActive(true);
        }
    }

    protected PendingIntent getMediaButtonReceiverPendingIntent(ComponentName componentName) {
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(componentName);

        mediaButtonIntent.putExtra(RECEIVER_EXTRA_CLASS, mediaServiceClass.getName());
        return PendingIntent.getBroadcast(context, 0, mediaButtonIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    @PlaybackStateCompat.State
    protected int getPlaybackState(boolean isPlaying) {
        return isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
    }

    /**
     * Determines the available playback commands supported for the current media state
     *
     * @param mediaState The current media playback state
     * @return The available playback options
     */
    @PlaybackStateCompat.Actions
    protected long getPlaybackOptions(NotificationHelper.NotificationMediaState mediaState) {
        long availableActions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE;

        if (mediaState.isNextEnabled()) {
            availableActions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        }

        if (mediaState.isPreviousEnabled()) {
            availableActions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        }

        return availableActions;
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param action The action to use
     * @param serviceClass The service class to notify of intents
     * @return The resulting PendingIntent
     */
    protected PendingIntent createPendingIntent(String action, Class<? extends Service> serviceClass) {
        Intent intent = new Intent(context, serviceClass);
        intent.setAction(action);

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * A simple callback class to listen to the notifications received from the remote view
     * and forward them to the {@link #mediaServiceClass}
     */
    protected class SessionCallback extends MediaSessionCompat.Callback {
        private PendingIntent playPausePendingIntent, nextPendingIntent, previousPendingIntent;

        public SessionCallback() {
            super();

            playPausePendingIntent = createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, mediaServiceClass);
            nextPendingIntent = createPendingIntent(RemoteActions.ACTION_NEXT, mediaServiceClass);
            previousPendingIntent = createPendingIntent(RemoteActions.ACTION_PREVIOUS, mediaServiceClass);
        }

        @Override
        public void onPlay() {
            sendPendingIntent(playPausePendingIntent);
        }

        @Override
        public void onPause() {
            sendPendingIntent(playPausePendingIntent);
        }

        @Override
        public void onSkipToNext() {
            sendPendingIntent(nextPendingIntent);
        }

        @Override
        public void onSkipToPrevious() {
            sendPendingIntent(previousPendingIntent);
        }

        private void sendPendingIntent(PendingIntent pi) {
            try {
                pi.send();
            } catch (Exception e) {
                Log.d(TAG, "Error sending media controls pending intent", e);
            }
        }
    }
}
