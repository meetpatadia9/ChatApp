package com.ipsmeet.chatapp.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ipsmeet.chatapp.R
import com.ipsmeet.chatapp.activities.MainActivity

const val channelId = "chatApp_notification"
const val channelName = "com.ipsmeet.chatapp"

class MyFirebaseMessagingService: FirebaseMessagingService() {

    /*
        Generate the notification
    */
    private fun generateNotification(name: String, message: String) {

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        //  we need `PendingIntents` when we have an Intent that we have to use in future
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)     //  `FLAG_ONE_SHOT` indicates that we to use `PendingIntent` only once

        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.wechat)
            .setAutoCancel(true)
            .setContentTitle(name)
            .setContentText(message)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000))    //  setVibrate(longArrayOf(vibrates, relax, vibrates, relax))
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setContent(getRemoteView(name, message))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    /*
        Attach the notification with custom layout
    */
    @SuppressLint("RemoteViewLayout")
    private fun getRemoteView(name: String, message: String): RemoteViews {
        val remoteViews = RemoteViews("com.ipsmeet.chatapp", R.layout.layout_notification)

        remoteViews.setTextViewText(R.id.notification_title, name)
        remoteViews.setTextViewText(R.id.notification_text, message)
        remoteViews.setImageViewResource(R.id.notification_img, R.mipmap.wechat)

        return remoteViews
    }

    /*
        show the notification
    */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.e("TAG", "onMessageReceived: Data Received...")
        generateNotification(message.notification!!.title!!, message.notification!!.body!!)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("onNewToken()", token)
    }

}