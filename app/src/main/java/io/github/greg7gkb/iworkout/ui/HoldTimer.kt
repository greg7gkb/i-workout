package io.github.greg7gkb.iworkout.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.greg7gkb.iworkout.R
import kotlinx.coroutines.delay

// Counts down a timed Exercise's hold, then buzzes and beeps. `onStop` gets the seconds held: the full
// target when it runs out, or less if stopped early. It times off the clock rather than counting ticks,
// so it stays accurate across recompositions and scrolling.
@Composable
fun HoldTimer(seconds: Int, onStop: (heldSeconds: Int) -> Unit) {
    val context = LocalContext.current
    var startedAt by rememberSaveable { mutableStateOf<Long?>(null) } // SystemClock.elapsedRealtime()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val start = startedAt
    if (start != null) {
        LaunchedEffect(start) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                if (now - start >= seconds * 1000L) break
                delay(100)
            }
            signalDone(context)
            startedAt = null
            onStop(seconds)
        }
    }

    // One button throughout: it starts the timer, then shows the countdown and stops it, so nothing jumps.
    OutlinedButton(
        onClick = {
            if (start == null) {
                now = SystemClock.elapsedRealtime()
                startedAt = now
            } else {
                startedAt = null
                onStop(((SystemClock.elapsedRealtime() - start) / 1000).toInt())
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(painterResource(R.drawable.ic_timer), contentDescription = null, modifier = Modifier.size(20.dp))
        val label = if (start == null) {
            "Start ${formatClock(seconds)} timer"
        } else {
            // Rounds up, so it shows 0:30 at the start and reaches 0:00 only when time is up.
            val remainingMs = (seconds * 1000L - (now - start)).coerceAtLeast(0)
            "${formatClock(((remainingMs + 999) / 1000).toInt())} · Stop"
        }
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

// A double buzz and a beep, loud enough to notice mid-plank. Waits for the beep to finish before releasing it.
private suspend fun signalDone(context: Context) {
    context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    // ToneGenerator can fail to initialize on some devices; the buzz still happens.
    val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull() ?: return
    try {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
        delay(700)
    } finally {
        tone.release()
    }
}
