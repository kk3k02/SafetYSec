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

/**
 * A reusable video player component for Jetpack Compose using Media3 ExoPlayer.
 *
 * @param videoUrl The URL or URI of the video to be played.
 * @param modifier Modifier to be applied to the player layout.
 */
@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Initialize and remember ExoPlayer instance. 
    // It will be re-initialized only if the videoUrl changes.
    val exoPlayer = remember(videoUrl) { 
        ExoPlayer.Builder(context).build().apply { 
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            // Set to false by default to let the user start playback manually
            playWhenReady = false 
        } 
    }
    
    // Manage ExoPlayer lifecycle to ensure resources are released when 
    // the composable leaves the composition or videoUrl changes.
    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Bridge between Jetpack Compose and the traditional Android View (PlayerView)
    AndroidView(
        factory = { ctx -> 
            PlayerView(ctx).apply { 
                player = exoPlayer
                useController = true // Enable playback controls (play/pause, seek, etc.)
                setBackgroundColor(android.graphics.Color.BLACK)
            } 
        }, 
        modifier = modifier.fillMaxSize()
    )
}
