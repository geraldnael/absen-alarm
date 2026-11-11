package id.gerald.absenalarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF12162B)
val Stamp = Color(0xFF0B7A6B)
val Sun = Color(0xFFFFCF33)
val Bg = Color(0xFFECEEF6)
val DAYS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ink, secondary = Stamp, tertiary = Sun, background = Bg)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { Screen() }
            }
        }
    }
}

fun soundName(c: Context, s: String?): String {
    if (s == null) return "Nada alarm bawaan"
    val u = Uri.parse(s)
    return try {
        c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: RingtoneManager.getRingtone(c, u)?.getTitle(c) ?: "Nada pilihan"
    } catch (e: Exception) {
        "Nada pilihan"
    }
}

fun daysText(d: Set<Int>) =
    if (d.size == 7) "Setiap hari" else DAYS.filterIndexed { i, _ -> (i + 1) in d }.joinToString(" ").ifEmpty { "Pilih hari" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDialog(init: Alarm, onSave: (Alarm) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var d by remember { mutableStateOf(init) }
    val time = rememberTimePickerState(init.hour, init.minute, true)

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            d = d.copy(sound = u.toString())
        }
    }
    val pickSystem = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        @Suppress("DEPRECATION")
        val u = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (u != null) d = d.copy(sound = u.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(d.copy(hour = time.hour, minute = time.minute, enabled = true)) }, enabled = d.days.isNotEmpty()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = d.label, onValueChange = { d = d.copy(label = it.take(40)) }, label = { Text("Nama") }, singleLine = true)
                TimeInput(state = time)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAYS.forEachIndexed { i, n ->
                        val day = i + 1
                        FilterChip(
                            selected = day in d.days,
                            onClick = { d = d.copy(days = if (day in d.days) d.days - day else d.days + day) },
                            label = { Text(n) },
                        )
                    }
                }
                Text("Nada: ${soundName(ctx, d.sound)}", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFile.launch(arrayOf("audio/*")) }) { Text("Pilih file") }
                    OutlinedButton(onClick = {
                        pickSystem.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false),
                        )
                    }) { Text("Nada sistem") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Getar", modifier = Modifier.weight(1f))
                    Switch(checked = d.vibrate, onCheckedChange = { d = d.copy(vibrate = it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        ctx.startForegroundService(
                            Intent(ctx, RingService::class.java)
                                .putExtra("id", d.id).putExtra("label", d.label)
                                .putExtra("sound", d.sound).putExtra("vib", d.vibrate),
                        )
                    }) { Text("Tes bunyi") }
                    TextButton(onClick = onDelete) { Text("Hapus", color = Color(0xFFA4261D)) }
                }
            }
        },
    )
}
