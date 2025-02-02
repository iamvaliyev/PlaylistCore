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

package com.devbrackets.android.playlistcore.components.notification

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.data.MediaInfo
import com.devbrackets.android.playlistcore.data.RemoteActions

/**
 * A default implementation for the [PlaylistNotificationProvider] that will correctly
 * handle both normal and large notifications, remote action registration, categories,
 * channels, etc.
 *
 * *Note* The default channel this provides only supports an English name and description
 * so it is recommended that you extend this class and override [buildNotificationChannel]
 * to provide your own custom name and description.
 */
open class DefaultPlaylistNotificationProvider(protected val context: Context) : PlaylistNotificationProvider {
  companion object {
    const val CHANNEL_ID = "PlaylistCoreMediaNotificationChannel"
  }

  protected val notificationManager: NotificationManager by lazy {
    context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  }

  protected open val clickPendingIntent: PendingIntent?
    get() = null

  override fun buildNotification(info: MediaInfo, mediaSession: MediaSessionCompat, serviceClass: Class<out Service>): Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      buildNotificationChannel()
    }

    return NotificationCompat.Builder(context, CHANNEL_ID).apply {
      setSmallIcon(info.appIcon)
      setLargeIcon(info.largeNotificationIcon)

      var contentText = info.album
      if (info.artist.isNotBlank()) {
        contentText += if (contentText.isNotBlank()) " - " + info.artist else info.artist
      }

      setContentTitle(info.title)
      setContentText(contentText)

      setContentIntent(clickPendingIntent)
      setDeleteIntent(createPendingIntent(serviceClass, RemoteActions.ACTION_STOP))

      val allowSwipe = !(info.mediaState.isPlaying)
      setAutoCancel(allowSwipe)
      setOngoing(!allowSwipe)

      setCategory(Notification.CATEGORY_TRANSPORT)
      setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

      setActions(this, info, serviceClass)
      setStyle(buildMediaStyle(mediaSession, serviceClass))
    }.build()
  }

  protected open fun setActions(builder: NotificationCompat.Builder, info: MediaInfo, serviceClass: Class<out Service>) {
    with(info.mediaState) {
      // Previous
      var actionIcon = if (isPreviousEnabled) R.drawable.playlistcore_notification_previous else R.drawable.playlistcore_notification_previous_disabled
      var title = context.resources.getString(R.string.playlistcore_default_notification_previous)
      builder.addAction(actionIcon, title, createPendingIntent(serviceClass, RemoteActions.ACTION_PREVIOUS))

      // Play/Pause
      actionIcon = if (isPlaying) {
        title = context.resources.getString(R.string.playlistcore_default_notification_pause)
        if (isLoading) R.drawable.playlistcore_notification_pause_disabled else R.drawable.playlistcore_notification_pause
      } else {
        title = context.resources.getString(R.string.playlistcore_default_notification_play)
        if (isLoading) R.drawable.playlistcore_notification_play_disabled else R.drawable.playlistcore_notification_play
      }
      builder.addAction(actionIcon, title, createPendingIntent(serviceClass, RemoteActions.ACTION_PLAY_PAUSE))

      // Next
      actionIcon = if (isNextEnabled) R.drawable.playlistcore_notification_next else R.drawable.playlistcore_notification_next_disabled
      title = context.resources.getString(R.string.playlistcore_default_notification_next)
      builder.addAction(actionIcon, title, createPendingIntent(serviceClass, RemoteActions.ACTION_NEXT))
    }
  }

  protected open fun buildMediaStyle(mediaSession: MediaSessionCompat, serviceClass: Class<out Service>): MediaStyle {
    return with(MediaStyle()) {
      setMediaSession(mediaSession.sessionToken)
      setShowActionsInCompactView(0, 1, 2) // previous, play/pause, next
      setShowCancelButton(true)
      setCancelButtonIntent(createPendingIntent(serviceClass, RemoteActions.ACTION_STOP))
    }
  }

  /**
   * Builds the notification channel using the default name and description (English Only)
   * if the channel hasn't already been created
   */
  @TargetApi(Build.VERSION_CODES.O)
  protected open fun buildNotificationChannel() {
    if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
      val name = context.resources.getString(R.string.playlistcore_default_notification_channel_name)
      val description = context.resources.getString(R.string.playlistcore_default_notification_channel_description)
      buildNotificationChannel(name, description)
    }
  }

  /**
   * Builds the notification channel using the specified name and description
   */
  @TargetApi(Build.VERSION_CODES.O)
  protected open fun buildNotificationChannel(name: CharSequence, description: String) {
    val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
    channel.description = description
    channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    notificationManager.createNotificationChannel(channel)
  }

  /**
   * Creates a PendingIntent for the given action to the specified service
   *
   * @param action The action to use
   * @param serviceClass The service class to notify of intents
   * @return The resulting PendingIntent
   */
  protected open fun createPendingIntent(serviceClass: Class<out Service>, action: String): PendingIntent {
    val intent = Intent(context, serviceClass)
    intent.action = action

    return PendingIntent.getService(context, 0, intent, getIntentFlags())
  }

  protected open fun getIntentFlags(): Int {
    return when {
      Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> PendingIntent.FLAG_UPDATE_CURRENT
      else -> PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
  }
}