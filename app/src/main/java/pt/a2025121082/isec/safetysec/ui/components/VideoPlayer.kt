package pt.a2025121082.isec.safetysec.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) { 
        ExoPlayer.Builder(context).build().apply { 
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = false 
        } 
    }
    
    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx -> 
            PlayerView(ctx).apply { 
                player = exoPlayer
                useController = true
                setBackgroundColor(android.graphics.Color.BLACK)
            } 
        }, 
        modifier = modifier.fillMaxSize()
    )
}
