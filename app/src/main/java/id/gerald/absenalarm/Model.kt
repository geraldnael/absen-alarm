package id.gerald.absenalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

const val MONEV_URL = "https://monev.maganghub.kemnaker.go.id/dashboard/riwayat"

/** days: 1 = Senin ... 7 = Minggu */
data class Alarm(
    val id: Int,
    val label: String = "Absen masuk",
    val hour: Int = 7,
    val minute: Int = 30,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val sound: String? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun load(c: Context): List<Alarm> {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Alarm(
                id = o.getInt("id"),
                label = o.getString("label"),
                hour = o.getInt("h"),
                minute = o.getInt("m"),
                days = o.getString("days").split(",").filter { s -> s.isNotEmpty() }.map { s -> s.toInt() }.toSet(),
                sound = if (o.isNull("sound")) null else o.getString("sound"),
                vibrate = o.getBoolean("vib"),
                enabled = o.getBoolean("on"),
            )
        }
    }

    fun save(c: Context, list: List<Alarm>) {
        val a = JSONArray()
        list.forEach { x ->
            a.put(
                JSONObject()
                    .put("id", x.id).put("label", x.label).put("h", x.hour).put("m", x.minute)
                    .put("days", x.days.sorted().joinToString(","))
                    .put("sound", x.sound ?: JSONObject.NULL)
                    .put("vib", x.vibrate).put("on", x.enabled),
            )
        }
        prefs(c).edit().putString("list", a.toString()).apply()
    }
}

fun Alarm.nextTrigger(from: Long = System.currentTimeMillis()): Long? {
    if (!enabled || days.isEmpty()) return null
    for (off in 0..7) {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            add(Calendar.DAY_OF_YEAR, off)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // Senin = 1 ... Minggu = 7
        if (dow in days && c.timeInMillis > from) return c.timeInMillis
    }
    return null
}
