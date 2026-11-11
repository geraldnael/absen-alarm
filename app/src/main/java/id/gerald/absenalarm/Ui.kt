package id.gerald.absenalarm

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Mute = Color(0xFF5A6082)
private val Line = Color(0xFFCFD3E4)

private class Particle(val x: Float, val y: Float, val speed: Float, val r: Float)

/** Muncul dengan geser naik + fade, berurutan menurut index. */
@Composable
private fun Rise(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 70L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "rise")
    Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 48f }) { content() }
}

/** Jam miring 3D (rotasi X/Y), partikel melayang, dan stempel yang berdenyut saat ditekan. */
@Composable
private fun Stage(done: Boolean, burst: Int, nowMs: Long, onStamp: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "stage")
    val ry by inf.animateFloat(-24f, 24f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ry")
    val rx by inf.animateFloat(-7f, 7f, infiniteRepeatable(tween(3700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rx")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "drift")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "breathe")
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(burst) {
        if (burst > 0) {
            pulse.animateTo(1.28f, tween(110))
            pulse.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f))
        }
    }
    val body by animateColorAsState(if (done) Stamp else Ink, tween(600), label = "body")
    val fill by animateColorAsState(if (done) Color.Transparent else Sun, tween(500), label = "fill")
    val particles = remember { List(70) { Particle(Random.nextFloat(), Random.nextFloat(), 0.03f + Random.nextFloat() * 0.08f, 1.5f + Random.nextFloat() * 2.5f) } }

    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val k = (pulse.value - 1f) * 2f
            particles.forEach { p ->
                val y = (p.y + drift * p.speed * 20f) % 1f
                val px = size.width / 2 + (p.x * size.width - size.width / 2) * (1f + k)
                val py = size.height / 2 + ((1f - y) * size.height - size.height / 2) * (1f + k)
                drawCircle(Sun.copy(alpha = 0.75f), p.r.dp.toPx(), Offset(px, py))
            }
        }
        Canvas(
            Modifier.size(250.dp).graphicsLayer {
                rotationY = ry
                rotationX = rx
                cameraDistance = 14f * density
                scaleX = pulse.value
                scaleY = pulse.value
            },
        ) {
            val c = center
            val r = size.minDimension / 2 * 0.94f
            drawCircle(Color.White.copy(alpha = 0.55f), r, c)
            drawCircle(body.copy(alpha = 0.25f), r, c + Offset(0f, 10.dp.toPx()), style = Stroke(2.dp.toPx()))
            drawCircle(body, r, c, style = Stroke(5.dp.toPx()))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0).toFloat()
                val long = i % 3 == 0
                val r1 = r * (if (long) 0.78f else 0.84f)
                val r2 = r * 0.92f
                drawLine(
                    body, c + Offset(sin(a) * r1, -cos(a) * r1), c + Offset(sin(a) * r2, -cos(a) * r2),
                    strokeWidth = (if (long) 4 else 2).dp.toPx(), cap = StrokeCap.Round,
                )
            }
            val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
            val min = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
            val hr = cal.get(Calendar.HOUR) + min / 60f
            fun hand(deg: Float, len: Float, w: Float) {
                val a = Math.toRadians(deg.toDouble()).toFloat()
                drawLine(Sun, c, c + Offset(sin(a) * r * len, -cos(a) * r * len), strokeWidth = w, cap = StrokeCap.Round)
            }
            hand(hr / 12f * 360f, 0.5f, 7.dp.toPx())
            hand(min / 60f * 360f, 0.72f, 5.dp.toPx())
            drawCircle(body, 6.dp.toPx(), c)
        }
        Box(
            Modifier.size(150.dp)
                .graphicsLayer {
                    rotationZ = -8f
                    val s = pulse.value * (if (done) 1f else 1f + breathe * 0.04f)
                    scaleX = s; scaleY = s
                }
                .clip(CircleShape).background(fill).border(4.dp, body, CircleShape)
                .clickable(enabled = !done, onClick = onStamp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (done) "TERCATAT" else "SUDAH ABSEN", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = body, textAlign = TextAlign.Center)
                Text(if (done) SimpleDateFormat("d MMM", Locale("id", "ID")).format(Date(nowMs)) else "ketuk setelah absen", fontSize = 11.sp, color = body)
            }
        }
    }
}

@Composable
private fun Week(done: Set<String>) {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7))
    val today = dayKey()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 7) {
            val k = dayKey(c)
            val on = k in done
            val scale by animateFloatAsState(if (on) 1f else 0.92f, spring(dampingRatio = 0.4f), label = "wd")
            val bg by animateColorAsState(if (on) Stamp else Color.Transparent, label = "wdBg")
            Box(
                Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape).background(bg)
                    .border(2.dp, if (on) Stamp else if (k == today) Ink else Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(DAYS[i].take(1), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (on) Color.White else Ink) }
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun streak(done: Set<String>): Int {
    var n = 0
    val c = Calendar.getInstance()
    for (i in 0 until 400) {
        val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        if (dow <= 5) {
            if (dayKey(c) in done) n++ else if (i > 0) break
        }
        c.add(Calendar.DAY_OF_YEAR, -1)
    }
    return n
}

private fun nextText(alarms: List<Alarm>, now: Long): String {
    val n = alarms.mapNotNull { a -> a.nextTrigger(now)?.let { a to it } }.minByOrNull { it.second }
        ?: return "Belum ada alarm aktif"
    val mins = ((n.second - now) / 60000).toInt()
    val rel = when {
        mins < 60 -> "$mins menit lagi"
        mins < 1440 -> "${mins / 60} jam ${mins % 60} mnt lagi"
        else -> "${mins / 1440} hari lagi"
    }
    return "${n.first.label} · ${"%02d:%02d".format(n.first.hour, n.first.minute)} · $rel"
}

@Composable
private fun AlarmCard(a: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val dim by animateFloatAsState(if (a.enabled) 1f else 0.5f, label = "dim")
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = dim }.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%02d:%02d".format(a.hour, a.minute), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(a.label, fontWeight = FontWeight.SemiBold)
                Text("${daysText(a.days)} · ${soundName(ctx, a.sound)}", fontSize = 12.sp, color = Mute)
            }
            Switch(checked = a.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val alarms = remember { mutableStateListOf<Alarm>().apply { addAll(Store.load(ctx)) } }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var burst by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Scheduler.scheduleAll(ctx)
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val doneSet = remember(now) { Store.done(ctx) }
    val isDone = dayKey() in doneSet

    fun persist(a: Alarm) {
        val i = alarms.indexOfFirst { it.id == a.id }
        if (i >= 0) alarms[i] = a else alarms.add(a)
        Store.save(ctx, alarms.toList())
        Scheduler.schedule(ctx, a)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Rise(0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID")).format(Date(now)), color = Mute, fontSize = 14.sp)
                        Crossfade(isDone, label = "title") { d ->
                            Text(if (d) "Sudah tercatat." else "Sudah absen belum?", fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${streak(doneSet)}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        Text("hari beruntun", fontSize = 11.sp, color = Mute)
                    }
                }
            }
        }
        item {
            Rise(1) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Stage(isDone, burst, now) {
                            Store.markDone(ctx)
                            burst++
                            now = System.currentTimeMillis()
                            ctx.startService(Intent(ctx, RingService::class.java).setAction(RingService.ACTION_STOP)) // diamkan alarm yang sedang bunyi
                        }
                        Week(doneSet)
                        Text(nextText(alarms, now), fontSize = 14.sp, color = Mute, textAlign = TextAlign.Center)
                        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MONEV_URL))) }) {
                            Text("Buka Monev sekarang", fontWeight = FontWeight.Bold, color = Ink)
                        }
                    }
                }
            }
        }
        item { Rise(2) { Text("Alarm", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) } }
        itemsIndexed(alarms, key = { _, a -> a.id }) { i, a ->
            Rise(3 + minOf(i, 4)) { AlarmCard(a, onClick = { editing = a }, onToggle = { persist(a.copy(enabled = it)) }) }
        }
        item {
            Rise(4) {
                OutlinedButton(
                    onClick = { editing = Alarm(id = (System.currentTimeMillis() and 0x7fffffff).toInt()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ Tambah alarm") }
            }
        }
    }

    editing?.let { cur ->
        EditDialog(
            init = cur,
            onSave = { persist(it); editing = null },
            onDelete = {
                alarms.removeAll { it.id == cur.id }
                Store.save(ctx, alarms.toList()); Scheduler.cancel(ctx, cur.id); editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
