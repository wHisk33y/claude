package de.knuffi.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.sound.TrickVoice
import de.knuffi.app.sound.VoiceState
import de.knuffi.app.ui.components.GlassIconButton
import de.knuffi.app.ui.theme.LocalPalette

/** The speech commands for tricks, with the microphone permission. */
@Composable
fun rememberTrickVoice(): TrickVoice {
    val context = LocalContext.current
    val voice = remember { TrickVoice(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.listen() else GameRepository.message("Ohne Mikrofon kann dein Haustier dich leider nicht hören.")
    }
    voice.requestPermission = { launcher.launch(android.Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit) {
        // talking to the pet (heard by the microphone reactions) starts listening for a command
        val wake = { voice.wake() }
        VoiceState.wake = wake
        onDispose {
            if (VoiceState.wake === wake) VoiceState.wake = null
            voice.destroy()
        }
    }
    return voice
}

/** 🎙️ button that pulses while listening. */
@Composable
fun VoiceButton(voice: TrickVoice, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val p = LocalPalette.current
    val listening = VoiceState.listening
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Box(modifier, contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = 1.1f + 0.25f * pulse
                        scaleY = 1.1f + 0.25f * pulse
                        alpha = 0.6f
                    }
                    .clip(CircleShape)
                    .background(p.pink),
            )
        }
        GlassIconButton(if (listening) "👂" else "🎙️", { voice.toggle() }, size = size)
    }
}
