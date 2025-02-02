/*
 * Copyright (C) 2016 - 2021 Brian Wernick
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

package com.devbrackets.android.playlistcore.components.mediasession

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.view.ContentInfoCompat
import com.devbrackets.android.playlistcore.data.MediaInfo
import com.devbrackets.android.playlistcore.data.RemoteActions

open class DefaultMediaSessionProvider(val context: Context, val serviceClass: Class<out Service>) : MediaSessionCompat.Callback(), MediaSessionProvider {
  companion object {
    const val SESSION_TAG = "DefaultMediaSessionProvider.Session"
    const val RECEIVER_EXTRA_CLASS = "com.devbrackets.android.playlistcore.RECEIVER_EXTRA_CLASS"
  }

  protected var playPausePendingIntent = createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, serviceClass)
  protected var nextPendingIntent = createPendingIntent(RemoteActions.ACTION_NEXT, serviceClass)
  protected var previousPendingIntent = createPendingIntent(RemoteActions.ACTION_PREVIOUS, serviceClass)

  protected val mediaSession: MediaSessionCompat by lazy {
    val componentName = ComponentName(context, DefaultMediaSessionControlsReceiver::class.java.name)
    MediaSessionCompat(context, SESSION_TAG, componentName, getMediaButtonReceiverPendingIntent(componentName))
  }

  override fun get(): MediaSessionCompat {
    return mediaSession
  }

  override fun update(mediaInfo: MediaInfo) {
    mediaSession.setCallback(this)

    // Updates the current media MetaData
    val metaDataBuilder = MediaMetadataCompat.Builder()
    metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaInfo.title)
    metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mediaInfo.album)
    metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaInfo.artist)

    // Updates the icon
    BitmapFactory.decodeResource(context.resources, mediaInfo.appIcon)?.let {
      metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
    }

    // Updates the artwork
    if (mediaInfo.artwork != null) {
      metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaInfo.artwork)
    }

    mediaSession.setMetadata(metaDataBuilder.build())
  }

  override fun onPlay() {
    sendPendingIntent(playPausePendingIntent)
  }

  override fun onPause() {
    sendPendingIntent(playPausePendingIntent)
  }

  override fun onSkipToNext() {
    sendPendingIntent(nextPendingIntent)
  }

  override fun onSkipToPrevious() {
    sendPendingIntent(previousPendingIntent)
  }

  /**
   * Creates a PendingIntent for the given action to the specified service
   *
   * @param action The action to use
   * @param serviceClass The service class to notify of intents
   * @return The resulting PendingIntent
   */
  protected open fun createPendingIntent(action: String, serviceClass: Class<out Service>): PendingIntent {
    val intent = Intent(context, serviceClass)
    intent.action = action

    return PendingIntent.getService(context, 0, intent, getIntentFlags())
  }

  protected open fun getMediaButtonReceiverPendingIntent(componentName: ComponentName): PendingIntent {
    val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
    mediaButtonIntent.component = componentName

    mediaButtonIntent.putExtra(RECEIVER_EXTRA_CLASS, serviceClass.name)
    return PendingIntent.getBroadcast(context, 0, mediaButtonIntent, getIntentFlags())
  }

  protected open fun sendPendingIntent(pi: PendingIntent) {
    try {
      pi.send()
    } catch (e: Exception) {
      Log.d("DefaultMediaSessionPro", "Error sending media controls pending intent", e)
    }
  }

  protected open fun getIntentFlags(): Int {
    return when {
      Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> PendingIntent.FLAG_UPDATE_CURRENT
      else -> PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
  }
}