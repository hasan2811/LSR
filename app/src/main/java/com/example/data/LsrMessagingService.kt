package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LsrMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(mapOf("fcmToken" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .addOnFailureListener { e ->
                Log.w("LsrMessagingService", "Failed to save FCM token: ${e.message}")
            }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val prefs = getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
        val notifPicAlerts = prefs.getBoolean("notif_pic_alerts", true)
        val notifCriticalAlerts = prefs.getBoolean("notif_critical_alerts", true)

        val data = message.data
        val type = data["type"] ?: "ASSIGNMENT"
        val isCritical = data["isCritical"]?.toBoolean() ?: (type == "CRITICAL")

        if (isCritical && !notifCriticalAlerts) return
        if (!isCritical && !notifPicAlerts) return

        val title = message.notification?.title ?: data["title"] ?: "HSE LSR Alert"
        val body = message.notification?.body ?: data["message"] ?: "Pembaruan aktivitas inspeksi."
        val ruleId = data["ruleId"] ?: ""
        val findingId = data["findingId"]?.toIntOrNull() ?: 0
        val recipientEmail = data["recipientEmail"] ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val dao = AppDatabase.getDatabase(applicationContext).lsrDao()
                dao.insertNotification(
                    AppNotification(
                        recipientEmail = recipientEmail,
                        title = title,
                        message = body,
                        type = type,
                        findingId = findingId,
                        ruleId = ruleId,
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        isSynced = true
                    )
                )
            }
        }

        NotificationHelper.showNotification(
            context = applicationContext,
            notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            title = title,
            message = body,
            isCritical = isCritical
        )
    }
}
