package frameluma.app.ui

import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.AndroidExternalSurfaceScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/** Silent original loop decoded to its own surface; the visible playhead drives light colours. */
@Composable
internal fun BackdropMedia(scene: BackdropScene, embedded: Boolean, video: BackdropVideo, onPreparing: () -> Unit, onReady: () -> Unit, onFailure: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val ready by rememberUpdatedState(onReady)
    val preparing by rememberUpdatedState(onPreparing)
    val failure by rememberUpdatedState(onFailure)
    var supported by remember(video) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(video) {
        supported = withContext(Dispatchers.IO) { HardwareVideoDecoders.supports(video) }
        if (supported == false) failure()
    }
    if (supported != true) return
    val initialize: AndroidExternalSurfaceScope.() -> Unit = {
        onSurface { surface, _, _ ->
            preparing()
            val player = MediaPlayer()
            var released = false
            var started = false
            var latestPosition = scene.elapsedMs
            val timeline = MediaTimeline { player.currentPosition.toLong() }
            val position: () -> Long = {
                if (!released && started) {
                    try { latestPosition = timeline.current(SystemClock.elapsedRealtime()) }
                    catch (_: IllegalStateException) { failure() }
                }
                latestPosition
            }
            fun release() {
                if (released) return
                if (started) position()
                released = true
                if (scene.mediaPosition === position) scene.mediaPosition = null
                if (started) scene.clock.align(latestPosition, SystemClock.elapsedRealtime())
                player.setOnPreparedListener(null)
                player.setOnSeekCompleteListener(null)
                player.setOnInfoListener(null)
                player.setOnErrorListener(null)
                player.release()
            }
            surface.onDestroyed { release() }
            try {
                player.setSurface(surface)
                player.isLooping = true
                player.setVolume(0f, 0f)
                player.setOnErrorListener { _, what, extra ->
                    Log.w("FrameLumaUi", "Video background unavailable ($what/$extra); falling back to GPU")
                    release()
                    failure()
                    true
                }
                player.setOnInfoListener { _, what, _ ->
                    if (!released) {
                        val now = SystemClock.elapsedRealtime()
                        when (what) {
                            MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                                timeline.refresh(now)
                                if (!started) {
                                    started = true
                                    scene.mediaPosition = position
                                    Log.i("FrameLumaUi", "Video background ${player.videoWidth}x${player.videoHeight}, ${video.fps}fps, silent loop")
                                    ready()
                                }
                            }
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> if (started) timeline.buffering(true, now)
                            MediaPlayer.MEDIA_INFO_BUFFERING_END -> if (started) timeline.buffering(false, now)
                        }
                    }
                    false
                }
                // Optional timing correction only; playback never waits for this vendor callback.
                player.setOnSeekCompleteListener {
                    if (!released && started) timeline.refresh(SystemClock.elapsedRealtime())
                }
                player.setOnPreparedListener {
                    if (!released) {
                        player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                        // Opening the page starts a fresh loop, like the original application.
                        // Seeking straight after start can flush the first video plane back to black.
                        scene.clock.align(0, SystemClock.elapsedRealtime())
                        player.start()
                    }
                }
                context.resources.openRawResourceFd(video.resource).use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                player.prepareAsync()
                awaitCancellation()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w("FrameLumaUi", "Video background initialization failed; falling back to GPU", error)
                failure()
            } finally { release() }
        }
    }
    if (embedded) AndroidEmbeddedExternalSurface(Modifier.fillMaxSize(), onInit = initialize)
    else AndroidExternalSurface(Modifier.fillMaxSize(), onInit = initialize)
}

/** Avoid CPU decoding on devices that do not advertise hardware HEVC at the asset's format. */
private fun hasHardwareDecoder(video: BackdropVideo): Boolean = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
        val hardware = if (Build.VERSION.SDK_INT >= 29) codec.isHardwareAccelerated else {
            val name = codec.name.lowercase()
            (name.startsWith("omx.") || name.startsWith("c2.")) &&
                !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.contains(".sw.")
        }
        !codec.isEncoder && hardware && codec.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } &&
            codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).videoCapabilities
                ?.areSizeAndRateSupported(video.width, video.height, video.fps.toDouble()) == true
    }
}.getOrDefault(false)

/** Codec service queries are process-scoped, bounded to the three packaged formats. */
private object HardwareVideoDecoders {
    private val supported = mutableMapOf<BackdropVideo, Boolean>()
    @Synchronized fun supports(video: BackdropVideo) = supported.getOrPut(video) { hasHardwareDecoder(video) }
}
