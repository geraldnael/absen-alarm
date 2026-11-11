package id.gerald.absenalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object Scheduler {
    private fun fire(c: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun set(c: Context, id: Int, at: Long) {
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire(c, id))
    }

    fun scheduleAll(c: Context) = Store.load(c).forEach { schedule(c, it) }

    fun schedule(c: Context, a: Alarm) {
        val t = a.nextTrigger()
        if (t == null) am(c).cancel(fire(c, a.id)) else set(c, a.id, t)
    }

    fun snooze(c: Context, a: Alarm, minutes: Int) = set(c, a.id, System.currentTimeMillis() + minutes * 60_000L)

    fun cancel(c: Context, id: Int) = am(c).cancel(fire(c, id))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        Store.load(c).firstOrNull { it.id == id }?.let { Scheduler.schedule(c, it) } // jadwalkan kemunculan berikutnya
        ContextCompat.startForegroundService(c, Intent(c, RingService::class.java).putExtra("id", id))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Scheduler.scheduleAll(c)
}
