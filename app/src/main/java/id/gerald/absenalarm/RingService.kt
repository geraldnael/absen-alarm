package id.gerald.absenalarm

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

class RingService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { Store.markDone(this); stopAll(); return START_NOT_STICKY }
            ACTION_SNOOZE -> {
                alarm(intent)?.let { Scheduler.snooze(this, it, 10) }
                stopAll(); return START_NOT_STICKY
            }
        }
        val a = alarm(intent) ?: run { stopSelf(); return START_NOT_STICKY }
        val n = buildNotification(a)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
        play(a)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopAll() }, 120_000) // berhenti otomatis setelah 2 menit
        return START_NOT_STICKY
    }

    private fun alarm(i: Intent?): Alarm? {
        if (i == null) return null
        if (i.hasExtra("label")) { // alarm tes dari layar edit
            return Alarm(i.getIntExtra("id", 0), i.getStringExtra("label") ?: "Absen", sound = i.getStringExtra("sound"), vibrate = i.getBooleanExtra("vib", true))
        }
        val id = i.getIntExtra("id", -1)
        return Store.load(this).firstOrNull { it.id == id }
    }

    private fun tryStart(uri: Uri): Boolean = try {
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(this@RingService, uri)
            isLooping = true
            prepare()
            start()
        }
        true
    } catch (e: Exception) {
        player?.release(); player = null
        false
    }

    private fun play(a: Alarm) {
        player?.release(); player = null
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val chosen = a.sound?.let { Uri.parse(it) }
        if (chosen == null || !tryStart(chosen)) tryStart(default)
        if (a.vibrate) {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        }
    }

    private fun buildNotification(a: Alarm): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("alarm", "Alarm absen", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
            },
        )
        fun svc(action: String, rc: Int) = PendingIntent.getService(
            this, rc, Intent(this, RingService::class.java).setAction(action).putExtra("id", a.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(this, 1, Intent(this, OpenActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        return Notification.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(a.label)
            .setContentText("Waktunya absen. Ketuk untuk membuka Monev.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Sudah absen", svc(ACTION_STOP, 2))
            .addAction(0, "Tunda 10 mnt", svc(ACTION_SNOOZE, 3))
            .build()
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        @Suppress("DEPRECATION")
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release(); player = null
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_STOP = "id.gerald.absenalarm.STOP"
        const val ACTION_SNOOZE = "id.gerald.absenalarm.SNOOZE"
    }
}

/** Dibuka saat notifikasi diketuk: hentikan bunyi lalu buka Monev. */
class OpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, RingService::class.java).setAction(RingService.ACTION_STOP))
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MONEV_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
