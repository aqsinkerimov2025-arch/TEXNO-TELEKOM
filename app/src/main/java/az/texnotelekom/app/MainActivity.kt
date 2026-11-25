package az.texnotelekom.app

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.Environment
import android.media.MediaRecorder
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.provider.MediaStore
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.MediaController
import android.widget.VideoView
import android.view.TextureView
import android.graphics.Matrix
import android.content.ContentValues
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import android.provider.OpenableColumns
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

private val bg = Color(0xFF050A18)
private val panel = Color(0xFF111A2C)
private val accent = Color(0xFF168BFF)
private const val INSTANT_PUSH_WORKER_URL = "https://texno-telekom-push-v2.aqsin-kerimov-2025.workers.dev"

private fun triggerInstantPush() {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    user.getIdToken(false).addOnSuccessListener { tokenResult ->
        val idToken = tokenResult.token ?: return@addOnSuccessListener
        Thread {
            try {
                val connection = (URL(INSTANT_PUSH_WORKER_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("idToken", idToken).toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.inputStream.close()
                connection.disconnect()
            } catch (_: Exception) { }
        }.start()
    }
}



private const val ADMIN_EMAIL = "aqsin.kerimov.2025@gmail.com"
private const val CLOUDINARY_CLOUD = "at4nuvsd"
private const val CLOUDINARY_PRESET = "texno_telekom_upload"

private fun remoteColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
} catch (_: Exception) { fallback }

private fun uploadPhoneImage(context: Context, uri: Uri, callback: (Result<String>) -> Unit) {
    Thread {
        try {
            val boundary = "----TexnoTelekom" + UUID.randomUUID().toString().replace("-", "")
            val connection = (URL("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD/image/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 120000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(65536)
            }
            connection.outputStream.use { output ->
                fun field(name: String, value: String) {
                    output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
                }
                field("upload_preset", CLOUDINARY_PRESET)
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"texno-melumat.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
                val input = context.contentResolver.openInputStream(uri) ?: throw Exception("Şəkil oxunmadı.")
                input.use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw Exception("Cloudinary xətası ($code): $response")
                val secureUrl = JSONObject(response).optString("secure_url")
                if (secureUrl.isBlank()) throw Exception("Şəkil linki alınmadı.")
                Handler(Looper.getMainLooper()).post { callback(Result.success(secureUrl)) }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { callback(Result.failure(e)) }
        }
    }.start()
}



private fun uploadGroupVideo(context: Context, uri: Uri, callback: (Result<String>) -> Unit) {
    Thread {
        var connection: HttpURLConnection? = null
        try {
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            val boundary = "----TexnoTelekom" + UUID.randomUUID().toString().replace("-", "")
            connection = (URL("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD/video/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 300000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(65536)
            }
            connection.outputStream.use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n$CLOUDINARY_PRESET\r\n".toByteArray(Charsets.UTF_8))
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"texno-group-video\"\r\nContent-Type: $mime\r\n\r\n".toByteArray(Charsets.UTF_8))
                val input = context.contentResolver.openInputStream(uri) ?: throw Exception("Video faylı oxunmadı.")
                input.use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("Video yüklənmədi ($code): $response")
            val secureUrl = JSONObject(response).optString("secure_url")
            if (secureUrl.isBlank()) throw Exception("Video linki alınmadı.")
            Handler(Looper.getMainLooper()).post { callback(Result.success(secureUrl)) }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { callback(Result.failure(e)) }
        } finally { connection?.disconnect() }
    }.start()
}

private fun fitVideoTexture(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return
    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val scaleX: Float
    val scaleY: Float
    if (viewAspect > videoAspect) {
        scaleX = videoAspect / viewAspect
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    }
    textureView.setTransform(Matrix().apply {
        setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    })
}

@Composable
private fun GroupVideoPlayer(url: String) {
    val context = LocalContext.current
    var showFullscreen by remember(url) { mutableStateOf(false) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var isReady by remember(url) { mutableStateOf(false) }
    var hasEnded by remember(url) { mutableStateOf(false) }
    var playbackPosition by remember(url) { mutableLongStateOf(0L) }
    var playbackDuration by remember(url) { mutableLongStateOf(0L) }
    var isSeeking by remember(url) { mutableStateOf(false) }
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(url)))
            playWhenReady = false
            prepare()
        }
    }

    val textureViewState = remember(player) { mutableStateOf<TextureView?>(null) }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                textureViewState.value?.let {
                    fitVideoTexture(it, videoSize.width, videoSize.height)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isReady = state == androidx.media3.common.Player.STATE_READY ||
                    state == androidx.media3.common.Player.STATE_ENDED
                hasEnded = state == androidx.media3.common.Player.STATE_ENDED
                if (hasEnded) isPlaying = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            textureViewState.value?.let { player.clearVideoTextureView(it) }
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!isSeeking) playbackPosition = player.currentPosition.coerceAtLeast(0L)
            playbackDuration = player.duration.takeIf { it > 0L } ?: 0L
            delay(400)
        }
    }

    fun formatVideoTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%02d:%02d".format(minutes, seconds)
    }

    fun togglePlayback() {
        if (!isReady) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (hasEnded) {
                player.seekTo(0)
                hasEnded = false
            }
            player.play()
        }
    }

    BackHandler(enabled = showFullscreen) { showFullscreen = false }

    @Composable
    fun VideoSurface(fullscreen: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier)
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    TextureView(viewContext).also { texture ->
                        textureViewState.value = texture
                        texture.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                            val size = player.videoSize
                            fitVideoTexture(view as TextureView, size.width, size.height)
                        }
                        player.setVideoTextureView(texture)
                    }
                },
                update = { texture ->
                    if (textureViewState.value !== texture) {
                        textureViewState.value = texture
                        val shouldResume = player.playWhenReady
                        player.setVideoTextureView(texture)
                        player.playWhenReady = shouldResume
                    }
                    val size = player.videoSize
                    fitVideoTexture(texture, size.width, size.height)
                }
            )

            if (isReady) {
                Text(
                    if (isPlaying) "Ⅱ" else "▶",
                    color = Color.White,
                    fontSize = if (fullscreen) 54.sp else 38.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                        .clickable { togglePlayback() }
                        .padding(
                            horizontal = if (fullscreen) 28.dp else 22.dp,
                            vertical = if (fullscreen) 20.dp else 14.dp
                        )
                )
            }

            if (isReady && playbackDuration > 0L) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = if (fullscreen) 18.dp else 10.dp, vertical = if (fullscreen) 12.dp else 6.dp)
                        .padding(bottom = if (fullscreen) 56.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatVideoTime(playbackPosition),
                            color = Color.White,
                            fontSize = if (fullscreen) 14.sp else 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            formatVideoTime(playbackDuration),
                            color = Color.White,
                            fontSize = if (fullscreen) 14.sp else 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (fullscreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "↶ 10 san",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        playbackPosition = target
                                        hasEnded = false
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                            Spacer(Modifier.width(28.dp))
                            Text(
                                "10 san ↷",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        val target = (player.currentPosition + 10_000L).coerceAtMost(playbackDuration)
                                        player.seekTo(target)
                                        playbackPosition = target
                                        hasEnded = false
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (fullscreen) 54.dp else 36.dp)
                            .pointerInput(playbackDuration) {
                                fun updateSeek(x: Float) {
                                    val fraction = (x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    playbackPosition = (fraction * playbackDuration).toLong().coerceIn(0L, playbackDuration)
                                }
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        isSeeking = true
                                        updateSeek(offset.x)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        updateSeek(change.position.x)
                                    },
                                    onDragEnd = {
                                        player.seekTo(playbackPosition.coerceIn(0L, playbackDuration))
                                        isSeeking = false
                                    },
                                    onDragCancel = {
                                        player.seekTo(playbackPosition.coerceIn(0L, playbackDuration))
                                        isSeeking = false
                                    }
                                )
                            }
                    ) {
                        val centerY = size.height / 2f
                        val inset = 8.dp.toPx()
                        val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)
                        val fraction = (playbackPosition.toFloat() / playbackDuration.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(inset, centerY), Offset(inset + trackWidth, centerY), strokeWidth = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(Color(0xFFE53935), Offset(inset, centerY), Offset(inset + trackWidth * fraction, centerY), strokeWidth = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(inset + trackWidth * fraction, centerY))
                    }
                }
            }

            if (fullscreen) {
                Text(
                    "⛶",
                    color = Color.White,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 64.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .clickable {
                            player.pause()
                            isPlaying = false
                            showFullscreen = false
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            if (!fullscreen) {
                Text(
                    "⛶",
                    color = Color.White,
                    fontSize = 30.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                        .clickable { showFullscreen = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }

    if (!showFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            VideoSurface(fullscreen = false)
        }
    }

    if (showFullscreen) {
        Dialog(
            onDismissRequest = { showFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            VideoSurface(fullscreen = true)
        }
    }
}

private data class StickerUpload(val url: String, val mimeType: String, val fileName: String)

private fun clipboardStickerUri(context: Context): Uri? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    for (i in 0 until clip.itemCount) {
        val item = clip.getItemAt(i)
        if (item.uri != null) return item.uri
        val text = item.text?.toString()?.trim().orEmpty()
        if (text.startsWith("content://") || text.startsWith("https://") || text.startsWith("http://")) {
            runCatching { Uri.parse(text) }.getOrNull()?.let { return it }
        }
    }
    return null
}

private fun uploadStickerMedia(context: Context, uri: Uri, callback: (Result<StickerUpload>) -> Unit) {
    Thread {
        try {
            var fileName = "telegram-sticker"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) fileName = cursor.getString(index) ?: fileName
            }
            val mime = context.contentResolver.getType(uri) ?: when {
                fileName.endsWith(".tgs", true) -> "application/gzip"
                fileName.endsWith(".webm", true) -> "video/webm"
                fileName.endsWith(".gif", true) -> "image/gif"
                fileName.endsWith(".webp", true) -> "image/webp"
                else -> "application/octet-stream"
            }
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val resource = when {
                extension == "tgs" || mime == "application/gzip" -> "raw"
                extension == "webm" || mime.startsWith("video/") || mime.startsWith("audio/") -> "video"
                else -> "image"
            }
            val boundary = "----TexnoSticker" + UUID.randomUUID().toString().replace("-", "")
            val connection = (URL("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD/$resource/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 30000; readTimeout = 180000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(65536)
            }
            connection.outputStream.use { output ->
                fun field(name: String, value: String) {
                    output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
                }
                field("upload_preset", CLOUDINARY_PRESET)
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\nContent-Type: $mime\r\n\r\n".toByteArray(Charsets.UTF_8))
                val input = context.contentResolver.openInputStream(uri) ?: throw Exception("Stiker faylı oxunmadı.")
                input.use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) { val count = stream.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw Exception("Stiker yüklənmədi ($code): $response")
                val json = JSONObject(response)
                val secureUrl = json.optString("secure_url")
                if (secureUrl.isBlank()) throw Exception("Stiker linki alınmadı.")
                Handler(Looper.getMainLooper()).post { callback(Result.success(StickerUpload(secureUrl, mime, fileName))) }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { callback(Result.failure(e)) }
        }
    }.start()
}

 
@Suppress("DEPRECATION")
private fun createGroupVoiceRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

private fun uploadGroupAudio(file: File, callback: (Result<String>) -> Unit) {
    Thread {
        try {
            val boundary = "----TexnoAudio" + UUID.randomUUID().toString().replace("-", "")
            val connection = (URL("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD/raw/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 30000; readTimeout = 180000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(65536)
            }
            connection.outputStream.use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n$CLOUDINARY_PRESET\r\n".toByteArray(Charsets.UTF_8))
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice-message.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray(Charsets.UTF_8))
                file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            connection.disconnect()
            if (code !in 200..299) throw Exception("Səs yüklənmədi ($code): $response")
            val url = JSONObject(response).optString("secure_url")
            if (url.isBlank()) throw Exception("Səs linki alınmadı.")
            Handler(Looper.getMainLooper()).post { callback(Result.success(url)) }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { callback(Result.failure(e)) }
        }
    }.start()
}

private const val INLINE_STICKER_TOKEN = "[[STIKER]]"
private const val INLINE_STICKER_ID = "texno_inline_sticker"

@Composable
private fun TextWithInlineSticker(value: String, stickerUrl: String, stickerMime: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) {
    val displayValue = value
    if (stickerUrl.isBlank() || !displayValue.contains(INLINE_STICKER_TOKEN)) {
        Text(displayValue.replace(INLINE_STICKER_TOKEN, ""), color = color, fontSize = fontSize, lineHeight = lineHeight)
        return
    }
    val annotated = buildAnnotatedString {
        val parts = displayValue.split(INLINE_STICKER_TOKEN)
        parts.forEachIndexed { index, part ->
            append(part)
            if (index < parts.lastIndex) appendInlineContent(INLINE_STICKER_ID, "[stiker]")
        }
    }
    val inline = mapOf(INLINE_STICKER_ID to InlineTextContent(Placeholder(1.6.em, 1.6.em, PlaceholderVerticalAlign.Center)) {
        StickerPreview(stickerUrl, stickerMime, Modifier.fillMaxSize())
    })
    Text(annotated, inlineContent = inline, color = color, fontSize = fontSize, lineHeight = lineHeight)
}

@Composable
private fun StickerPreview(url: String, mimeType: String, modifier: Modifier = Modifier) {
    if (url.isBlank()) return
    if (url.contains(".tgs", true) || mimeType == "application/gzip") {
        var composition by remember(url) { mutableStateOf<com.airbnb.lottie.LottieComposition?>(null) }
        var failed by remember(url) { mutableStateOf(false) }
        LaunchedEffect(url) {
            try {
                val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.net.URL(url).openStream().use { stream ->
                        java.util.zip.GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }
                composition = com.airbnb.lottie.LottieCompositionFactory.fromJsonStringSync(json, url).value
                if (composition == null) failed = true
            } catch (_: Exception) { failed = true }
        }
        val current = composition
        if (current != null) {
            com.airbnb.lottie.compose.LottieAnimation(
                composition = current,
                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                modifier = modifier
            )
        } else if (failed) {
            Text("TGS stikerini göstərmək mümkün olmadı", color = Color.LightGray, modifier = modifier)
        } else {
            Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    } else if (mimeType.startsWith("video/") || url.contains(".webm", true)) {
        val context = LocalContext.current
        val player = remember(url) {
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { viewContext ->
                androidx.media3.ui.PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            modifier = modifier
        )
    } else {
        val context = LocalContext.current
        val imageLoader = remember(context) {
            coil.ImageLoader.Builder(context)
                .components { add(coil.decode.GifDecoder.Factory()) }
                .build()
        }
        AsyncImage(
            model = url,
            imageLoader = imageLoader,
            contentDescription = "Hərəkətli stiker",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun GroupVoiceMessagePlayer(url: String, sentAt: Long) {
    val context = LocalContext.current
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (state == androidx.media3.common.Player.STATE_ENDED) {
                    isPlaying = false
                    positionMs = durationMs
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0L) durationMs = player.duration
            kotlinx.coroutines.delay(180)
        }
    }
    fun timeLabel(ms: Long): String {
        val seconds = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }
    val clock = remember(sentAt) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sentAt))
    }
    val bars = remember(url) { List(38) { i -> 9 + ((i * 17 + url.length * 7) % 25) } }
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFFFFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.IconButton(
            onClick = {
                if (player.isPlaying) player.pause()
                else {
                    if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                }
            },
            modifier = Modifier.size(42.dp)
        ) {
            Text(if (isPlaying) "Ⅱ" else "▶", color = Color(0xFF66747B), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                bars.forEachIndexed { index, height ->
                    val played = index.toFloat() / bars.size <= progress
                    Box(
                        Modifier.weight(1f).height(height.dp)
                            .background(if (played) Color(0xFF1687D9) else Color(0xFFBFC5C8), RoundedCornerShape(3.dp))
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(timeLabel(if (positionMs > 0L) positionMs else durationMs), color = Color(0xFF59636A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(clock, color = Color(0xFF59636A), fontSize = 11.sp)
            }
        }
        Box(
            Modifier.size(38.dp).background(Color(0xFFE8F3FA), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text("🎙", fontSize = 19.sp)
        }
    }
}

private fun uploadApk(context: Context, uri: Uri, callback: (Result<Pair<String, Long>>) -> Unit) {
    Thread {
        try {
            var fileName = "application.apk"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) fileName = cursor.getString(index) ?: fileName
            }
            if (!fileName.endsWith(".apk", ignoreCase = true)) throw Exception("Yalnız APK faylı seçin.")
            val boundary = "----TexnoTelekom" + UUID.randomUUID().toString().replace("-", "")
            val connection = (URL("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD/raw/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 180000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(65536)
            }
            connection.outputStream.use { output ->
                fun field(name: String, value: String) {
                    output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
                }
                field("upload_preset", CLOUDINARY_PRESET)
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n".toByteArray(Charsets.UTF_8))
                val input = context.contentResolver.openInputStream(uri) ?: throw Exception("APK faylı oxunmadı.")
                var bytes = 0L
                input.use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        bytes += count
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw Exception("Cloudinary xətası ($code): $response")
                val secureUrl = JSONObject(response).optString("secure_url")
                if (secureUrl.isBlank()) throw Exception("APK yükləmə linki alınmadı.")
                Handler(Looper.getMainLooper()).post { callback(Result.success(secureUrl to bytes)) }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { callback(Result.failure(e)) }
        }
    }.start()
}
private const val ADMIN_ICON_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABICAYAAABGOvOzAAABCGlDQ1BJQ0MgUHJvZmlsZQAAeJxjYGA8wQAELAYMDLl5JUVB7k4KEZFRCuwPGBiBEAwSk4sLGHADoKpv1yBqL+viUYcLcKakFicD6Q9ArFIEtBxopAiQLbIOYWuA2EkQtg2IXV5SUAJkB4DYRSFBzkB2CpCtkY7ETkJiJxcUgdT3ANk2uTmlyQh3M/Ck5oUGA2kOIJZhKGYIYnBncAL5H6IkfxEDg8VXBgbmCQixpJkMDNtbGRgkbiHEVBYwMPC3MDBsO48QQ4RJQWJRIliIBYiZ0tIYGD4tZ2DgjWRgEL7AwMAVDQsIHG5TALvNnSEfCNMZchhSgSKeDHkMyQx6QJYRgwGDIYMZAKbWPz9HbOBQAAAfv0lEQVR42r2cW4yt2XHXf1VrfZd96e5zmzMzZ+yJJ844IlYkBIkQPJm7gEQIBSJkCTlISCQSD5A3LhITPxCBBEIQIeUJCV5wjHiIkHhBaCQesBCQixGxPYk9tufic+/b3vu7rFXFw1p7d5/xzHicBM5M63SfPmfvr2rVqvrXv/7Vwu/TL3cXQEUk168XkP408ONm4x9T8onhK/BjRW6i7oCAOPgZkMxEUAEkKfp1s+Y3VPU/Q/wSMInIZX3tCBjgIuK/l+eW/weG30gp/WGN879U+APgwAgM4DPkCfIMYlePEBRE6uMISABZAH35+ybvElYTNP8ebf+xiDy89v4KIHJ4wf9/DnD3cM3wmzmPPxcCfxvm52Akn3/H03huedqIzTtyHsTziFkSyIAjKjhX/wuKSoOEziU0HptFWBzdgu4Yuo9Dnt8hLH8F5N9B8xsiMuyfBcGE7y8i5Pdw6iIi5u4tTL8A9jmYX2R6wHjxwG049bR9osP2lHl3Tpq35DSS5omcE5SwwRUcB3PEDQGCRkJs0NgQmp52ceTt4ibN8o4tT+4FTl6GySEsv0FY/TqkvyfSf0UEzFy/n2iQ343x+3vnPvwlsF+E6YcZ7jM8eSfvLu7ruHkoaXwC6RJPxXApVxY3ARwVRURBHHfDPeOewQxFinNwTEBCi4YlxCNid9NXRy/mxcm9GG+9BP1LgD6E9hfhN39J5Mfm65H5++qAvfHuD4/g6J+A/CzpHaaHv5N35+/o9vyhjNsn5Okc8S1dhC4KIURi06IhItogGhDVcgXEiqGecTM8FydgGUsTc5qY00xKxjg7GhY0ixt0y+do1y94f/wx626/Eug/Cdh/hfQzIouv/75HgHsJLXe/i22/hOZX0tOv2Xj2TXZPv66787eZx0uCOn2ntFFom5a2XaBNB9ogWpyABlxKARCtkeFeHGCGpwyWIM9YGknzlnncMk8DKc3MGZI3aHub5ckrrG98ytubr+Rw69WI6bfmnD/bNItfA4bvdR3iR7/z/zO4e0PafJ7or8wPf3PePvlGc/H0d0iXb6N2ybIJLJYrFsslMbZIbCG2SOhwCQixZHsJiARK1vMSCRji5XNvHCwhnsASzbygXyzI04ZxPGfcbRnGHfMwcTmPpGGSEyT2ISQ9+dTLjaZ/A/xQidarK/u7dgAQRH5szuP5v9B2+TfTwy/Nm4dfa84ff5Vp+za9jqyWDYvVkrZfo80CQgcSIQRcIlJLvuN7m0sAugKCk0EMwYsjJILUfysB0YiGgAZogtNFZzvO7MbHDOcQQ0SbLrT9Sdbu1hrGn3L3X4UvZkQy7r87B9TQT+7DT2Lpb9jZl9Pu8e/EzaPfIm+/xSLsWC07lsuOtu+giaCKiyAIbpRTtoyLlwgwKfa7IgRAELGCDbyUw3I5rd5RhdCAdKguaHUmkBBGxCfG8SnD+ZtouxSJfWhvpbvS/8AXzS5/OYSf/tkPS4rxI5Q73P2YdPqvyY8Xw6Ov2fbJ18R232apl/Q99H2kaSKogFsNXykn6ntjKUbWkxcogMdLVXDskJEExREKVuBwRdxz+ZexIXQNrSfMEuo7xuk7DI+VoAHBrL0jRrz9V92Hfy4iX9nnsO83AlREcs5nf1ej3p7e/WraPv1GnDZvofaEtpnoQ0fUDGTc5wrwEjDjhPLA7sW4gyf8CvUdftnh245e+5Ygbrg64uV98IwIaMjEmCDNaJjYzcLmUcA1qDSNNc/dOYH58+7+2f27f2QH7MPGffqj2Pzz6eKNfH767XDx9E1CekwfJrroRHWUCXxXypekErKuCAq+P+29df6e+qNXSbBWA9hD4/L9GobguX6MYBPBE02YCc2ACuTJ2QzK+ZMWlut4vDy2sHr1r8D2n4msvvR+V+F7J0Gb/g46t/Ppt9Nw8Zak+SGLOND3mUZAfYY8IOIF60sDpiW8kVLuahoo/cuHvtnVQYkeosT3UQCIpeqAHdhI8AnRGYkZXEh2xuXubTand1gcv+Bh9XE3i/8A+In3i4L4IYAnu3vD9PDHPd1nOH9HbbhPFy7p40grE0EAy0j2msUjIrGEMAEhIBXRufsBDV4PAa93Q4SaD/Z3RWp0VMcIiFuNgAnPE2IjUiNBMQgDvTpjUvLuAdP2kfbzI9Hw3B9x91dF5I335oIPioBQLvL0l2n6T4z338q783eCT09YhpE2TAgT4lKMyiU8XcJV+UKrI2qSK8dXTLpWkkykJsjrDgDkKnkilAizhNQcgCXcMrgj5og6QWbaAF0emedzbHsK4xYWBqRPAm+8F/zFD45Fgbz7a+iWPD4m7R5BuqSJiUaqaeJAxqwYWMqcI5oRlHwwyA49mgm4Wz1dw4VDshS/9mwiV+mivi42YZ7Yfyni5XqIgICq0Ealy0LOE3naSRp3Ka7XtzLzzfdDv/GDIO8wDJ8yNn9GL952Gy+C5B1BZmI1XrkK1/IAArXBEQl4SYEYyr78H4Jf5CrZeTFW9obsT7wce3kHsWKxKligxIYDe4BlpfyiKEoQEM/YPGLzVDJpPtyn75kDFLDY2R9S2jBvz3MazoP4QBucqIK74sQSiWZkFMNxKeUpxHA4QRdBpakRIzUnGIoiqriWvydeKoSTD05wd7I5tu8Wa6+AWT1KJwQhhlAwhjmVHwEzLCfcZiATAuH7KoMBuQ2Opa3ntENtIkrt4S0y58SwHRimxDAlpmRkB9VAEzs0VoSngRgbmhgJIaKqxUkhEmNAomLu2JSY5xGzjLthbqSUSWnGUibbTJrn2jEaIkIMynLVsl43tF0gCJhodX5xWvZD1Xv5+0WCudzdDKSawQ3LRpqMy/NLHj9+ym43MUwz05zI2dEQiW1THOBSH7QlxoBq/QhKjA0xRjQoBszjRJoGsqfiADNSzuSU8GyYJfKcMMtYMnAlNsrx0YIbN5fcvLlmfbSEoJgLcug0cy2vevz9OkDByTmTc8bMcDHGcebs6SX3v/OQR4/OyCmhqrgUCBtjJTgmwcwx8yu4u8dBomhQVGu/QLlK1NMXrXllXwwriLJ6BSw7eXaGnTFudmzPL5l3CbkXadehYEopXIMcOi+T79MBeQKY58Q0DnhKmGSG3ZaHD57w6OFThiHRdw2LRUPbNzRNS4gBCYoD2TM5Gzk7bpDNsJxLAtT6YC64QFCpSQ2CBoIKQQUNUhxVcYBlI2djnow0zgy7kYvzLbgQ25abIUDbFUdmQ0SlRED40Wto60MdUJK1+adQI6dMmmfImckyZ2cbnj49Z56M9bLl5GTB+mhBu2houwYNigTdk51YrWXuVEeUltivtTklUWpN04JqyeRKOcGKCPBs5FyuQk7GNGQ2F5HT0y3b3cjT0zOadU/HClMnm1XCMQA8+J5l8BoCPMHOP8d0jruFijvYDTMX51vGcabrIzdvLTm+0bNYdIRGiI0QmkCIxQl7w0SVCplq2eLgHK9/JpUWF9uXzHIlxEvEWDbISjbFZiUno22cEBQzOD3bsNls2VwOSMxoQ4HkLhXX+Zc+Cg6oOHTzSTTetXmHiIvjpNmYNyO7bSJqy/FRx42bPctVILaKRiUGJTR6uAYERUUQLRjBVQiiiAgmlQmXq2bJLZdy7lLLmOEOlqw4RQTxSBZAE47T0bBKLdtpYE6Z7eVE2xt9jARtEWkjbIH1f/8oV6BGpj4y0oWE5kgleAhRhuyM44y70fWR1aqn71uaBiQKTRMJMR4MRQMhaDE4CB6klkAHlZICkH2GqwkQ3CrOsFBeJ2VUS0ItidZrdVE0GG0b6Bcdi0VHupwYx8w0Gt2qIcQe1aa2483uAzL9d//64hcXb2M8la4nhNY1NJgLYyolpeuFrtdS10MH0mBEZlPGBLsRtoOz3cIwQcoBpUGlsMESBGkCNIpERUJAgqAhFuZYAk5HTg3bATYD7CZnnBLznCvYiYgoQZUYA03TEpuGlBO7aSZ5QEKHiDjSkEgvfqQq4O762mv4T/2U/hqyfFlC46KxgJWUEZyu1XLyKqQspDlhNpMccoZshd4QVdquYblsWR11LJYNoQ2l8dEKc/cATQoadBfGKbPdDGwuRnbbHTllPM0EMaIqMVRgJaWXFFGapqFtMrMJZoJRGjPVAOYJzYvv6YDKooZf+AVJr722+4/Q/UURdZFYypWV+tw1DU0MuBnDOLDZjQzDzDxnzBQ8kGr5D62wWEVu3Tni1p2brJqWyD45KWh9BEkgBfldXux48OAJ5083zLNBcjwXIBbVabSh6xoWfaDvGsQjTQg0IWLPvrYTWsdDishbvwsgRL23WnFRmdjEWO7yNA6cX1xwdjEyTY57IISOGGMdcEJKmc0201xOrE6chQecthI+e1IUXBSTTLLMbkjsNiPDLiE0uJWmya0gw12aCJcD/UI4OVqwWqwJQVEBrayTiBJjJ0jMhGWfGX4E+LVDG/rBDnh9fxmeByeESBMbVIrxTQwECag7wzwxTxnVyGK1oGnXxGaBhJagEW0CrhlkZrEKtN0a1SVo4Ionkz1dhKsgTUe/WHFynFl2DrTMM3hWck5YnsnzjjxeYrZjmmf6PiEhorHMXBVDVdAQXBeLaPn8UQgn/+kK4n84DkjuHo3N55QtIqIBEHNiVPrYEBtFY6T1ntVRy0IWeFgxW8M4C/MEooFOI6u+ZbkOHB21LNc9oYmlfVWtuNhLjyHgrsRWOToOBFuxuZi53GTGKZOyYJYIoWOxXhGP1ng+pw0zGhXLQmwCMRV63TxjtRFSETv9AD7ug67AbczuMG9Iw4UM23PyPBICtE3xdGga+naJdg271HO2Ub7zeOTB0y2b3cQ4J9roPP/ckk+8fIe2W7K0pg477EBiOLmCIIVcCTKLbLeZt9855937Gx6dJS6HQoasl4GX793g3p0Vx6uGvtkhYsyj0QQlR8gY07BhtzmT9uIp3a07d27AS+5+do1z+zAgND2npJO8OePy7JGcnz4gpy1NKNGrWkQNMawYveXiQnl8AQ/Plce7lovtzP373+Hs8QNuHUWePNngQGjucKtbE2Kp8SIgpkBGy7yElJzT0y1f/8Yp33zzjLNN5HRsefvBBU/PHnPjqCFZSxdb2luRZbcg6gyMZBVyDJhnhs0pZ0/uE28+yt1zPxKwy89KOPr7fiAMPtgBgL0Iwm7z1C7OH+lu8xSzgTYIIQqo4yIkDyTrydIzEclRWJwssN7Jj4x3Hj7m4YMti/6U5567zcmNE5bHsOxaVKqvJVSi0zEXdkPi4aOZt97Z8Z1HTlzeYnF0gj1W7j95zHbMfPxjHUNeM3liyjvU7ECNiYBaIk0bdhePGc4f6Xz5rjfLj/119+HfAl+9Toy+rwNyzi+HoIzbc5vGC3UbEZ9RKagMKbU2AdKsObp1j0dD4nzziNPtjs1knF0a46SE2CF6k2wrhjEyTErvEd1rAyxTymw5/cudsR1aZl9zOWTm3YTFHU+fjlxeeiFRmhOObn2c9Y0Jm99lygmtBysCoRG0AbWBNFzIdPowN+tXXoT06f2U6ENzgLj/EBQKWiXTNgoxVPhqOKE0NhpYLNasl3d5fLllnN7irW+9xenllouzc/rOuXf3Dh9/+RWef+ElVseLApe1KXcJp7ixvBYYrrA8abj9QsNbDzNvv/mQi807nF5csuiEmydH3Lp5k7vPv8StmyPbJ5f45gIXO4CvJkZCFyCC+gSWwUZH2+EjJUGNMYDTxIauafGuxaeITLKX8oAKsWtYrhd0xyueu9tw7+6aJw8Nm7csbmS6527wyssf59VPvcIPvPISN25AvxhKlkbLNEi00t+CRjg6WdF0C0K74+zSOL+4QGXH8bIj9mvu3XueT7z8Anfu3uJouSUNHdNcOcjJwQsqbLoW2kjbKCEAGuT97P2gKpAg0nQL2naFNT10S3K+xH0spIUoTRDa1oh95vnnez79w3eJcsaTJz05Zxb9insvfoxP/eBLvPjSXdp+hOC4bAuLK1o0QlbEEaqR9fGKo+MT+m7NOG4JuuX08ZqgSr9c8MKLL/Dqqy9wfLMl+iWxEXIEnwsTpRpo2p6266HpadsFTd/v6//uIzkg5/TlECLaLAlxUT7aBfPUktK2PLsb4gM2n0N+zPH6Bj/0yhHr/h5nZytySnT9ilu3X+D5F1Z0yxmJI/hc5DAExEPBA2KoZtQMZEKaiaObwg/+4AnLxce4PF0THLq+5/adO9x+rqfRc+btI4IXuj57RlFMA03TodpiGtHQ5XDjbiAP/4Fw8l/cf+WZ+eAHRIB8AhoX6ZGwAO0QKd0cLnhyPGcsD8zTY7gQ2vWW4+OWrukZtk7KiRB6lutItx6RvMVtAB0K56dNUYlIW/m7AcsDPl0gPhMIHK8TzUs9861jyJkYIovlTJRHzBcbxu0DbD5FbSJbPiB4c6F8GZC2d8IRWPjaHuR97ysQ9G0Ios1SYrMC6ZmSkJPjCciOexlSWj4jjTPoJaFZE2Nkfew1Iyc0XCB5qBqADCEh2iLagPZFO+QZprGKJGZIO8ydIJl1b1jMeMpIHvF0zjxl5mlLHs/wdInbyJyMlAVcGMZCpXWxI3ZrwdTR8JVnof77OyBXUcQXzLY/E49ufia0R0ZY6ZAiNivkQHRHXVBP4DvMEjaVEXmIHRqrQkRahBHPEdcyLXIDUiitrNYRWHZsHvE0IjKDTUUfVGl5rfIZYyLPMznNeJ4hX+Jph1mB0TkbZkqehWa5RNsT79e3Ayk9pj351WLiZz50PK5vvPFGfPXVVyeb7PPaH//xuLzhYfkcE2vGuaOhp82JlBw1J3gRLphNiDmeZ5wIIRbARNX7ELEM01iip+2UphVEZzxPTLtL8rwhhpkYy3zRfIKcwBw8kZnIXriHMkBx3CMpZeasJAvMWRHtCOEm3dGLJrdeCubxqwpn7r8SXn/9dXF3e18gVJPDXvr638jnX148/8qPzptT25w+1WG7ZRwDkrZYMswCjUXUAsEilgOZhkBD0AanAWlwImIN05w5vxjYbS5wRrp2R5CGlGfG+YIYZo7WLat1R5BympBwMuaKe2mZs7S12XFmz4x5YsiJMUdMjun6e/THn2B9+5OCrAcNJ3/rvfKYvXosighmZWjw+PT8c/Oc/sQ0Dbe//fbbsW38pJPMyA3x5Uv4asOQO0bOGTOMcyy8n0VCLGywhkgIDTIHJEZEI1CaoGnMnF5c8vjxGcN2RnSDSiSlGVHj+HjJc3rEFBdosJpncpkG5YzPGa+DGk+Gzc40GWOeGTwxh0hob9GtP4EvXmLHWi8entl23n3+W2/dP4lBx75rfl2VXxaR33Z3iV/4wheCiOQHDx7/fHL+6Zvf/BYX2y0K5DTCtEHTVvJlIg/HlWs/IYxOdNBRUC0ssIZQeT+ps4FQB/9FF2gm7LYdF2PPOM+Vm1DMjBCdNHbM5wvasQ47SWUSZEbRRuwHpLlghyzkKZNmZ05OtoCkFeeXPQ/f2SJP3yTJctktj39iTolGhRdfuPunjtbLz56fn/9JEfnKXgXWv/mtt//Pb/3W137gf/yvX0+n5xeBQn5oF0UayTQ+08lMlIyQEM2FzVJAy0hctQxElEqF19FXGXMrqgF3KQMSvxJE7MdXulfWeC5TBK3T55zrYKROkN3KdCkXEGUZzALJKB/SkumYaRiTMM85m2XaILxw9878F/78n+uPj4/+4d0X7nx+nwNujMN0+zv3H+j9+4+ax09OxaWID0MUGoVWoFEhhirv14IuRQtFjSqqZQ5QRln736uSYP91HZLstT/7MfeelXfP5Fzmh4JgruQcDgPTIg3YR0XGs2AO5kY2mLIz5y3JdmSHORtmFjxluhgRx+8/eOiL5fLeIQnudjRmFsdxZp5m5jlh7uSQkRmmoEwSiopbhRCVqBHVovdXLRpBESGoEqQaKlI+r8OQ0knWCWn98z034V50QoKQLVQBRTHOHczKoNbND9fGTMqssA5hzSGZl6myGdmdbBnPZaRO60zJmM3ExRcHBywWiDtqeT+lLYoLF7sSHIQ6jECICO6CZjlI24teSIpoQgSxEh2mWmhPcdR1rxiqpKhVw+WZjtwlYG6QIVedYc7gHg6iavP6mAjZc42CKpDwOlHaG2+GWdEKGEhySMiN62VQQcXEy8nXMbRIucdmViTuIqBVbeJFBhXrQ+GVRq7KDhUv/Z6V+Z8ih1NG5BAIVSFTxmXmVGqjgiavUvpyMO7l+cz216F+7VVran4tv1wnvoRcc89egFETUHHAAEjEYxtxFYwSOpILJe4GYoaqkvcDVymjTrMifRMvydBQwkHwuH+bEjnlNfaGlcFKeVA/KEVFBNvf8b163Dk803UHuFcZTo2K4qlnvz783drCSwg0MRKDjgcH9HARm2a3Wi/7JjaHIWHhP7xMemuIiRfNjlURnB30OlpP2Itk2MudFakSiCp1Kw9d5nzXedqiIwwHAdXBMOzgzGeMN8OecUCVKVN0CCnnQ+LMlktcBaXrF75cLgjw8PoVGEKQ8/VicTM2wd1N3Kzq/kqpQQ3JgnhCXXDdC6OknO/eeIWs5X5qHXcVgbjV8K/j7lrerpQghbgobLnXPFmuQN6LKmqkiZfVG6nR4/WkU/08eSbXiDIvMkYwRIVF39K2EdS31x0wR9ddjEWhsfeyyV7DW1glwas8LpYTrnM5rxMjDfuI2GsA5TB9FTHcjWypGlhOVVVK8jJHJRNCQZcgZYdo/++yXZPZeNUmSk125ZQNJ7sxV00BZmS8Jsdckq86QRWV+IwDcMsqluv42cnZCOoE14K+9rJmL6WJvapDrkSS7oWTy14SqNePfd0uWqNMCIG2aen7lhAC0zgzDAPTuAMKpRVizfhUIvaaINOshryX7jK71Y/M7Jlc74Pnmif2qzhVb6AoImG47gAT1Uk1IFoSjpXeuFSDUnlLXhApMj0pwiiXUIzXshuQ9zlhn9kBN2OeR9ycpm1YLHrWixXL1Yo2RnLOXG4ueXp6yuXllpwTTdsecEM59VyNdsyEbCVZu1+VOaMY7+7I3miXqjKrW2m4FLUa5wCx9v9ZmnDar5eEtnGXPbJyXB3NcjXGSlLV8FX0KGDiBOWw0LCXuUgVS6aUmOdE0MAytqVZUiXGSNcviBrouh4XKSqP7YBPQtM2iJahqNkeMJUudF+N/Bo0tjrCw+ygrBf3SuA4KoGuW4iGQBPa030EiIjYOw+fvLU6WtO0LS6Q9rS4SVGF+z5rewUaUsJfS80ny8EpJRkWdibnIrKylGn6Bnfn7OyMzcUlIkrXdFDn+0frI7a7gWEYmae5MtRhjxWrsVJz1F5nZBi5AKdqvOUEWSDbQYJXwJf6arnSlLOrlnH5IQeEEE6bEAmy195m3AskRsoKS5AiYREtSG+v/jbxWs71MJ0pG49SVGbTTFCha4sDNheX5JxZLhccrVal8REhxsii64lNZBg2+GQ0tKU01ASHa9UfWnGwZ5xU80KVy+Z6VWpuMKrEFtBQBjJd118AxNdff10B82xPA1Ik6DmXxcW6zFR4RL1Sdpgcdp3dyyaXWKnfWnuAfZfnVWYnTVM2u2o/MY4zm82G7W6Le3+o81KXK1IuRoVYBijm17K5XYX+fuHSamXxvO8LSgXIfr3RMkIQBC6ahifPREAUfSPEiKgc1KFScX441OaSgPZhjnJYitp3gFdq8FL3U06klBARcja6vmOxWLDb7Tg7OwN3+q5DVavGx5jmiZQSQSmt8P69/epk98/j11Cj1cxfQn7vlPosbjjmfdtJUHm81w3Gz3zmMwUlts0bRf7Sq6AHIOO1J98fuSpVgro/5Vy33eSA4kQqJyByDZU547BjtViwWq0ZhoHNZsO7775LNits8npNjA2b7ZaUEjSBVJszESkZHq8dIQdAdf33Ujodk9opZj/sI6qor9crmhjfFZGNu0vcI96bR4tvveu+Wy1WC/HgZIQKcrJ4WYYwxUPR4KpqBTJXxl/tAVAxQm2G3BmGIqdZLBasl2tOTk4QEc7Pzxl3O+Z5riBKmHNhi2LLwSAO3d414/frts84oMB0r2KpspRtRXmq4qvVirZpvgnw+uuvh3itZ9qFoLsY46K8WU10Wt+0TnPLyV6tuZWdjVB7/qvW1qXA5EJwZMZxxN1pmqasOoTA+mhdQj9GxmliSjPTNCMiNF13ZSjvMfywa/zdDtjTZwaH0rnfvVGEJjZICKfvR4srV93qAXLuh6GOlcW2XK5BHaRjXn7qhWDXEuCe53FyTszzzDiOzHMpbcMw0HUdbdOU7CzCNM/sdjvcnbbriLUpS5YPxu8dkN0Ojdm+D7iKiEqR1ciRasz+v6JD3o/GPkN87bXXpM7LHXUPsUhdtSo4y+kX8UL56Q51R8qKiFkOO056uP/7/OCUpYdpntmNIyLCnDOXmw0xBErvIWTLzHM5+bbrCgqsivM9Kbpvrb/rvh+Ss1Wl6VUn69eIAVWIUdEguGh2d3kdiJ9+7dN7gfS6730ZonnXRULgIDLaUxpugtcwd7ysqtUdvz3tXqgvPeQDqwmsadpKp0XEr/YQvAqmNQS6vqfrOkIIlSypzE7OB4H1swbXNvmZCOCwsLkfx4l4wRh9S9sGQvDdfrM8/rT8dHYffnKYT//Rdjzvx/nSY+sioULIg6bIn+nG9luepZt71vj9iVFzR9sviG2HqhD1ACCe0aWUDfFCqVvd+XlmqdavuIf3OuGQH8zIdVlrv1i1b8dDA20XJOUt5sMfdPcbInIac774V5B+7nTzDr/95v/2R6dvy246B8kl8bm//89ZcK0Zt5yU1Mxv2Hs4PgghEEKlzQ9c8HuFOXItbK/uu1cQxLXv7Y2/7ozDOqLId7+4lL3mMV2Gb7/7de597NafvW0nX3bf/tL/BTuzmYfHGfDlAAAAAElFTkSuQmCC"
private const val ADMIN_UID = "FflCErahavNIb8ZXsvHIyrxZYZH3"
private val ADMIN_DEVICE_IDS = setOf("a20e9a204172f84f", "2ad6b306ca18b8e0")

private data class ReleaseUpdate(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String)

data class AppItem(
    val id: String, val name: String, val category: String, val version: String,
    val description: String, val downloadUrl: String, val iconUrl: String,
    val stickerUrl: String, val stickerMime: String,
    val isActive: Boolean, val fileSize: String
)
data class ChannelPost(
    val id: String, val title: String, val version: String, val isVip: Boolean,
    val description: String, val features: String, val downloadUrl: String,
    val fileSize: String, val androidRequirement: String, val channelUrl: String
)
data class PhoneInfo(val id: String, val title: String, val body: String, val imageUrl: String, val stickerUrl: String, val stickerMime: String, val createdAt: Long = 0L)
data class ChannelMessage(val id: String, val title: String, val body: String, val imageUrl: String, val createdAt: Long)
data class GroupChatMessage(val id: String, val uid: String, val nickname: String, val text: String, val imageUrl: String, val audioUrl: String, val videoUrl: String = "", val createdAt: Long, val replyToId: String = "", val replyToNickname: String = "", val replyToText: String = "")


@Composable
private fun ProgramIcon(value: String, description: String, size: Int) {
    if (value.startsWith("b64:")) {
        val bitmap = remember(value) {
            try {
                val bytes = Base64.decode(value.removePrefix("b64:"), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(bitmap, contentDescription = description, modifier = Modifier.size(size.dp), contentScale = ContentScale.Crop)
        } else {
            Text("📱", modifier = Modifier.size(size.dp))
        }
    } else {
        AsyncImage(value, description, Modifier.size(size.dp), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun SupportBadgeLogo(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        )
    ) {
        Image(
            painter = painterResource(R.drawable.texno_support_badge_clean),
            contentDescription = "TEXNO DƏSTƏK QRUPU",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

class MainActivity : ComponentActivity() {
    private val notificationTarget = mutableStateOf<Pair<String, String>?>(null)
    private var updateDownloadId: Long = -1L
    private var pendingUpdateUrl: String? = null
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != updateDownloadId) return
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = manager.getUriForDownloadedFile(id) ?: return
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { startActivity(install) } catch (_: Exception) { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget.value = readNotificationTarget(intent)
        // Subscribe every installation to public program/game update pushes.
        FirebaseMessaging.getInstance().subscribeToTopic("texno_updates")
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 701)
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(updateReceiver, filter)
        }
        setContent {
            TexnoTelekomApp(
                onInstallUpdate = { url -> installUpdate(url) },
                notificationTarget = notificationTarget.value,
                onNotificationTargetHandled = { notificationTarget.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget.value = readNotificationTarget(intent)
    }

    private fun readNotificationTarget(intent: Intent?): Pair<String, String>? {
        val type = intent?.getStringExtra("notification_content_type") ?: intent?.getStringExtra("type") ?: "program"
        if (type == "phone_info") return "" to type
        val id = intent?.getStringExtra("notification_program_id")?.takeIf { it.isNotBlank() } ?: return null
        return id to type
    }

    private fun installUpdate(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingUpdateUrl = url
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("TEXNO-TELEKOM yenilənir")
                .setDescription("Yeni versiya yüklənir")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "texno-telekom-update.apk")
            updateDownloadId = (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            val url = pendingUpdateUrl
            if (url != null) {
                pendingUpdateUrl = null
                installUpdate(url)
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChatScreen(backgroundColor: Color, onClose: () -> Unit, onOpenBubbleAdmin: () -> Unit, onNicknameChanged: (String) -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val prefs = remember { context.getSharedPreferences("texno_group_chat", Context.MODE_PRIVATE) }
    var nickname by remember { mutableStateOf(prefs.getString("nickname", "") ?: "") }
    var nameConfirmed by remember { mutableStateOf(nickname.trim().length >= 2) }
    var draft by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf<GroupChatMessage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordingPaused by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var recordingPausedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording, recordingPaused) {
        while (recording && !recordingPaused) {
            recordingSeconds = ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt()
            delay(250)
        }
    }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    val liveWaveform = remember { mutableStateListOf<Int>() }
    LaunchedEffect(recording, recordingPaused) {
        while (recording && !recordingPaused) {
            val amplitude = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
            val level = kotlin.math.sqrt((amplitude.coerceIn(0, 32767) / 32767f).toDouble()).toFloat()
            liveWaveform.add((4 + level * 27).toInt())
            if (liveWaveform.size > 34) liveWaveform.removeAt(0)
            delay(90)
        }
    }
    var status by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<GroupChatMessage>() }
    val messageListState = rememberLazyListState()
    var messagesLoaded by remember { mutableStateOf(false) }
    var initialMessagesPositioned by remember { mutableStateOf(false) }
    var groupMembers by remember { mutableStateOf<List<Triple<String, String, Boolean>>>(emptyList()) }
    var showMembersDialog by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    val isOwner = auth.currentUser?.uid == ADMIN_UID
    var isGroupAdmin by remember { mutableStateOf(isOwner) }
    var groupManagerUids by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        if (uid == ADMIN_UID) {
            isGroupAdmin = true
        } else {
            db.collection("group_managers").document(uid).get()
                .addOnSuccessListener { isGroupAdmin = it.exists() }
                .addOnFailureListener { isGroupAdmin = false }
        }
    }
    var showManagementPanel by remember { mutableStateOf(false) }
    var bubbleStyle by remember { mutableStateOf("default") }
    LaunchedEffect(Unit) {
        db.collection("group_settings").document("bubble_style")
            .addSnapshotListener { doc, error ->
                if (error == null && doc != null && doc.exists()) {
                    bubbleStyle = doc.getString("style") ?: "default"
                }
            }
    }

    var groupSubtitle by remember { mutableStateOf("Ümumi söhbət") }
    var groupSubtitleDraft by remember { mutableStateOf("") }
    var editingGroupSubtitle by remember { mutableStateOf(false) }
    var groupSubtitleSaving by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var managementBusy by remember { mutableStateOf(false) }
    var antiSpamEnabled by remember { mutableStateOf(false) }
    var allowMedia by remember { mutableStateOf(true) }
    var allowAddUsers by remember { mutableStateOf(true) }
    var allowPinMessages by remember { mutableStateOf(false) }
    var allowEditOwnTags by remember { mutableStateOf(false) }
    var allowChangeGroupInfo by remember { mutableStateOf(false) }
    var starsEnabled by remember { mutableStateOf(false) }
    var slowModeSeconds by remember { mutableIntStateOf(0) }
    var managerUidDraft by remember { mutableStateOf("") }
    var showAddManagerDialog by remember { mutableStateOf(false) }
    var exceptionUidDraft by remember { mutableStateOf("") }
    var showExceptionDialog by remember { mutableStateOf(false) }
    var removedUsersCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        db.collection("group_settings").document("group_subtitle").get()
            .addOnSuccessListener { doc ->
                groupSubtitle = doc.getString("text") ?: "Ümumi söhbət"
            }
    }
    fun saveGroupSubtitle(value: String) {
        if (!isGroupAdmin || groupSubtitleSaving) return
        groupSubtitleSaving = true
        val cleanValue = value.trim()
        db.collection("group_settings").document("group_subtitle")
            .set(mapOf("text" to cleanValue, "updated_at" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                groupSubtitle = cleanValue
                groupSubtitleDraft = cleanValue
                editingGroupSubtitle = false
                groupSubtitleSaving = false
                status = if (cleanValue.isBlank()) "Qrupun alt yazısı silindi." else "Qrupun alt yazısı yeniləndi."
            }
            .addOnFailureListener {
                groupSubtitleSaving = false
                status = "Alt yazı yadda saxlanmadı: ${it.localizedMessage}"
            }
    }
    LaunchedEffect(isGroupAdmin) {
        if (!isGroupAdmin) return@LaunchedEffect
        db.collection("group_settings").document("admin_controls").get()
            .addOnSuccessListener { doc ->
                antiSpamEnabled = doc.getBoolean("anti_spam") ?: false
                allowMedia = doc.getBoolean("allow_media") ?: true
                allowAddUsers = doc.getBoolean("allow_add_users") ?: true
                allowPinMessages = doc.getBoolean("allow_pin_messages") ?: false
                allowEditOwnTags = doc.getBoolean("allow_edit_own_tags") ?: false
                allowChangeGroupInfo = doc.getBoolean("allow_change_group_info") ?: false
                starsEnabled = doc.getBoolean("stars_enabled") ?: false
                slowModeSeconds = (doc.getLong("slow_mode_seconds") ?: 0L).toInt()
            }
        db.collection("group_removed_users").get()
            .addOnSuccessListener { removedUsersCount = it.size() }
        db.collection("group_managers").get()
            .addOnSuccessListener { snap -> groupManagerUids = snap.documents.map { it.id }.filter { it != ADMIN_UID } }
    }
    fun saveAdminControl(field: String, value: Any) {
        if (!isGroupAdmin) return
        db.collection("group_settings").document("admin_controls")
            .set(mapOf(field to value, "updated_at" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { status = "Parametr yadda saxlanmadı: ${it.localizedMessage}" }
    }
    fun addGroupManager(uid: String) {
        val cleanUid = uid.trim()
        if (!isGroupAdmin || cleanUid.isBlank() || cleanUid == ADMIN_UID) return
        db.collection("group_managers").document(cleanUid)
            .set(mapOf("uid" to cleanUid, "added_at" to FieldValue.serverTimestamp(),
                "permissions" to mapOf("media" to allowMedia, "add_users" to allowAddUsers,
                    "pin_messages" to allowPinMessages, "edit_own_tags" to allowEditOwnTags,
                    "change_group_info" to allowChangeGroupInfo)))
            .addOnSuccessListener {
                showAddManagerDialog = false
                managerUidDraft = ""
                db.collection("group_managers").get().addOnSuccessListener { snap ->
                    groupManagerUids = snap.documents.map { it.id }.filter { it != ADMIN_UID }
                }
                status = "İstifadəçiyə adminlik verildi."
            }
            .addOnFailureListener { status = "Rəhbər əlavə edilmədi: ${it.localizedMessage}" }
    }
    fun addGroupException(uid: String) {
        val cleanUid = uid.trim()
        if (!isGroupAdmin || cleanUid.isBlank()) return
        db.collection("group_exceptions").document(cleanUid)
            .set(mapOf("uid" to cleanUid, "created_at" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { showExceptionDialog = false; exceptionUidDraft = ""; status = "İstisna əlavə edildi." }
            .addOnFailureListener { status = "İstisna əlavə edilmədi: ${it.localizedMessage}" }
    }
    var showGroupRules by remember { mutableStateOf(!prefs.getBoolean("group_rules_seen_v2", false)) }
    var groupRules by remember { mutableStateOf("1. Bir-birinizə hörmətlə yanaşın.\\n2. Təhqir və söyüş yazmayın.\\n3. Reklam və spam paylaşmayın.\\n4. Şəxsi məlumatları icazəsiz paylaşmayın.\\n5. Qrupda mövzuya uyğun mesajlar yazın.") }
    var rulesDraft by remember { mutableStateOf("") }
    var showRulesEditor by remember { mutableStateOf(false) }
    var rulesSaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        db.collection("group_settings").document("rules").get()
            .addOnSuccessListener { doc ->
                val saved = doc.getString("text")
                if (!saved.isNullOrBlank()) groupRules = saved
            }
    }
    fun saveGroupRules() {
        if (!isGroupAdmin || rulesSaving) return
        rulesSaving = true
        db.collection("group_settings").document("rules")
            .set(mapOf("text" to rulesDraft.trim(), "updated_at" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                groupRules = rulesDraft.trim()
                showRulesEditor = false
                rulesSaving = false
                status = "Qaydalar yadda saxlanıldı."
            }
            .addOnFailureListener {
                rulesSaving = false
                status = "Qaydalar yadda saxlanılmadı: ${it.localizedMessage}"
            }
    }
    fun deleteAllGroupMessages() {
        if (!isGroupAdmin || managementBusy) return
        managementBusy = true
        db.collection("group_messages").get()
            .addOnSuccessListener { snapshot ->
                val chunks = snapshot.documents.chunked(450)
                fun deleteChunk(index: Int) {
                    if (index >= chunks.size) {
                        managementBusy = false
                        showDeleteAllConfirm = false
                        showManagementPanel = false
                        status = "Bütün qrup mesajları silindi."
                        return
                    }
                    val batch = db.batch()
                    chunks[index].forEach { batch.delete(it.reference) }
                    batch.commit()
                        .addOnSuccessListener { deleteChunk(index + 1) }
                        .addOnFailureListener {
                            managementBusy = false
                            status = "Mesajlar silinmədi: ${it.localizedMessage}"
                        }
                }
                deleteChunk(0)
            }
            .addOnFailureListener {
                managementBusy = false
                status = "Mesajlar yüklənmədi: ${it.localizedMessage}"
            }
    }

    fun uploadAndSendGroupImage(uri: Uri) {
        busy = true
        uploadPhoneImage(context, uri) { result ->
            result.onSuccess { url ->
                val user = auth.currentUser
                if (user == null) { busy = false; status = "Qrupa qoşulmaq mümkün olmadı."; return@onSuccess }
                val data = hashMapOf<String, Any>("uid" to user.uid, "nickname" to nickname.trim().take(24), "text" to "", "image_url" to url, "video_url" to "", "audio_url" to "", "created_at" to FieldValue.serverTimestamp())
                reply?.let { data["reply_to_id"] = it.id; data["reply_to_nickname"] = it.nickname; data["reply_to_text"] = it.text.ifBlank { "📷 Şəkil" }.take(180) }
                db.collection("group_messages").add(data)
                    .addOnSuccessListener { busy = false; reply = null; status = "" }
                    .addOnFailureListener { busy = false; status = "Şəkil göndərilmədi: ${it.localizedMessage}" }
            }.onFailure { busy = false; status = "Şəkil yüklənmədi: ${it.localizedMessage}" }
        }
    }
    fun uploadAndSendGroupVideo(uri: Uri) {
        busy = true
        uploadGroupVideo(context, uri) { result ->
            result.onSuccess { url ->
                val user = auth.currentUser
                if (user == null) { busy = false; status = "Qrupa qoşulmaq mümkün olmadı."; return@onSuccess }
                val data = hashMapOf<String, Any>("uid" to user.uid, "nickname" to nickname.trim().take(24), "text" to "", "image_url" to "", "video_url" to url, "audio_url" to "", "created_at" to FieldValue.serverTimestamp())
                reply?.let { data["reply_to_id"] = it.id; data["reply_to_nickname"] = it.nickname; data["reply_to_text"] = it.text.ifBlank { "🎬 Video" }.take(180) }
                db.collection("group_messages").add(data)
                    .addOnSuccessListener { busy = false; reply = null; status = "" }
                    .addOnFailureListener { busy = false; status = "Video göndərilmədi: ${it.localizedMessage}" }
            }.onFailure { busy = false; status = "Video yüklənmədi: ${it.localizedMessage}" }
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("video/")) uploadAndSendGroupVideo(uri) else uploadAndSendGroupImage(uri)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "texno_group_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TexnoTelekom")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Şəkil yadda saxlanılmadı.")
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw Exception("Şəkil hazırlanmadı.")
                } ?: throw Exception("Şəkil yadda saxlanılmadı.")
                uploadAndSendGroupImage(uri)
            } catch (e: Exception) { status = "Kamera xətası: ${e.localizedMessage}" }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val file = File(context.cacheDir, "group_voice_${System.currentTimeMillis()}.m4a")
                val rec = createGroupVoiceRecorder(context)
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(96000)
                rec.setAudioSamplingRate(44100)
                rec.setOutputFile(file.absolutePath)
                rec.prepare(); rec.start()
                voiceFile = file; recorder = rec; liveWaveform.clear(); recordingSeconds = 0; recordingPausedMs = 0L; recordingStartMs = SystemClock.elapsedRealtime(); recordingPaused = false; recording = true; status = ""
            } catch (e: Exception) { status = "Səs yazısı başlatılmadı: ${e.localizedMessage}" }
        } else status = "Səs yazmaq üçün mikrofon icazəsi lazımdır."
    }
    LaunchedEffect(messagesLoaded, messages.lastOrNull()?.id, nameConfirmed, showGroupRules) {
        if (messagesLoaded && nameConfirmed && !showGroupRules && messages.isNotEmpty()) {
            if (!initialMessagesPositioned) {
                messageListState.scrollToItem(0)
                initialMessagesPositioned = true
            } else {
                messageListState.animateScrollToItem(0)
            }
        }
    }

    DisposableEffect(nameConfirmed, nickname, auth.currentUser?.uid) {
        val currentUser = auth.currentUser
        if (!nameConfirmed || currentUser == null || nickname.trim().length < 2) {
            onDispose { }
        } else {
            val presenceRef = db.collection("group_presence").document(currentUser.uid)
            presenceRef.set(
                mapOf(
                    "uid" to currentUser.uid,
                    "nickname" to nickname.trim().take(24),
                    "online" to true,
                    "last_seen" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            val presenceListener = db.collection("group_presence")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        groupMembers = snapshot.documents.mapNotNull { doc ->
                            val userId = doc.getString("uid") ?: doc.id
                            val name = doc.getString("nickname")?.trim().orEmpty()
                            if (name.isBlank()) null else Triple(userId, name, doc.getBoolean("online") ?: false)
                        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
                    }
                }
            onDispose {
                presenceListener.remove()
                presenceRef.set(
                    mapOf("online" to false, "last_seen" to FieldValue.serverTimestamp()),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        val registration = db.collection("group_messages")
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limit(200)
            .addSnapshotListener { snap, error ->
                if (error == null && snap != null) {
                    val loadedMessages = snap.documents.map { d ->
                        GroupChatMessage(
                            d.id, d.getString("uid") ?: "", d.getString("nickname") ?: "İstifadəçi",
                            d.getString("text") ?: "", d.getString("image_url") ?: "", d.getString("audio_url") ?: "", d.getString("video_url") ?: "",
                            d.getTimestamp("created_at")?.toDate()?.time ?: 0L,
                            d.getString("reply_to_id") ?: "", d.getString("reply_to_nickname") ?: "", d.getString("reply_to_text") ?: ""
                        )
                    }
                    messages.clear()
                    messages.addAll(loadedMessages)
                    messagesLoaded = true
                } else if (error != null) status = "Mesajlar yüklənmədi: ${error.localizedMessage}"
            }
        onDispose {
            registration.remove()
            try { recorder?.stop(); recorder?.release() } catch (_: Exception) { }
        }
    }
    fun sendText() {
        val value = draft.trim()
        val user = auth.currentUser
        if (value.isBlank() || busy) return
        if (user == null) { status = "Qrupa qoşulmaq mümkün olmadı."; return }
        busy = true
        val data = hashMapOf<String, Any>("uid" to user.uid, "nickname" to nickname.trim().take(24), "text" to value, "image_url" to "", "audio_url" to "", "created_at" to FieldValue.serverTimestamp())
        reply?.let { data["reply_to_id"] = it.id; data["reply_to_nickname"] = it.nickname; data["reply_to_text"] = it.text.ifBlank { if (it.imageUrl.isNotBlank()) "📷 Şəkil" else "🎙 Səs mesajı" }.take(180) }
        db.collection("group_messages").add(data)
            .addOnSuccessListener { draft = ""; reply = null; busy = false; status = "" }
            .addOnFailureListener { busy = false; status = "Mesaj göndərilmədi: ${it.localizedMessage}" }
    }
    fun cancelVoice() {
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        voiceFile?.delete()
        voiceFile = null
        recording = false
        recordingPaused = false
        recordingSeconds = 0
        status = ""
    }
    fun toggleVoicePause() {
        val rec = recorder ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (recordingPaused) {
                    rec.resume()
                    recordingStartMs = SystemClock.elapsedRealtime() - recordingSeconds * 1000L
                    recordingPaused = false
                } else {
                    rec.pause()
                    recordingPaused = true
                }
            } catch (e: Exception) { status = "Səs yazısı dayandırıla bilmədi: ${e.localizedMessage}" }
        }
    }
    fun finishVoice() {
        val rec = recorder ?: return
        val file = voiceFile
        try { rec.stop() } catch (_: Exception) { }
        try { rec.release() } catch (_: Exception) { }
        recorder = null; recording = false; recordingPaused = false
        if (file == null || !file.exists() || file.length() < 500) { status = "Səs yazısı boşdur."; return }
        busy = true
        uploadGroupAudio(file) { result ->
            result.onSuccess { url ->
                val user = auth.currentUser
                if (user == null) { busy = false; status = "Qrupa qoşulmaq mümkün olmadı."; return@onSuccess }
                val data = hashMapOf<String, Any>("uid" to user.uid, "nickname" to nickname.trim().take(24), "text" to "", "image_url" to "", "audio_url" to url, "created_at" to FieldValue.serverTimestamp())
                reply?.let { data["reply_to_id"] = it.id; data["reply_to_nickname"] = it.nickname; data["reply_to_text"] = it.text.ifBlank { "🎙 Səs mesajı" }.take(180) }
                db.collection("group_messages").add(data)
                    .addOnSuccessListener { busy = false; reply = null; file.delete(); status = "" }
                    .addOnFailureListener { busy = false; status = "Səs göndərilmədi: ${it.localizedMessage}" }
            }.onFailure { busy = false; status = "Səs yüklənmədi: ${it.localizedMessage}" }
        }
    }

    val homeBackgroundBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to Color(0xFF030712),
            0.12f to Color(0xFF071329),
            0.23f to Color(0xFF081B38),
            0.38f to Color(0xFF0B2550),
            0.52f to Color(0xFF050B18),
            0.66f to Color(0xFF081A35),
            0.79f to Color(0xFF0A2450),
            0.91f to Color(0xFF071329),
            1.00f to Color(0xFF030712)
        ),
        start = Offset.Zero,
        end = Offset(900f, 1500f)
    )
    Column(
        Modifier.fillMaxSize()
            .background(homeBackgroundBrush)
            .drawBehind {
                drawRect(brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF168BFF).copy(alpha = 0.24f), Color(0xFF168BFF).copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.28f, size.height * 0.78f),
                    radius = size.maxDimension * 0.62f
                ))
                drawRect(brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF008DFF).copy(alpha = 0.22f), Color(0xFF0054C8).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.42f),
                    radius = size.maxDimension * 0.58f
                ))
                drawRect(brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0xFF36A9FF).copy(alpha = 0.12f), Color.Transparent),
                    start = Offset(0f, size.height * 0.84f),
                    end = Offset(size.width, size.height * 0.58f)
                ))
            }
            .statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.clickable { onClose() }.padding(horizontal = 8.dp))
            SupportBadgeLogo(size = 42.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("TEXNO DƏSTƏK QRUPU", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                if (groupSubtitle.isNotBlank()) Text(groupSubtitle, color = Color(0xFFBDBDBD), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 5, softWrap = true, overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable { showMembersDialog = true }.padding(start = 70.dp, end = 16.dp, top = 0.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("👥 Üzvlər: ${groupMembers.size}", color = Color(0xFFE0E0E0), fontSize = 11.sp)
            Spacer(Modifier.width(12.dp))
            Text("🟢 Onlayn: ${groupMembers.count { it.third }}", color = Color(0xFF72D99A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("SİYAHI", color = Color(0xFFFFD36A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.fillMaxWidth().clickable { showGroupRules = true }.padding(start = 70.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📋", fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("QRUP QAYDALARI", color = Color(0xFFFFD36A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color(0xFF303030))
        if (showMembersDialog) {
            AlertDialog(
                onDismissRequest = { showMembersDialog = false },
                title = {
                    Column {
                        Text("QRUP İSTİFADƏÇİLƏRİ")
                        Text("Cəmi: ${groupMembers.size} · Aktiv: ${groupMembers.count { it.third }}", fontSize = 12.sp, color = Color(0xFF72D99A))
                    }
                },
                text = {
                    if (groupMembers.isEmpty()) Text("Hələ qrup üzvü yoxdur.")
                    else LazyColumn(Modifier.heightIn(max = 440.dp)) {
                        items(groupMembers, key = { it.first }) { member ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(member.second, color = Color.White, modifier = Modifier.weight(1f))
                                Text(if (member.third) "ONLAYN" else "OFLAYN",
                                    color = if (member.third) Color(0xFF72D99A) else Color(0xFFAAAAAA),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Color(0xFF444444))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showMembersDialog = false }) { Text("BAĞLA") } }
            )
        }
        if (showGroupRules) {
            Column(
                Modifier.weight(1f).fillMaxWidth().background(Color.Transparent).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("QRUP QAYDALARI", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("✕", color = Color.White, fontSize = 26.sp, modifier = Modifier.clickable { prefs.edit().putBoolean("group_rules_seen_v2", true).apply(); showGroupRules = false }.padding(8.dp))
                }
                if (showRulesEditor && isGroupAdmin) {
                    OutlinedTextField(
                        value = rulesDraft,
                        onValueChange = { rulesDraft = it },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        placeholder = { Text("Qrup qaydalarını yazın") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                    )
                    Button(onClick = { saveGroupRules() }, enabled = !rulesSaving, modifier = Modifier.fillMaxWidth()) {
                        Text(if (rulesSaving) "YADDA SAXLANILIR..." else "QAYDALARI YADDA SAXLA")
                    }
                } else {
                    Text(groupRules, color = Color(0xFFE6E6E6), fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    if (isGroupAdmin) {
                        OutlinedButton(onClick = { rulesDraft = groupRules; showRulesEditor = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("QAYDALARI REDAKTƏ ET")
                        }
                    }
                    Text("Oxuduqdan sonra yuxarı sağdakı ✕ düyməsinə toxunaraq qrupa keçin.", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                }
            }
        } else if (!nameConfirmed) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Qrupa xoş gəlmisiniz", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Qrupda görünəcək adınızı bir dəfə yazın.", color = Color.LightGray, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = nickname, onValueChange = { nickname = it.take(24) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Adınız") })
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (nickname.trim().length >= 2) {
                        nickname = nickname.trim()
                        prefs.edit().putString("nickname", nickname).apply()
                        onNicknameChanged(nickname)
                        nameConfirmed = true
                    } else status = "Ad ən azı 2 hərf olmalıdır."
                }, modifier = Modifier.fillMaxWidth()) { Text("Daxil ol") }
                if (status.isNotBlank()) Text(status, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                state = messageListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text("Hələ mesaj yoxdur. İlk mesajı sən göndər!", color = Color.LightGray, modifier = Modifier.fillMaxWidth().padding(top = 30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                items(messages.asReversed(), key = { it.id }) { item ->
                    val adminMessage = item.uid == ADMIN_UID
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (adminMessage) Arrangement.Start else Arrangement.End) {
                        val bubbleColor = if (adminMessage) Color(0xFF38280D) else when (bubbleStyle) {
                            "gold" -> Color(0xFFD6A437)
                            "royal" -> Color(0xFF35234F)
                            "diamond" -> Color(0xFFDDF7FA)
                            "rose" -> Color(0xFFB94D78)
                            "emerald" -> Color(0xFF087B61)
                            "obsidian" -> Color(0xFF24232A)
                            "sapphire" -> Color(0xFF2856A6)
                            else -> Color(0xFFF4F0E8)
                        }
                        val bubbleTextColor = if (!adminMessage && (bubbleStyle == "diamond" || bubbleStyle == "classic")) Color(0xFF171717) else Color.White
                        val bubbleShape = RoundedCornerShape(if (adminMessage) 22.dp else when (bubbleStyle) {
                            "gold", "royal", "obsidian" -> 22.dp
                            "diamond" -> 28.dp
                            "rose" -> 26.dp
                            "emerald" -> 18.dp
                            else -> 24.dp
                        })
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bubbleColor),
                            shape = bubbleShape,
                            modifier = Modifier.fillMaxWidth(0.88f)
                                .then(if (adminMessage || bubbleStyle == "gold" || bubbleStyle == "royal" || bubbleStyle == "obsidian") Modifier.border(2.dp, Color(0xFFFFD36A), bubbleShape) else Modifier)
                                .clickable { reply = item }
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (adminMessage) "MR_R3HB3R" else item.nickname, color = bubbleTextColor.copy(alpha = 0.78f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (adminMessage) {
                                        Text(
                                            "👑 VIP ADMIN",
                                            color = Color(0xFFFFD36A),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier
                                                .background(Color(0xFF292015), RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFFD6A437), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (item.replyToNickname.isNotBlank()) Text("↪ ${item.replyToNickname}: ${item.replyToText}", color = bubbleTextColor.copy(alpha = 0.75f), fontSize = 12.sp)
                                if (item.text.isNotBlank()) Text(item.text, color = bubbleTextColor, fontSize = 17.sp, lineHeight = 24.sp)
                                if (item.imageUrl.isNotBlank()) AsyncImage(model = item.imageUrl, contentDescription = "Qrup şəkli", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
                                if (item.videoUrl.isNotBlank()) GroupVideoPlayer(item.videoUrl)
                                if (item.audioUrl.isNotBlank()) GroupVoiceMessagePlayer(item.audioUrl, item.createdAt)
                                Text("Cavab vermək üçün toxun", color = bubbleTextColor.copy(alpha = 0.65f), fontSize = 10.sp)
                                if (item.createdAt > 0L) {
                                    Text(
                                        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt)),
                                        color = bubbleTextColor.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (reply != null) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF242424)).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cavab: ${reply!!.nickname} · ${reply!!.text.ifBlank { "Media" }}", color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("✕", color = Color.White, modifier = Modifier.clickable { reply = null }.padding(8.dp))
                }
            }
            if (recording) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).background(Color(0xFFF9F9F9), RoundedCornerShape(22.dp)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%d:%02d".format(recordingSeconds / 60, recordingSeconds % 60), color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            val bars = liveWaveform.toList()
                            bars.forEach { height ->
                                Box(Modifier.width(3.dp).height(height.dp).background(Color(0xFF73777A), RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { cancelVoice() }, modifier = Modifier.size(44.dp).background(Color(0xFFFFE6E9), CircleShape)) {
                            Icon(Icons.Default.Delete, contentDescription = "Səs yazısını sil", tint = Color(0xFFD91B49))
                        }
                        Button(onClick = { toggleVoicePause() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0)), enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Text(if (recordingPaused) "▶ Davam et" else "Ⅱ Dayandır", color = Color.Black)
                        }
                        IconButton(onClick = { finishVoice() }, modifier = Modifier.size(46.dp).background(Color(0xFF20A66A), CircleShape)) {
                            Icon(Icons.Default.Send, contentDescription = "Səs mesajını göndər", tint = Color.White)
                        }
                    }
                }
            } else {
                if (showEmojiPicker) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("😀", "😂", "🥰", "😍", "😊", "👍", "❤️", "🔥", "🙏", "👏", "😎", "🎉", "😢", "🤔", "😘").forEach { emoji ->
                            Text(emoji, fontSize = 25.sp, modifier = Modifier.clickable { draft += emoji })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).background(Color(0xFFF4F0E8), RoundedCornerShape(28.dp)).padding(horizontal = 4.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showEmojiPicker = !showEmojiPicker }, enabled = !busy, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = "Smayliklər", tint = Color(0xFF666B70))
                    }
                    BasicTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f).padding(vertical = 10.dp), singleLine = true, enabled = !busy, textStyle = TextStyle(color = Color(0xFF202124), fontSize = 15.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF159B68)), decorationBox = { inner -> Box { if (draft.isEmpty()) Text("Mesaj", color = Color.Gray); inner() } })
                    IconButton(onClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) }, enabled = !busy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Şəkil və fayl seç", tint = Color(0xFF666B70))
                    }
                    IconButton(onClick = { cameraLauncher.launch(null) }, enabled = !busy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Kamera", tint = Color(0xFF666B70))
                    }
                    IconButton(
                        onClick = { if (draft.isNotBlank()) { sendText(); showEmojiPicker = false } else micPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                        enabled = !busy,
                        modifier = Modifier.size(54.dp).background(Color(0xFF20A66A), CircleShape)
                    ) {
                        if (draft.isNotBlank()) {
                            Icon(Icons.Default.Send, contentDescription = "Mesajı göndər", tint = Color.White, modifier = Modifier.size(25.dp))
                        } else {
                            Icon(Icons.Default.Mic, contentDescription = "Səs yaz", tint = Color.White, modifier = Modifier.size(29.dp))
                        }
                    }
                }
            }
            if (status.isNotBlank()) Text(status, color = Color(0xFFFFD36A), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupManagementAdminPanel() {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid.orEmpty()
    var bubbleStyle by remember { mutableStateOf("default") }
    val bubbleStyleOptions = listOf(
        "gold" to "Qızıl Premium",
        "royal" to "Royal Gold",
        "diamond" to "Diamond",
        "rose" to "Rose Gold",
        "emerald" to "Emerald",
        "obsidian" to "Obsidian Premium",
        "sapphire" to "Sapphire",
        "classic" to "Klassik"
    )
    LaunchedEffect(Unit) {
        db.collection("group_settings").document("bubble_style").get()
            .addOnSuccessListener { bubbleStyle = it.getString("style") ?: "default" }
    }
    fun saveBubbleStyle(style: String) {
        if (uid != ADMIN_UID) return
        db.collection("group_settings").document("bubble_style")
            .set(mapOf("style" to style, "updated_at" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { bubbleStyle = style }
    }
    var subtitle by remember { mutableStateOf("Ümumi söhbət") }
    var subtitleDraft by remember { mutableStateOf("") }
    var editingSubtitle by remember { mutableStateOf(false) }
    var managers by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupUsers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showUserPicker by remember { mutableStateOf(false) }
    var grantMedia by remember { mutableStateOf(false) }
    var grantAddUsers by remember { mutableStateOf(false) }
    var grantPin by remember { mutableStateOf(false) }
    var grantEditTags by remember { mutableStateOf(false) }
    var grantChangeInfo by remember { mutableStateOf(false) }
    var exceptionUid by remember { mutableStateOf("") }
    var showException by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var antiSpam by remember { mutableStateOf(false) }
    var allowMedia by remember { mutableStateOf(true) }
    var allowAddUsers by remember { mutableStateOf(true) }
    var allowPin by remember { mutableStateOf(false) }
    var allowTags by remember { mutableStateOf(false) }
    var allowInfo by remember { mutableStateOf(false) }
    var stars by remember { mutableStateOf(false) }
    var slowMode by remember { mutableIntStateOf(0) }
    var rules by remember { mutableStateOf("") }
    var rulesDraft by remember { mutableStateOf("") }
    var editingRules by remember { mutableStateOf(false) }
    var isManager by remember { mutableStateOf(uid == ADMIN_UID) }
    var isOwner = uid == ADMIN_UID

    LaunchedEffect(uid) {
        if (uid.isNotBlank() && uid != ADMIN_UID) {
            db.collection("group_managers").document(uid).get()
                .addOnSuccessListener { isManager = it.exists() }
        }
        db.collection("group_settings").document("group_subtitle").get()
            .addOnSuccessListener { subtitle = it.getString("text") ?: "Ümumi söhbət" }
        db.collection("group_settings").document("admin_controls").get().addOnSuccessListener { d ->
            antiSpam = d.getBoolean("anti_spam") ?: false
            allowMedia = d.getBoolean("allow_media") ?: true
            allowAddUsers = d.getBoolean("allow_add_users") ?: true
            allowPin = d.getBoolean("allow_pin_messages") ?: false
            allowTags = d.getBoolean("allow_edit_own_tags") ?: false
            allowInfo = d.getBoolean("allow_change_group_info") ?: false
            stars = d.getBoolean("stars_enabled") ?: false
            slowMode = (d.getLong("slow_mode_seconds") ?: 0L).toInt()
        }
        db.collection("group_managers").get().addOnSuccessListener { managers = it.documents.map { doc -> doc.id }.filter { it != ADMIN_UID } }
        db.collection("group_messages").get().addOnSuccessListener { snap ->
            groupUsers = snap.documents.mapNotNull { doc ->
                val userId = doc.getString("uid").orEmpty()
                val name = doc.getString("nickname").orEmpty().trim()
                if (userId.isNotBlank() && name.isNotBlank() && userId != ADMIN_UID) userId to name else null
            }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
        db.collection("group_settings").document("rules").get()
            .addOnSuccessListener { rules = it.getString("text") ?: "" }
    }
    fun saveSubtitle(value: String) {
        db.collection("group_settings").document("group_subtitle")
            .set(mapOf("text" to value.trim(), "updated_at" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { subtitle = value.trim(); editingSubtitle = false; status = "Qrupun alt yazısı yadda saxlandı." }
            .addOnFailureListener { status = "Xəta: ${it.localizedMessage}" }
    }
    fun saveControl(key: String, value: Any) {
        db.collection("group_settings").document("admin_controls")
            .set(mapOf(key to value, "updated_at" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { status = "Parametr yadda saxlanmadı: ${it.localizedMessage}" }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("QRUP RƏHBƏRLİYİ", color = Color(0xFFFFD36A), fontWeight = FontWeight.Black, fontSize = 18.sp)
        if (isOwner) {
            Text("PREMIUM SÖHBƏT BALONLARI", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
            Text("Köhnə dizaynlar əvəzlənib. Seçilən premium görünüş bütün istifadəçilərə tətbiq olunacaq.", color = Color.LightGray, fontSize = 12.sp)
            bubbleStyleOptions.forEach { (key, label) ->
                val preview = when (key) {
                    "gold" -> Color(0xFFD6A437)
                    "royal" -> Color(0xFF35234F)
                    "diamond" -> Color(0xFFDDF7FA)
                    "rose" -> Color(0xFFB94D78)
                    "emerald" -> Color(0xFF087B61)
                    "obsidian" -> Color(0xFF24232A)
                    "sapphire" -> Color(0xFF2856A6)
                    else -> Color(0xFFF4F0E8)
                }
                val foreground = if (key == "diamond" || key == "classic") Color(0xFF111A2C) else Color.White
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF111A2C))
                    .clickable { saveBubbleStyle(key) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(preview)
                        .then(if (key == "gold" || key == "royal" || key == "obsidian") Modifier.border(2.dp, Color(0xFFFFD36A), RoundedCornerShape(14.dp)) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text(when (key) {
                            "gold" -> "✦  Aa  PREMIUM GOLD  ✦"
                            "royal" -> "♛  Aa  ROYAL GOLD"
                            "diamond" -> "❖  Aa  DIAMOND"
                            "rose" -> "❀  Aa  ROSE GOLD"
                            "emerald" -> "✧  Aa  EMERALD"
                            "obsidian" -> "♜  Aa  OBSIDIAN"
                            "sapphire" -> "✦  Aa  SAPPHIRE"
                            else -> "Aa  CLASSIC"
                        }, color = foreground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    if (bubbleStyle == key) Text("✓", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider()
        }
        Text("Qrupun alt yazısı", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
        if (editingSubtitle) {
            OutlinedTextField(subtitleDraft, { subtitleDraft = it }, modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 3, maxLines = 8, label = { Text("Yeni alt yazı") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveSubtitle(subtitleDraft) }, modifier = Modifier.weight(1f)) { Text("YADDA SAXLA") }
                OutlinedButton(onClick = { editingSubtitle = false }, modifier = Modifier.weight(1f)) { Text("LƏĞV ET") }
            }
        } else {
            Text("Hazırkı yazı: ${subtitle.ifBlank { "(boşdur)" }}", color = Color.LightGray)
            OutlinedButton(onClick = { subtitleDraft = subtitle; editingSubtitle = true }, modifier = Modifier.fillMaxWidth()) { Text("ALT YAZINI REDAKTƏ ET") }
            OutlinedButton(onClick = { saveSubtitle("") }, modifier = Modifier.fillMaxWidth(), enabled = subtitle.isNotBlank()) { Text("ALT YAZINI SİL") }
        }
        HorizontalDivider()
        Text("QRUP PARAMETRLƏRİ", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
        @Composable
        fun Setting(title: String, checked: Boolean, changed: (Boolean) -> Unit) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = checked, onCheckedChange = changed)
            }
        }
        Setting("Aqressiv Anti-Spam", antiSpam) { antiSpam = it; saveControl("anti_spam", it) }
        Setting("Media göndərmə", allowMedia) { allowMedia = it; saveControl("allow_media", it) }
        Setting("İstifadəçi əlavə etmək", allowAddUsers) { allowAddUsers = it; saveControl("allow_add_users", it) }
        Setting("Mesajları sabitləmək", allowPin) { allowPin = it; saveControl("allow_pin_messages", it) }
        Setting("Öz etiketlərini redaktə etmək", allowTags) { allowTags = it; saveControl("allow_edit_own_tags", it) }
        Setting("Qrup məlumatlarını dəyişmək", allowInfo) { allowInfo = it; saveControl("allow_change_group_info", it) }
        Setting("Mesajları ulduzla ödənişli etmək", stars) { stars = it; saveControl("stars_enabled", it) }
        Text("YAVAŞ REJİM", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
        val slowOptions = listOf(0 to "Bağlı", 5 to "5 san", 10 to "10 san", 30 to "30 san", 60 to "1 dəq", 300 to "5 dəq", 900 to "15 dəq", 3600 to "1 saat")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            slowOptions.forEach { (seconds, label) ->
                androidx.compose.material3.FilterChip(selected = slowMode == seconds, onClick = { slowMode = seconds; saveControl("slow_mode_seconds", seconds) }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        HorizontalDivider()
        Text("ADMİNLƏRİ İDARƏ ET", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)

        managers.forEach { manager ->
            val displayName = groupUsers.firstOrNull { it.first == manager }?.second ?: "Adı tapılmadı"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    db.collection("group_managers").document(manager).delete().addOnSuccessListener { managers = managers - manager; status = "${displayName} adlı istifadəçinin adminliyi ləğv edildi." }
                }) { Text("LƏĞV ET", color = Color(0xFFFF7777)) }
            }
        }
        if (isOwner) {
            OutlinedButton(onClick = {
                selectedUser = null
                grantMedia = false; grantAddUsers = false; grantPin = false; grantEditTags = false; grantChangeInfo = false
                showUserPicker = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text("＋ QRUP ÜZVLƏRİNDƏN ADMİN SEÇ")
            }
        }
        if (showUserPicker) {
            AlertDialog(
                onDismissRequest = { showUserPicker = false },
                title = { Text("Qrup üzvünü və icazələri seç") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (groupUsers.isEmpty()) {
                            Text("Hələ qrupda mesaj yazan istifadəçi tapılmadı.")
                        } else {
                            LazyColumn(Modifier.heightIn(max = 220.dp)) {
                                items(groupUsers) { user ->
                                    Row(Modifier.fillMaxWidth().clickable { selectedUser = user }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(user.second, color = Color.White, modifier = Modifier.weight(1f))
                                        if (selectedUser?.first == user.first) Text("✓", color = Color(0xFFFFD36A))
                                    }
                                    HorizontalDivider(color = Color(0xFF444444))
                                }
                            }
                        }
                        if (selectedUser != null) {
                            Text("VERİLƏCƏK İCAZƏLƏR", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
                            @Composable
                            fun Permission(label: String, checked: Boolean, change: (Boolean) -> Unit) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Switch(checked = checked, onCheckedChange = change)
                                }
                            }
                            Permission("Media göndərmək", grantMedia) { grantMedia = it }
                            Permission("İstifadəçi əlavə etmək", grantAddUsers) { grantAddUsers = it }
                            Permission("Mesajları sabitləmək", grantPin) { grantPin = it }
                            Permission("Öz etiketlərini redaktə etmək", grantEditTags) { grantEditTags = it }
                            Permission("Qrup məlumatlarını dəyişmək", grantChangeInfo) { grantChangeInfo = it }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val user = selectedUser
                        if (user != null && user.first != ADMIN_UID) {
                            db.collection("group_managers").document(user.first)
                                .set(mapOf(
                                    "uid" to user.first,
                                    "nickname" to user.second,
                                    "added_at" to FieldValue.serverTimestamp(),
                                    "permissions" to mapOf(
                                        "media" to grantMedia,
                                        "add_users" to grantAddUsers,
                                        "pin_messages" to grantPin,
                                        "edit_own_tags" to grantEditTags,
                                        "change_group_info" to grantChangeInfo
                                    )
                                ))
                                .addOnSuccessListener {
                                    managers = (managers + user.first).distinct()
                                    showUserPicker = false
                                    selectedUser = null
                                    status = "${user.second} adlı istifadəçiyə seçilmiş icazələr verildi."
                                }
                                .addOnFailureListener { status = "Adminlik verilmədi: ${it.localizedMessage}" }
                        }
                    }, enabled = selectedUser != null) { Text("ADMİN ET") }
                },
                dismissButton = { TextButton(onClick = { showUserPicker = false; selectedUser = null }) { Text("LƏĞV ET") } }
            )
        }
        OutlinedButton(onClick = { showException = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ İSTİSNA ƏLAVƏ ET") }
        if (showException) {
            OutlinedTextField(exceptionUid, { exceptionUid = it }, modifier = Modifier.fillMaxWidth(), label = { Text("İstifadəçinin UID-si") })
            Button(onClick = {
                val clean = exceptionUid.trim()
                if (clean.isNotBlank()) db.collection("group_exceptions").document(clean)
                    .set(mapOf("uid" to clean, "created_at" to FieldValue.serverTimestamp()))
                    .addOnSuccessListener { showException = false; exceptionUid = ""; status = "İstisna əlavə edildi." }
            }, modifier = Modifier.fillMaxWidth()) { Text("ƏLAVƏ ET") }
        }
        HorizontalDivider()
        Text("QRUP QAYDALARI", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
        if (editingRules) {
            OutlinedTextField(rulesDraft, { rulesDraft = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text("Qrup qaydaları") })
            Button(onClick = {
                db.collection("group_settings").document("rules").set(mapOf("text" to rulesDraft, "updated_at" to FieldValue.serverTimestamp()))
                    .addOnSuccessListener { rules = rulesDraft; editingRules = false; status = "Qaydalar yadda saxlandı." }
                    .addOnFailureListener { status = "Qaydalar saxlanmadı: ${it.localizedMessage}" }
            }, modifier = Modifier.fillMaxWidth()) { Text("QAYDALARI YADDA SAXLA") }
        } else {
            Text(rules.ifBlank { "Qaydalar yazılmayıb." }, color = Color.LightGray, fontSize = 12.sp)
            OutlinedButton(onClick = { rulesDraft = rules; editingRules = true }, modifier = Modifier.fillMaxWidth()) { Text("QAYDALARI REDAKTƏ ET") }
        }
        Button(onClick = { showClearConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9F2929)), modifier = Modifier.fillMaxWidth()) { Text("BÜTÜN QRUP MESAJLARINI SİL") }
        if (showClearConfirm) {
            AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("Bütün mesajlar silinsin?") }, text = { Text("Bu əməliyyat geri qaytarılmır.") },
                confirmButton = { TextButton(onClick = {
                    db.collection("group_messages").get().addOnSuccessListener { snap ->
                        val chunks = snap.documents.chunked(400)
                        fun deleteChunk(index: Int) {
                            if (index >= chunks.size) { showClearConfirm = false; status = "Qrup mesajları silindi."; return }
                            val batch = db.batch()
                            chunks[index].forEach { batch.delete(it.reference) }
                            batch.commit().addOnSuccessListener { deleteChunk(index + 1) }.addOnFailureListener { status = "Mesajlar silinmədi: ${it.localizedMessage}" }
                        }
                        deleteChunk(0)
                    }
                }) { Text("SİL") } },
                dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("LƏĞV ET") } })
        }
        if (status.isNotBlank()) Text(status, color = Color(0xFFFFD36A), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TexnoTelekomApp(
    onInstallUpdate: (String) -> Unit = {},
    notificationTarget: Pair<String, String>? = null,
    onNotificationTargetHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var showGroupChat by remember { mutableStateOf(false) }
    BackHandler(enabled = showGroupChat) { showGroupChat = false }
    var groupNickname by remember { mutableStateOf(context.getSharedPreferences("texno_group_chat", Context.MODE_PRIVATE).getString("nickname", "") ?: "") }
    var profileNickname by remember { mutableStateOf(groupNickname) }
    var groupNameConfirmed by remember { mutableStateOf(groupNickname.trim().length >= 2) }
    var groupDraft by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<GroupChatMessage?>(null) }
    var groupBusy by remember { mutableStateOf(false) }
    var groupAuthReady by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var groupAuthError by remember { mutableStateOf("") }
    val groupMessages = remember { mutableStateListOf<GroupChatMessage>() }
    var feedbackDraft by remember { mutableStateOf("") }
    var feedbackBusy by remember { mutableStateOf(false) }
    var feedbackRecording by remember { mutableStateOf(false) }
    var feedbackRecordingPaused by remember { mutableStateOf(false) }
    var feedbackRecordingSeconds by remember { mutableIntStateOf(0) }
    var feedbackRecordingStartMs by remember { mutableLongStateOf(0L) }
    var feedbackPausedTotalMs by remember { mutableLongStateOf(0L) }
    var feedbackPauseStartedMs by remember { mutableLongStateOf(0L) }
    val feedbackWaveform = remember { mutableStateListOf<Int>() }
    var feedbackRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var feedbackFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(feedbackRecording, feedbackRecordingPaused) {
        while (feedbackRecording && !feedbackRecordingPaused) {
            val elapsed = SystemClock.elapsedRealtime() - feedbackRecordingStartMs - feedbackPausedTotalMs
            feedbackRecordingSeconds = (elapsed.coerceAtLeast(0L) / 1000L).toInt()
            val amp = try { feedbackRecorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
            val level = kotlin.math.sqrt((amp.coerceIn(0, 32767) / 32767f).toDouble()).toFloat()
            feedbackWaveform.add((5 + level * 34).toInt())
            if (feedbackWaveform.size > 42) feedbackWaveform.removeAt(0)
            delay(100)
        }
    }
    var showFeedback by remember { mutableStateOf(false) }
    var showClearFeedbackConfirm by remember { mutableStateOf(false) }
    val feedbackItems = remember { mutableStateListOf<String>() }
    fun startFeedbackRecording() {
        try {
            val file = File(context.cacheDir, "feedback_voice_${System.currentTimeMillis()}.m4a")
            val recorder = createGroupVoiceRecorder(context)
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioEncodingBitRate(96000)
            recorder.setAudioSamplingRate(44100); recorder.setOutputFile(file.absolutePath)
            recorder.prepare(); recorder.start()
            feedbackFile = file
            feedbackRecorder = recorder
            feedbackWaveform.clear()
            feedbackRecordingSeconds = 0
            feedbackPausedTotalMs = 0L
            feedbackPauseStartedMs = 0L
            feedbackRecordingStartMs = SystemClock.elapsedRealtime()
            feedbackRecordingPaused = false
            feedbackRecording = true
        } catch (e: Exception) { groupAuthError = "Səs yazısı başlatılmadı: ${e.localizedMessage}" }
    }
    val feedbackAudioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startFeedbackRecording() else groupAuthError = "Mikrofon icazəsi lazımdır."
    }
    var groupMediaBusy by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val file = File(context.cacheDir, "group_voice_${System.currentTimeMillis()}.m4a")
                val recorder = createGroupVoiceRecorder(context)
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(96000)
                recorder.setAudioSamplingRate(44100)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare(); recorder.start()
                voiceFile = file; voiceRecorder = recorder; isRecordingVoice = true; groupAuthError = ""
            } catch (e: Exception) { groupAuthError = "Səs yazısı başlatılmadı: ${e.localizedMessage}" }
        } else groupAuthError = "Səs yazmaq üçün mikrofon icazəsi lazımdır."
    }
    val groupImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            groupMediaBusy = true
            uploadPhoneImage(context, uri) { result ->
                result.onSuccess { url ->
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null && groupNickname.trim().length >= 2) {
                        val data = hashMapOf<String, Any>("uid" to user.uid, "nickname" to groupNickname.trim().take(24), "text" to "", "image_url" to url, "audio_url" to "", "created_at" to FieldValue.serverTimestamp())
                        replyToMessage?.let { data["reply_to_id"] = it.id; data["reply_to_nickname"] = it.nickname; data["reply_to_text"] = it.text.ifBlank { if (it.imageUrl.isNotBlank()) "📷 Şəkil" else "🎙 Səs mesajı" }.take(180) }
                        db.collection("group_messages").add(data).addOnSuccessListener { groupMediaBusy = false; groupAuthError = "" }.addOnFailureListener { groupMediaBusy = false; groupAuthError = "Şəkil göndərilmədi: ${it.localizedMessage}" }
                    } else { groupMediaBusy = false; groupAuthError = "Əvvəlcə qrup adınızı yazın." }
                }.onFailure { groupMediaBusy = false; groupAuthError = "Şəkil yüklənmədi: ${it.localizedMessage}" }
            }
        }
    }

    // Server-managed design values update live without publishing a new APK.
    var designBgHex by remember { mutableStateOf("#050A18") }
    var designPanelHex by remember { mutableStateOf("#111A2C") }
    var designAccentHex by remember { mutableStateOf("#168BFF") }
    var designGoldCards by remember { mutableStateOf(true) }
    val liveBg = remoteColor(designBgHex, bg)
    val livePanel = remoteColor(designPanelHex, panel)
    val liveAccent = remoteColor(designAccentHex, accent)

    LaunchedEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) groupAuthReady = true
        else auth.signInAnonymously().addOnSuccessListener { groupAuthReady = true }
            .addOnFailureListener { groupAuthError = "Qrupa qoşulmaq mümkün olmadı: ${it.localizedMessage}" }
    }

    var availableUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var updateCheckInProgress by remember { mutableStateOf(false) }
    var showUpdateCheckDialog by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf("") }
    var updateCheckResult by remember { mutableStateOf<ReleaseUpdate?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val checkForAppUpdate: (Boolean) -> Unit = { manualCheck ->
        Thread {
            try {
                val connection = (URL("https://api.github.com/repos/aqsinkerimov2025-arch/TEXNO-TELEKOM/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "TEXNO-TELEKOM-Android")
                }
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                connection.disconnect()
                val tag = json.optString("tag_name")
                val remoteCode = tag.removePrefix("app-v").toLongOrNull() ?: 0L
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name") == "TEXNO-TELEKOM.apk") apkUrl = asset.optString("browser_download_url")
                }
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
                val currentName = info.versionName ?: "Naməlum"
                val displayVersion = Regex("""\d+\.\d+(?:\.\d+)?""")
                    .find(json.optString("name"))?.value ?: currentName
                if (remoteCode > currentCode && apkUrl.startsWith("https://")) {
                    val rawNotes = json.optString("body").ifBlank { "Yeni versiya və yeniliklər mövcuddur." }
                    val update = ReleaseUpdate(remoteCode, displayVersion, apkUrl, rawNotes.trim())
                    Handler(Looper.getMainLooper()).post {
                        updateCheckInProgress = false
                        availableUpdate = update
                        if (manualCheck) {
                            updateCheckResult = update
                            updateCheckMessage = ""
                            showUpdateCheckDialog = true
                        }
                        // Yeniləmə yalnız tətbiqin daxilində göstərilir; telefon bildirişi göndərilmir.
                    }
                } else if (remoteCode > 0L && remoteCode <= currentCode) {
                    Handler(Looper.getMainLooper()).post {
                        updateCheckInProgress = false
                        if (manualCheck) {
                            updateCheckResult = null
                            updateCheckMessage = "Tətbiqin versiyası son versiyadır. (Versiya: $currentName)"
                            showUpdateCheckDialog = true
                        }
                    }
                } else {
                    throw Exception("Son versiyanın APK faylı tapılmadı.")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    updateCheckInProgress = false
                    if (manualCheck) {
                        updateCheckResult = null
                        updateCheckMessage = "Yeniləməni yoxlamaq mümkün olmadı. İnternet bağlantınızı yoxlayıb yenidən cəhd edin."
                        showUpdateCheckDialog = true
                    }
                }
            }
        }.start()
    }
    // Ekran ilk dəfə açılanda da dərhal yoxla; ON_RESUME observer-i gec qoşula bilər.
    LaunchedEffect(Unit) {
        checkForAppUpdate(false)
    }
    DisposableEffect(lifecycleOwner) {
        val updateHandler = Handler(Looper.getMainLooper())
        val updateCheck = object : Runnable {
            override fun run() {
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    checkForAppUpdate(false)
                    updateHandler.postDelayed(this, 10 * 1000L)
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    updateHandler.removeCallbacks(updateCheck)
                    updateCheck.run()
                }
                Lifecycle.Event.ON_PAUSE -> updateHandler.removeCallbacks(updateCheck)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            updateHandler.removeCallbacks(updateCheck)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    if (showUpdateCheckDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateCheckDialog = false },
            title = { Text(if (updateCheckResult != null) "Yeniləmə mövcuddur" else "Yeniləmə yoxlanışı") },
            text = {
                Text(
                    updateCheckResult?.let { "TEXNO-TELEKOM üçün yeni versiya: ${it.versionName}\n\nYeniləməni indi yükləyə bilərsiniz." }
                        ?: updateCheckMessage
                )
            },
            confirmButton = {
                if (updateCheckResult != null) {
                    TextButton(onClick = {
                        val update = updateCheckResult
                        showUpdateCheckDialog = false
                        if (update != null) onInstallUpdate(update.apkUrl)
                    }) { Text("YENİLƏMƏNİ YÜKLƏ") }
                } else {
                    TextButton(onClick = { showUpdateCheckDialog = false }) { Text("BAĞLA") }
                }
            },
            dismissButton = {
                if (updateCheckResult != null) {
                    TextButton(onClick = { showUpdateCheckDialog = false }) { Text("SONRA") }
                }
            }
        )
    }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }
    var profileDarkMode by remember { mutableStateOf(true) }
    var showPhoneInfo by remember { mutableStateOf(false) }
    var showAppUpdates by remember { mutableStateOf(false) }
    var appUpdateTitle by remember { mutableStateOf("") }
    var appUpdateBody by remember { mutableStateOf("") }
    var appUpdateType by remember { mutableStateOf("app_feature") }
    var showPhoneInfoFeed by remember { mutableStateOf(false) }
    var selectedPhoneInfoId by remember { mutableStateOf<String?>(null) }
    val texnoInfoLogo = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.texno_telekom_program_logo).asImageBitmap()
    }
    var showGames by remember { mutableStateOf(false) }
    var showPrograms by remember { mutableStateOf(false) }
    var adminEntry by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsSelected by remember { mutableStateOf<String?>(null) }




    var notificationProgramId by remember { mutableStateOf<String?>(null) }
    val notificationItemBringIntoView = remember { BringIntoViewRequester() }
    BackHandler(enabled = showPhoneInfoFeed || showPhoneInfo || showAppUpdates || showPrograms || showGames || showFeedback || showAbout || showSecurity || showSettings) {
        // Android/system back from any home-card destination returns directly to home.
        showPhoneInfoFeed = false
        selectedPhoneInfoId = null
        showPhoneInfo = false
        showAppUpdates = false
        showPrograms = false
        showGames = false
        showFeedback = false
        showAbout = false
        showSecurity = false
        showSettings = false
        settingsSelected = null
        selectedBottomTab = 0
    }
    var phoneInfoTitle by remember { mutableStateOf("") }
    var phoneInfoBody by remember { mutableStateOf("") }
    var phoneInfoImage by remember { mutableStateOf<Uri?>(null) }
    var phoneInfoSticker by remember { mutableStateOf<Uri?>(null) }
    var phoneInfoStickerBusy by remember { mutableStateOf(false) }
    var phoneInfoBusy by remember { mutableStateOf(false) }
    var pendingDeletePhoneInfoId by remember { mutableStateOf<String?>(null) }
    var pendingDeletePhoneInfoTitle by remember { mutableStateOf("") }
    var adminOpen by remember { mutableStateOf(false) }
    var showGroupManagementAdmin by remember { mutableStateOf(false) }
    BackHandler(enabled = adminOpen && !showGroupManagementAdmin) { adminOpen = false }
    BackHandler(enabled = showGroupManagementAdmin) { showGroupManagementAdmin = false }
    BackHandler(
        enabled = !showGroupChat && !showPhoneInfoFeed && !showPhoneInfo && !showPrograms && !showGames &&
            !showFeedback && !showAbout && !showSecurity && !showSettings && !adminOpen && !showGroupManagementAdmin
    ) {
        when {
            searchOpen -> { searchOpen = false; query = "" }
            selectedBottomTab != 0 -> selectedBottomTab = 0
            else -> Unit
        }
    }
    var showSiteTextEditor by remember { mutableStateOf(false) }
    var designAdminOpen by remember { mutableStateOf(false) }
    var showDeviceInfo by remember { mutableStateOf(false) }
    var revealDeviceCodeButton by remember { mutableStateOf(false) }
    val deviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Tapılmadı" }
    val deviceHash = remember(deviceId) {
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(deviceId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    var deviceBlocked by remember { mutableStateOf(false) }
    var deviceBlockReason by remember { mutableStateOf("") }
    val isAuthorizedDevice = deviceId in ADMIN_DEVICE_IDS

    LaunchedEffect(deviceHash, groupAuthReady, isAuthorizedDevice) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (groupAuthReady && currentUser != null && !isAuthorizedDevice) {
            db.collection("device_registry").document(deviceHash).set(
                mapOf(
                    "device_hash" to deviceHash,
                    "uid" to currentUser.uid,
                    "last_seen" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            db.collection("blocked_devices").document(deviceHash).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val until = snapshot.getTimestamp("blocked_until")
                        val active = until == null || until.toDate().time > System.currentTimeMillis()
                        deviceBlocked = active
                        deviceBlockReason = if (active) snapshot.getString("reason").orEmpty() else ""
                    } else {
                        deviceBlocked = false
                        deviceBlockReason = ""
                    }
                }
                .addOnFailureListener {
                    // Network/rules errors do not silently lock out a device.
                    deviceBlocked = false
                }
        }
    }
    val savedAdminUser = remember { FirebaseAuth.getInstance().currentUser }
    var loggedIn by remember { mutableStateOf(isAuthorizedDevice && savedAdminUser?.uid == ADMIN_UID && savedAdminUser.email == ADMIN_EMAIL) }


    val pushPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(loggedIn) {
        val adminUser = FirebaseAuth.getInstance().currentUser
        if (loggedIn && adminUser?.uid == ADMIN_UID) {
            val prefs = context.getSharedPreferences("texno_admin_poll", Context.MODE_PRIVATE)
            if (!prefs.contains("last_feedback_seen_at")) {
                prefs.edit().putLong("last_feedback_seen_at", System.currentTimeMillis()).apply()
            }
            val request = PeriodicWorkRequestBuilder<AdminFeedbackPollWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "texno_admin_feedback_poll",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pushPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    db.collection("admin_push_tokens").document(ADMIN_UID).set(
                        mapOf("uid" to ADMIN_UID, "token" to token, "updated_at" to FieldValue.serverTimestamp()),
                        SetOptions.merge()
                    )
                }
        }
    }

    DisposableEffect(loggedIn, deviceId) {
        var feedbackQuery: com.google.firebase.firestore.Query = db.collection("admin_feedback")
        if (!loggedIn) feedbackQuery = feedbackQuery.whereEqualTo("device_id", deviceId)
        val registration = feedbackQuery.orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e == null && snap != null) {
                    feedbackItems.clear()
                    snap.documents.forEach { d -> feedbackItems.add("${d.getString("nickname") ?: "İstifadəçi"}|${d.getString("text") ?: ""}|${d.getString("audio_url") ?: ""}|${d.getTimestamp("created_at")?.toDate()?.time ?: 0L}|${d.id}") }
                }
            }
        onDispose { registration.remove() }
    }

    var email by remember { mutableStateOf(ADMIN_EMAIL) }
    var password by remember { mutableStateOf("") }

    var error by remember { mutableStateOf("") }
    fun sendFeedbackText() {
        val textValue = feedbackDraft.trim()
        if (textValue.isBlank() || feedbackBusy) return
        feedbackBusy = true
        val data = hashMapOf<String, Any>("nickname" to groupNickname.trim().take(24).ifBlank { "İstifadəçi" }, "device_id" to deviceId, "text" to textValue, "audio_url" to "", "created_at" to FieldValue.serverTimestamp())
        db.collection("admin_feedback").add(data)
            .addOnSuccessListener { feedbackDraft = ""; feedbackBusy = false }
            .addOnFailureListener { feedbackBusy = false; error = "Mesaj göndərilmədi: ${it.localizedMessage}" }
    }
    fun stopAndSendFeedbackVoice() {
        val recorder = feedbackRecorder ?: return
        val file = feedbackFile
        try { recorder.stop() } catch (_: Exception) { }
        try { recorder.release() } catch (_: Exception) { }
        feedbackRecorder = null
        feedbackRecording = false
        feedbackRecordingPaused = false
        if (file == null || !file.exists() || file.length() < 500) {
            error = "Səs yazısı boşdur. Yenidən yaz."
            return
        }
        feedbackBusy = true
        uploadGroupAudio(file) { result ->
            result.onSuccess { audioUrl ->
                val data = hashMapOf<String, Any>("nickname" to groupNickname.trim().take(24).ifBlank { "İstifadəçi" }, "device_id" to deviceId, "text" to "", "audio_url" to audioUrl, "created_at" to FieldValue.serverTimestamp())
                db.collection("admin_feedback").add(data)
                    .addOnSuccessListener { feedbackBusy = false; file.delete() }
                    .addOnFailureListener { feedbackBusy = false; error = "Səs göndərilmədi: ${it.localizedMessage}" }
            }.onFailure { feedbackBusy = false; error = "Səs yüklənmədi: ${it.localizedMessage}" }
        }
    }
    var message by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var programSticker by remember { mutableStateOf<Uri?>(null) }
    var programStickerBusy by remember { mutableStateOf(false) }
    var currentProgramStickerUrl by remember { mutableStateOf("") }
    var currentProgramStickerMime by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("0 MB") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteProgramId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteProgramName by remember { mutableStateOf("") }
    var currentIcon by remember { mutableStateOf("") }
    var pickedImage by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var heroTitle by remember { mutableStateOf("TEXNO-TELEKOM") }
    var welcomeText by remember { mutableStateOf("✅TEXNO-TELEKOM RƏSMİ SAYTINA XOŞ GƏLMİSİNİZ...") }
    var infoTitle by remember { mutableStateOf("TEXNO-TELEKOM RƏSMİ SAYT MƏRKƏZİ") }
    var infoLine1 by remember { mutableStateOf("1️⃣ TƏTBİQLƏRİN PREMİUM VƏ PRO VERSİYALARI 👨‍💻") }
    var infoLine2 by remember { mutableStateOf("2️⃣ GOOGLE PLAY'DA OLMAYAN TƏTBİQLƏRİ") }
    var infoLine3 by remember { mutableStateOf("3️⃣ ŞİKAYƏT VƏ REKLAM ÜÇÜN ƏLAQƏ") }
    var whatsappPhone by remember { mutableStateOf("+994558053305") }
    var homeSignature by remember { mutableStateOf("MR_R3HB3R") }
    val programs = remember { mutableStateListOf<AppItem>() }
    val weeklyHitPrograms = remember { mutableStateListOf<AppItem>() }
    val games = remember { mutableStateListOf<AppItem>() }
    val favoritesPrefs = remember { context.getSharedPreferences("texno_favorites", Context.MODE_PRIVATE) }
    val favoriteIds = remember {
        mutableStateListOf<String>().apply {
            addAll(favoritesPrefs.getStringSet("favorite_ids", emptySet()) ?: emptySet())
        }
    }
    fun toggleFavorite(appId: String) {
        if (favoriteIds.contains(appId)) favoriteIds.remove(appId) else favoriteIds.add(appId)
        favoritesPrefs.edit().putStringSet("favorite_ids", favoriteIds.toSet()).apply()
    }
    LaunchedEffect(notificationTarget, programs.size, games.size) {
        val target = notificationTarget ?: return@LaunchedEffect
        if (target.second == "phone_info") {
            showPhoneInfo = true
            showPrograms = false
            showGames = false
            notificationProgramId = null
            onNotificationTargetHandled()
            return@LaunchedEffect
        }
        notificationProgramId = target.first
        if (target.second == "game") {
            showGames = true
            showPrograms = false
            if (games.any { it.id == target.first }) {
                kotlinx.coroutines.delay(350)
                notificationItemBringIntoView.bringIntoView()
                onNotificationTargetHandled()
            }
        } else {
            showPrograms = true
            showGames = false
            if (programs.any { it.id == target.first }) {
                kotlinx.coroutines.delay(350)
                notificationItemBringIntoView.bringIntoView()
                onNotificationTargetHandled()
            }
        }
    }
    var showGameForm by remember { mutableStateOf(false) }
    var gameName by remember { mutableStateOf("") }
    var gameVersion by remember { mutableStateOf("") }
    var gameDescription by remember { mutableStateOf("") }
    var gameLink by remember { mutableStateOf("") }
    var gameImage by remember { mutableStateOf<Uri?>(null) }
    var gameImageUrl by remember { mutableStateOf("") }
    var editingGameId by remember { mutableStateOf<String?>(null) }
    val posts = remember { mutableStateListOf<ChannelPost>() }
    val phoneInfos = remember { mutableStateListOf<PhoneInfo>() }
    var newPhoneInfoNotice by remember { mutableStateOf(false) }
    var newProgramNotice by remember { mutableStateOf(false) }
    var appNoticeItems by remember { mutableStateOf(listOf<Pair<String, Pair<Triple<String, String, Long>, String>>>()) }
    var noticeFilter by remember { mutableStateOf("all") }
    var showClearUpdatesConfirm by remember { mutableStateOf(false) }
    var clearingUpdates by remember { mutableStateOf(false) }
    var latestAdminFeedbackAt by remember { mutableLongStateOf(0L) }
    var showAdminFeedbackNotice by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postVersion by remember { mutableStateOf("") }
    var postVip by remember { mutableStateOf(false) }
    var postDescription by remember { mutableStateOf("") }
    var postFeatures by remember { mutableStateOf("") }
    var postDownloadUrl by remember { mutableStateOf("") }
    var postFileSize by remember { mutableStateOf("") }
    var postAndroid by remember { mutableStateOf("Android 5.0+") }
    var postChannelUrl by remember { mutableStateOf("") }
    var editingPostId by remember { mutableStateOf<String?>(null) }
    var pickedPostApk by remember { mutableStateOf<Uri?>(null) }
    var pickedPostApkName by remember { mutableStateOf("") }
    var postBusy by remember { mutableStateOf(false) }


    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedPostApk = uri
        pickedPostApkName = if (uri == null) "" else {
            var displayName = "APK faylı seçildi"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) displayName = cursor.getString(index) ?: displayName
            }
            displayName
        }
        if (uri != null) message = "APK seçildi: $pickedPostApkName"
    }

    val phoneInfoImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        phoneInfoImage = uri
        if (uri != null) message = "TEXNO-MƏLUMAT şəkli seçildi."
    }

    val phoneStickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> phoneInfoSticker = uri; if (uri != null) message = "TEXNO-MƏLUMAT stikeri seçildi." }
    val programStickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> programSticker = uri; if (uri != null) message = "Proqram stikeri seçildi." }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedImage = uri
        if (uri != null) message = "Şəkil qalereyadan seçildi."
    }
    val gameImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        gameImage = uri
        if (uri != null) message = "Oyun şəkli seçildi."
    }

    DisposableEffect(db, loggedIn) {
        if (!loggedIn) return@DisposableEffect onDispose { }
        val feedbackNoticeListener = db.collection("admin_feedback")
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, e ->
                if (e == null && snap != null) {
                    val latest = snap.documents.firstOrNull()?.getTimestamp("created_at")?.toDate()?.time ?: 0L
                    latestAdminFeedbackAt = latest
                    val seen = context.getSharedPreferences("texno_admin_notices", Context.MODE_PRIVATE)
                        .getLong("last_feedback_seen_at", 0L)
                    showAdminFeedbackNotice = latest > seen && latest > 0L
                }
            }
        onDispose { feedbackNoticeListener.remove() }
    }

    DisposableEffect(db) {
        val noticeListener = db.collection("app_notifications").orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, e ->
                if (e == null && snap != null) {
                    appNoticeItems = snap.documents.map { d ->
                        val title = d.getString("title") ?: "Yeni yenilik"
                        val body = d.getString("body") ?: title
                        val time = d.getTimestamp("created_at")?.toDate()?.time ?: 0L
                        d.id to (Triple(title, body, time) to (d.getString("type") ?: d.getString("content_type") ?: "other"))
                    }
                }
            }
        onDispose { noticeListener.remove() }
    }

    DisposableEffect(db) {
        val phoneListener = db.collection("phone_information")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    error = "TEXNO-MƏLUMAT yüklənmədi: ${e.localizedMessage}"
                } else if (snap != null) {
                    phoneInfos.clear()
                    val sortedDocs = snap.documents.sortedByDescending { d ->
                        d.getTimestamp("created_at")?.toDate()?.time ?: 0L
                    }
                    sortedDocs.forEach { d ->
                        phoneInfos.add(
                            PhoneInfo(
                                d.id,
                                d.getString("title") ?: d.getString("name") ?: "",
                                d.getString("body") ?: d.getString("description") ?: d.getString("content") ?: "",
                                d.getString("image_url") ?: d.getString("imageUrl") ?: d.getString("photo_url") ?: "",
                                d.getString("sticker_url") ?: "", d.getString("sticker_mime") ?: "",
                                d.getTimestamp("created_at")?.toDate()?.time ?: 0L
                            )
                        )
                    }
                }
            }
        onDispose { phoneListener.remove() }
    }

    DisposableEffect(db) {
        val listener = db.collection("programs").addSnapshotListener { snap, e ->
            if (e != null) error = "Firebase xətası: ${e.localizedMessage}"
            else if (snap != null) {
                programs.clear()
                weeklyHitPrograms.clear()
                games.clear()
                posts.clear()
                val weeklyCutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
                snap.documents
                    .filter { d ->
                        d.getString("content_type") != "channel_post" &&
                            d.getString("content_type") != "game" &&
                            (d.getBoolean("is_active") ?: true) &&
                            (d.getTimestamp("created_at")?.toDate()?.time ?: 0L) >= weeklyCutoff
                    }
                    .sortedByDescending { it.getTimestamp("created_at")?.toDate()?.time ?: 0L }
                    .forEach { d ->
                        weeklyHitPrograms.add(AppItem(
                            d.id, d.getString("name") ?: "", d.getString("category") ?: "",
                            d.getString("version") ?: "", d.getString("description") ?: "",
                            d.getString("download_url") ?: "", d.getString("icon_url") ?: "",
                            d.getString("sticker_url") ?: "", d.getString("sticker_mime") ?: "",
                            d.getBoolean("is_active") ?: true, d.getString("file_size") ?: "0 MB"
                        ))
                    }
                snap.documents.filter { it.getString("content_type") == "channel_post" }.forEach { d ->
                    posts.add(ChannelPost(d.id, d.getString("title") ?: "", d.getString("version") ?: "",
                        d.getBoolean("is_vip") ?: false, d.getString("description") ?: "",
                        d.getString("features") ?: "", d.getString("download_url") ?: "",
                        d.getString("file_size") ?: "", d.getString("android_requirement") ?: "",
                        d.getString("channel_url") ?: ""))
                }
                snap.documents.filter { it.getString("content_type") == "game" }.forEach { d ->
                    games.add(AppItem(d.id, d.getString("name") ?: "", "OYUN", d.getString("version") ?: "", d.getString("description") ?: "", d.getString("download_url") ?: "", d.getString("icon_url") ?: "", "", "", d.getBoolean("is_active") ?: true, d.getString("file_size") ?: ""))
                }
                snap.documents.filter { it.getString("content_type") != "channel_post" && it.getString("content_type") != "game" }.forEach { d ->
                    programs.add(AppItem(
                        d.id, d.getString("name") ?: "", d.getString("category") ?: "",
                        d.getString("version") ?: "", d.getString("description") ?: "",
                        d.getString("download_url") ?: "", d.getString("icon_url") ?: "",
                        d.getString("sticker_url") ?: "", d.getString("sticker_mime") ?: "",
                        d.getBoolean("is_active") ?: true, d.getString("file_size") ?: "0 MB"
                    ))
                }
            }
        }
        onDispose { listener.remove() }
    }

    DisposableEffect(db) {
        val registration = db.collection("site_settings").document("content")
            .addSnapshotListener { snapshot, e ->
                if (e == null && snapshot != null && snapshot.exists()) {
                    heroTitle = snapshot.getString("heroTitle") ?: heroTitle
                    welcomeText = snapshot.getString("welcome") ?: welcomeText
                    infoTitle = snapshot.getString("infoTitle") ?: infoTitle
                    infoLine1 = snapshot.getString("line1") ?: infoLine1
                    infoLine2 = snapshot.getString("line2") ?: infoLine2
                    infoLine3 = snapshot.getString("line3") ?: infoLine3
                    whatsappPhone = snapshot.getString("phone") ?: whatsappPhone
                    homeSignature = snapshot.getString("home_signature") ?: homeSignature
                    designBgHex = snapshot.getString("design_background") ?: designBgHex
                    designPanelHex = snapshot.getString("design_panel") ?: designPanelHex
                    designAccentHex = snapshot.getString("design_accent") ?: designAccentHex
                    designGoldCards = snapshot.getBoolean("design_gold_cards") ?: true
                }
            }
        onDispose { registration.remove() }
    }

    fun saveLiveDesign() {
        val values = hashMapOf<String, Any>(
            "design_background" to designBgHex.trim(),
            "design_panel" to designPanelHex.trim(),
            "design_accent" to designAccentHex.trim(),
            "design_gold_cards" to designGoldCards,
            "updated_at" to FieldValue.serverTimestamp()
        )
        db.collection("site_settings").document("content").set(values, SetOptions.merge())
            .addOnSuccessListener { message = "Dizayn bütün istifadəçilər üçün yeniləndi." }
            .addOnFailureListener { error = "Dizayn yadda saxlanmadı: ${it.localizedMessage}" }
    }

    var phoneInfoStickerUrlPending by remember { mutableStateOf("") }
    var phoneInfoStickerMimePending by remember { mutableStateOf("") }

    fun publishPhoneInfo(imageUrl: String) {
        val values = hashMapOf<String, Any>(
            "title" to phoneInfoTitle.trim().ifBlank { "TEXNO-MƏLUMAT" },
            "body" to phoneInfoBody.trim(),
            "image_url" to imageUrl,
            "sticker_url" to phoneInfoStickerUrlPending,
            "sticker_mime" to phoneInfoStickerMimePending,
            "created_at" to FieldValue.serverTimestamp()
        )
        db.collection("phone_information").add(values)
            .addOnSuccessListener {
                phoneInfoTitle = ""
                phoneInfoBody = ""
                phoneInfoImage = null
                phoneInfoSticker = null
                phoneInfoStickerUrlPending = ""
                phoneInfoStickerMimePending = ""
                phoneInfoBusy = false
                newPhoneInfoNotice = true
                db.collection("app_notifications")
                    .add(hashMapOf("type" to "phone_info", "title" to "Yeni TEXNO-MƏLUMAT paylaşımı", "body" to "Yeni məlumat paylaşıldı. Tətbiqə daxil olub baxın.", "created_at" to FieldValue.serverTimestamp()))
                    .addOnSuccessListener { triggerInstantPush() }
                message = "TEXNO-MƏLUMAT bölməsində paylaşıldı."
            }
            .addOnFailureListener {
                phoneInfoBusy = false
                error = "Paylaşmaq alınmadı: ${it.localizedMessage}"
            }
    }

    fun savePhoneInfo() {
        if (phoneInfoBusy) return
        if (phoneInfoBody.isBlank() && phoneInfoImage == null && phoneInfoSticker == null) {
            error = "Məlumat yazın və ya şəkil/stiker əlavə edin."
            return
        }
        phoneInfoBusy = true
        error = ""
        val selectedImage = phoneInfoImage
        val selectedSticker = phoneInfoSticker
        fun uploadStickerThenPublish(imageUrl: String) {
            if (selectedSticker == null) {
                phoneInfoStickerUrlPending = ""
                phoneInfoStickerMimePending = ""
                publishPhoneInfo(imageUrl)
            } else {
                message = "Stiker yüklənir..."
                uploadStickerMedia(context, selectedSticker) { stickerResult ->
                    stickerResult.onSuccess { media ->
                        phoneInfoStickerUrlPending = media.url
                        phoneInfoStickerMimePending = media.mimeType
                        publishPhoneInfo(imageUrl)
                    }.onFailure {
                        phoneInfoBusy = false
                        error = "Stiker yüklənmədi: ${it.localizedMessage}"
                    }
                }
            }
        }
        if (selectedImage == null) {
            uploadStickerThenPublish("")
        } else {
            message = "Şəkil yüklənir..."
            uploadPhoneImage(context, selectedImage) { result ->
                result.onSuccess { url -> uploadStickerThenPublish(url) }
                    .onFailure {
                        phoneInfoBusy = false
                        error = "Şəkil yüklənmədi: ${it.localizedMessage}"
                    }
            }
        }
    }

    fun saveSiteTexts() {
        val values = hashMapOf<String, Any>(
            "heroTitle" to heroTitle.trim(),
            "welcome" to welcomeText.trim(),
            "infoTitle" to infoTitle.trim(),
            "line1" to infoLine1.trim(),
            "line2" to infoLine2.trim(),
            "line3" to infoLine3.trim(),
            "phone" to whatsappPhone.trim(),
            "home_signature" to homeSignature.trim(),
            "updated_at" to FieldValue.serverTimestamp()
        )
        db.collection("site_settings").document("content").set(values, SetOptions.merge())
            .addOnSuccessListener { message = "Sayt yazıları yadda saxlanıldı." }
            .addOnFailureListener { error = "Yadda saxlamaq alınmadı: ${it.localizedMessage}" }
    }

    fun clearAllAppUpdates() {
        if (!isAuthorizedDevice || !loggedIn || clearingUpdates) return
        clearingUpdates = true
        db.collection("app_notifications").get()
            .addOnSuccessListener { snapshot ->
                val contentTypes = setOf("program", "game", "phone_info", "program_added", "program_updated", "game_added", "game_updated", "phone_info_added", "phone_info_updated")
                val contentDocuments = snapshot.documents.filter { (it.getString("type") ?: it.getString("content_type")) in contentTypes }
                val batches = contentDocuments.chunked(450).map { documents ->
                    db.batch().also { batch -> documents.forEach { batch.delete(it.reference) } }
                }
                if (batches.isEmpty()) {
                    clearingUpdates = false
                    showClearUpdatesConfirm = false
                    message = "Yeniliklər bölməsi artıq təmizdir."
                } else {
                    com.google.android.gms.tasks.Tasks.whenAll(batches.map { it.commit() })
                        .addOnSuccessListener {
                            clearingUpdates = false
                            showClearUpdatesConfirm = false
                            message = "Profil bölməsinin köhnə paylaşım bildirişləri silindi."
                        }
                        .addOnFailureListener {
                            clearingUpdates = false
                            error = "Təmizləmək alınmadı: ${it.localizedMessage}"
                        }
                }
            }
            .addOnFailureListener {
                clearingUpdates = false
                error = "Yeniliklər oxunmadı: ${it.localizedMessage}"
            }
    }

    fun clearForm() {
        name = ""; category = ""; version = ""; description = ""; link = ""
        fileSize = "0 MB"; editingId = null; currentIcon = ""; pickedImage = null; programSticker = null; currentProgramStickerUrl = ""; currentProgramStickerMime = ""
    }

    fun clearGameForm() { gameName = ""; gameVersion = ""; gameDescription = ""; gameLink = ""; gameImage = null; gameImageUrl = ""; editingGameId = null; showGameForm = false }
    fun clearPostForm() {
        postTitle = ""; postVersion = ""; postVip = false; postDescription = ""
        postFeatures = ""; postDownloadUrl = ""; postFileSize = ""
        postAndroid = "Android 5.0+"; postChannelUrl = ""; editingPostId = null
        pickedPostApk = null; pickedPostApkName = ""; postBusy = false
    }

    fun persistPost(downloadUrl: String) {
        val values = hashMapOf<String, Any>(
            "title" to postTitle.trim(), "version" to postVersion.trim(), "is_vip" to postVip,
            "description" to postDescription.trim(), "features" to postFeatures.trim(),
            "download_url" to downloadUrl.trim(), "file_size" to postFileSize.trim(),
            "android_requirement" to postAndroid.trim(), "channel_url" to postChannelUrl.trim(),
            "updated_at" to FieldValue.serverTimestamp(), "content_type" to "channel_post"
        )
        val id = editingPostId
        if (id == null) values["created_at"] = FieldValue.serverTimestamp()
        val task = if (id == null) db.collection("programs").add(values)
                   else db.collection("programs").document(id).set(values, SetOptions.merge())
        task.addOnSuccessListener {
            message = if (id == null) "Paylaşım əlavə edildi." else "Paylaşım yeniləndi."
            clearPostForm()
        }.addOnFailureListener { error = "Paylaşım saxlanmadı: ${it.localizedMessage}" }
            .addOnCompleteListener { postBusy = false }
    }

    fun savePost() {
        if (postBusy) return
        if (postTitle.isBlank()) { error = "Paylaşımın adı mütləqdir."; return }
        if (postDownloadUrl.isNotBlank() && !postDownloadUrl.startsWith("https://") && !postDownloadUrl.startsWith("http://")) {
            error = "APK linki http:// və ya https:// ilə başlamalıdır."; return
        }
        val selectedApk = pickedPostApk
        if (selectedApk != null) {
            postBusy = true
            message = "APK Cloudinary-yə yüklənir..."
            uploadApk(context, selectedApk) { result ->
                result.onSuccess { (url, bytes) ->
                    postDownloadUrl = url
                    if (postFileSize.isBlank()) postFileSize = String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
                    persistPost(url)
                }.onFailure {
                    postBusy = false
                    error = "APK yüklənmədi: ${it.localizedMessage}"
                }
            }
        } else {
            postBusy = true
            persistPost(postDownloadUrl)
        }
    }

    fun writeProgram(iconUrl: String) {
        val values = hashMapOf<String, Any>(
            "name" to name.trim(), "category" to category.trim(), "version" to version.trim(),
            "description" to description.trim(), "download_url" to link.trim(),
            "icon_url" to iconUrl, "sticker_url" to currentProgramStickerUrl, "sticker_mime" to currentProgramStickerMime, "is_active" to true, "file_size" to fileSize.trim(),
            "updated_at" to FieldValue.serverTimestamp()
        )
        val id = editingId
        if (id == null) values["created_at"] = FieldValue.serverTimestamp()
        val programDocument = if (id == null) db.collection("programs").document() else db.collection("programs").document(id)
        programDocument.set(values, SetOptions.merge()).addOnSuccessListener {
            newProgramNotice = true
            db.collection("app_notifications").add(hashMapOf(
                "type" to "program",
                "title" to if (id == null) "Yeni proqram əlavə edildi" else "Proqram yeniləndi",
                "body" to if (id == null) "${name.trim()} əlavə edildi." else "${name.trim()} yeniləndi. Yeni versiya: ${version.trim().ifBlank { "mövcuddur" }}",
                "created_at" to FieldValue.serverTimestamp(),
                "program_id" to programDocument.id,
                "content_type" to "program"
            )).addOnSuccessListener { triggerInstantPush() }
            message = if (id == null) "Proqram əlavə edildi." else "Proqram yeniləndi."
            clearForm(); busy = false
        }.addOnFailureListener {
            error = "Yadda saxlamaq alınmadı: ${it.localizedMessage}"; busy = false
        }
    }

    fun saveGame() {
        if (gameName.isBlank()) { error = "Oyunun adı mütləqdir."; return }
        if (gameLink.isNotBlank() && !gameLink.startsWith("https://") && !gameLink.startsWith("http://")) { error = "APK linki http:// və ya https:// ilə başlamalıdır."; return }
        busy = true; error = ""; message = ""
        fun persistGame(icon: String) {
            val values = hashMapOf<String, Any>("name" to gameName.trim(), "version" to gameVersion.trim(), "description" to gameDescription.trim(), "download_url" to gameLink.trim(), "icon_url" to icon, "is_active" to true, "content_type" to "game", "updated_at" to FieldValue.serverTimestamp())
            val id = editingGameId
            if (id == null) values["created_at"] = FieldValue.serverTimestamp()
            val gameDocument = if (id == null) db.collection("programs").document() else db.collection("programs").document(id)
            gameDocument.set(values, SetOptions.merge()).addOnSuccessListener {
                newProgramNotice = true
                db.collection("app_notifications").add(hashMapOf(
                    "type" to "game",
                    "title" to if (id == null) "Yeni oyun əlavə edildi" else "Oyun yeniləndi",
                    "body" to if (id == null) "${gameName.trim()} oyunu əlavə edildi." else "${gameName.trim()} oyunu yeniləndi. Yeni versiya: ${gameVersion.trim().ifBlank { "mövcuddur" }}",
                    "created_at" to FieldValue.serverTimestamp(),
                    "program_id" to gameDocument.id,
                    "content_type" to "game"
                )).addOnSuccessListener { triggerInstantPush() }
                message = if (id == null) "Oyun əlavə edildi." else "Oyun yeniləndi."
                clearGameForm(); busy = false
            }.addOnFailureListener { error = "Oyun yadda saxlanmadı: ${it.localizedMessage}"; busy = false }
        }
        val uri = gameImage
        if (uri == null) { persistGame(gameImageUrl); return }
        try {
            val source = context.contentResolver.openInputStream(uri) ?: throw Exception("Şəkil oxunmadı")
            val original = source.use { BitmapFactory.decodeStream(it) } ?: throw Exception("Şəkil formatı dəstəklənmir")
            val scaled = Bitmap.createScaledBitmap(original, 256, 256, true)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, output)
            val encoded = "b64:" + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            if (scaled !== original) scaled.recycle()
            original.recycle()
            persistGame(encoded)
        } catch (e: Exception) { error = "Oyun şəkli hazırlana bilmədi: ${e.localizedMessage}"; busy = false }
    }
    fun saveProgram() {
        if (name.isBlank() || category.isBlank()) { error = "Ad və kateqoriya mütləqdir."; return }
        if (link.isNotBlank() && !link.startsWith("https://") && !link.startsWith("http://")) {
            error = "Yükləmə linki http:// və ya https:// ilə başlamalıdır."; return
        }
        error = ""; message = ""; busy = true
        fun uploadStickerThenWrite(iconValue: String) {
            val selectedSticker = programSticker
            if (selectedSticker == null) {
                writeProgram(iconValue)
            } else {
                message = "Proqram stikeri yüklənir..."
                uploadStickerMedia(context, selectedSticker) { result ->
                    result.onSuccess { media ->
                        currentProgramStickerUrl = media.url
                        currentProgramStickerMime = media.mimeType
                        writeProgram(iconValue)
                    }.onFailure {
                        busy = false
                        error = "Stiker yüklənmədi: ${it.localizedMessage}"
                    }
                }
            }
        }
        val uri = pickedImage
        if (uri == null) {
            uploadStickerThenWrite(currentIcon)
            return
        }
        try {
            val source = context.contentResolver.openInputStream(uri) ?: throw Exception("Şəkil oxunmadı")
            val original = source.use { BitmapFactory.decodeStream(it) } ?: throw Exception("Şəkil formatı dəstəklənmir")
            val scaled = Bitmap.createScaledBitmap(original, 256, 256, true)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, output)
            val encoded = "b64:" + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            if (scaled !== original) scaled.recycle()
            original.recycle()
            uploadStickerThenWrite(encoded)
        } catch (e: Exception) {
            error = "Şəkli hazırlamaq alınmadı: " + (e.localizedMessage ?: "naməlum xəta")
            busy = false
        }
    }

    val shown = programs.filter { (it.isActive || loggedIn) && "${it.name} ${it.category} ${it.description}".contains(query, true) }


    MaterialTheme(
        colorScheme = darkColorScheme(
            background = liveBg,
            surface = livePanel,
            surfaceVariant = Color(0xFF17233A),
            primary = Color(0xFF168BFF),
            secondary = Color(0xFF62B7FF),
            tertiary = Color(0xFF168BFF),
            onPrimary = Color.White,
            onSecondary = Color(0xFFFFFFFF),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)
        )
    ) {
        if (showSettings) {
            val settingItems = listOf(
                Triple("👤", "Hesab məlumatları", "Hesab adını, e-poçt ünvanını və giriş məlumatlarını göstərir."),
                Triple("🔔", "Bildirişlər", "Tətbiq yenilikləri və vacib elanlar barədə bildirişlərin idarəsini izah edir."),
                Triple("🎨", "Görünüş", "Tətbiqin rəngləri və ekran görünüşü haqqında məlumat verir."),
                Triple("📱", "Cihaz məlumatları", "Telefon modeli, Android versiyası və tətbiq versiyasını göstərir."),
            )
            val selectedSetting = settingItems.firstOrNull { it.second == settingsSelected }
            Column(
                Modifier.fillMaxSize().background(liveBg).statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 34.sp,
                        modifier = Modifier.clickable {
                            if (settingsSelected != null) settingsSelected = null else showSettings = false
                        }.padding(end = 14.dp))
                    Text(if (selectedSetting == null) "AYARLAR" else selectedSetting.second.uppercase(),
                        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
                if (selectedSetting == null) {
                    Text("Tətbiqin seçimləri və hesab məlumatları", color = Color(0xFFCBD6EB), fontSize = 14.sp)
                    settingItems.forEach { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                settingsSelected = item.second
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(item.first, fontSize = 27.sp, modifier = Modifier.width(48.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.second, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(item.third, color = Color(0xFFB8C5DC), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2)
                                }
                                Text("›", color = Color(0xFFFFD36A), fontSize = 30.sp)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(selectedSetting.first, fontSize = 38.sp)
                            Text(selectedSetting.second, color = Color(0xFFFFD36A), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(selectedSetting.third, color = Color.White, fontSize = 15.sp, lineHeight = 23.sp)
                            when (selectedSetting.second) {
                                "Hesab məlumatları" -> {
                                    val account = FirebaseAuth.getInstance().currentUser
                                    var editableName by remember(account?.uid) {
                                        mutableStateOf(
                                            account?.displayName
                                                ?: context.getSharedPreferences("texno_telekom", Context.MODE_PRIVATE)
                                                    .getString("profile_display_name", "TEXNO-TELEKOM istifadəçisi")
                                                ?: "TEXNO-TELEKOM istifadəçisi"
                                        )
                                    }
                                    Text("İstifadəçi adı", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = editableName,
                                        onValueChange = { editableName = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("İstifadəçi adını yazın") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFFFFD36A),
                                            unfocusedBorderColor = Color.Gray,
                                            cursorColor = Color(0xFFFFD36A)
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            val newName = editableName.trim()
                                            if (newName.isNotEmpty()) {
                                                if (account != null) {
                                                    val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                        .setDisplayName(newName)
                                                        .build()
                                                    account.updateProfile(profile).addOnCompleteListener { task ->
                                                        if (task.isSuccessful) {
                                                            context.getSharedPreferences("texno_telekom", Context.MODE_PRIVATE)
                                                                .edit().putString("profile_display_name", newName).apply()
                                                            android.widget.Toast.makeText(context, "İstifadəçi adı yeniləndi", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Adı dəyişmək mümkün olmadı", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } else {
                                                    context.getSharedPreferences("texno_telekom", Context.MODE_PRIVATE)
                                                        .edit().putString("profile_display_name", newName).apply()
                                                    android.widget.Toast.makeText(context, "İstifadəçi adı yadda saxlanıldı", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                android.widget.Toast.makeText(context, "İstifadəçi adı boş ola bilməz", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD36A))
                                    ) {
                                        Text("Yadda saxla", color = Color(0xFF101827), fontWeight = FontWeight.Bold)
                                    }
                                }
                                "Cihaz məlumatları" -> {
                                    val device = Build.MODEL ?: "Naməlum"
                                    val version = Build.VERSION.RELEASE ?: "Naməlum"
                                    val appVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Naməlum" } catch (_: Exception) { "Naməlum" }
                                    Text("Telefon modeli: $device", color = Color.White, fontSize = 14.sp)
                                    Text("Android versiyası: $version", color = Color.White, fontSize = 14.sp)
                                    Text("Tətbiq versiyası: $appVersion", color = Color.White, fontSize = 14.sp)
                                }
                                "Bildirişlər" -> Text("Bildiriş icazəsini telefonunuzun Ayarlar → Tətbiqlər → TEXNO-TELEKOM → Bildirişlər bölməsindən idarə edə bilərsiniz.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                                "Görünüş" -> Text("TEXNO-TELEKOM hazırda tünd rəngli interfeysdən istifadə edir. Bu görünüş rahat oxunuş üçün hazırlanıb.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
        } else if (showSecurity) {
            Column(
                Modifier.fillMaxSize().background(liveBg).statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 34.sp,
                        modifier = Modifier.clickable { showSecurity = false }.padding(end = 14.dp))
                    Text("TƏHLÜKƏSİZLİK MƏLUMATI", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TexnoTelekomLogo(size = 104.dp)
                        Text("TEXNO-TELEKOM", color = Color(0xFFFFD36A), fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("TƏHLÜKƏSİZLİK VƏ MƏXFİLİK MƏLUMATI", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Xoş gəlmisiniz! TEXNO-TELEKOM istifadəçilərinə rahat və təhlükəsiz rəqəmsal mühit təqdim etməyi hədəfləyir. Bu bölmədə platformadan istifadə zamanı diqqət edilməli təhlükəsizlik qaydaları ilə tanış ola bilərsiniz.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        HorizontalDivider(color = Color(0xFF34445F))
                        Text("🔒 1. Məxfilik siyasəti", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Şəxsi məlumatlarınızı qoruyun. Şifrələrinizi və hesabınıza aid məxfi məlumatları başqa şəxslərlə paylaşmayın.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("👤 2. Hesab təhlükəsizliyi", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Hesabınızı qorumaq üçün güclü şifrələrdən istifadə edin və tanımadığınız şəxslərə hesabınıza giriş imkanı verməyin.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("📥 3. Proqramların təhlükəsiz yüklənməsi", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Proqramları etibarlı mənbələrdən yükləyin. Quraşdırmadan əvvəl tətbiqin tələb etdiyi icazələri yoxlayın.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("⚠️ 4. Təhlükəsizlik xəbərdarlıqları", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Şübhəli keçidlərə daxil olmayın, naməlum faylları açmayın və şəxsi məlumatlarınızı şübhəli saytlara daxil etməyin.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("🛡️ 5. İstifadəçi hüquqları", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Hər bir istifadəçi şəxsi məlumatlarının məxfiliyinə hörmət edilməsini gözləmək hüququna malikdir.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("📞 6. Təhlükəsizlik dəstəyi", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Tətbiqdən istifadə zamanı təhlükəsizlik problemi və ya şübhəli fəaliyyətlə qarşılaşsanız, TEXNO-TELEKOM admini ilə əlaqə saxlayın.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("📋 7. İstifadə qaydaları", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Platformadan məsuliyyətlə istifadə edin. Digər istifadəçilərə hörmətlə yanaşın və zərərli məzmun paylaşmayın.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        Text("🔄 8. Yeniləmələr", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Tətbiqin yeni versiyalarını izləyin və mümkün olduqda ən son versiyadan istifadə edin.", color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
                        HorizontalDivider(color = Color(0xFF34445F))
                        Text("TEXNO-TELEKOM — Texnologiya, etibar və təhlükəsizlik bir arada!", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Black, lineHeight = 23.sp)
                        Text("© TEXNO-TELEKOM — Bütün hüquqlar qorunur.", color = Color(0xFFCBD6EB), fontSize = 12.sp)
                    }
                }
            }
        } else if (showAbout) {
            Column(
                Modifier.fillMaxSize()
                    .background(liveBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 34.sp,
                        modifier = Modifier.clickable { showAbout = false }.padding(end = 14.dp))
                    Text("TƏTBİQ HAQQINDA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFD36A)),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "NOYABR",
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE0A92F)).padding(horizontal = 2.dp, vertical = 6.dp),
                                    color = Color(0xFF172033),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    "25",
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    color = Color(0xFF172033),
                                    fontSize = 27.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Yaranma tarixi", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("25.11.2025", color = Color.White, fontSize = 14.sp)
                            }
                        }
                        TexnoTelekomLogo(size = 116.dp)
                        Text("TEXNOLOGİYANIN ÜNVANI!", color = Color(0xFFFFD36A), fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("Xoş gəlmisiniz! TEXNO-TELEKOM müasir texnologiyaları, rəqəmsal yenilikləri və faydalı tətbiqləri bir araya gətirən xüsusi platformadır.", color = Color.White, fontSize = 15.sp, lineHeight = 23.sp)
                        Text("Məqsədimiz istifadəçilərə texnologiya dünyasını daha yaxından tanıtmaq, faydalı proqramları və maraqlı yenilikləri bir məkanda təqdim etməkdir.", color = Color.White, fontSize = 15.sp, lineHeight = 23.sp)
                        HorizontalDivider(color = Color(0xFF34445F))
                        Text("🚀 PLATFORMAMIZIN İMKANLARI", color = Color(0xFFFFD36A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("📱 Proqramlar – Android üçün faydalı və maraqlı tətbiqlər.\n\n🎮 Oyunlar – Müxtəlif janrlarda əyləncəli mobil oyunlar.\n\n💡 TEXNO-MƏLUMAT – Telefonlar, texnologiyalar və rəqəmsal yeniliklər haqqında məlumatlar.\n\n⭐ Sevimlilər – Bəyəndiyiniz proqramları və oyunları yadda saxlayın.\n\n👥 TEXNO-TELEKOM QRUPU – Digər istifadəçilərlə ünsiyyət qurun və fikirlərinizi bölüşün.\n\n📢 Yeniliklər – Platformadakı yeniliklərdən və paylaşımlardan xəbərdar olun.", color = Color.White, fontSize = 14.sp, lineHeight = 23.sp)
                        HorizontalDivider(color = Color(0xFF34445F))
                        Text("🎯 MİSSİYAMIZ", color = Color(0xFFFFD36A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Texnologiyanı hər kəs üçün daha əlçatan etmək, istifadəçilərə rahat və müasir platforma təqdim etmək, rəqəmsal dünyadakı yenilikləri daim izləmək.", color = Color.White, fontSize = 15.sp, lineHeight = 23.sp)
                        HorizontalDivider(color = Color(0xFF34445F))
                        Text("💙 DƏYƏRİMİZ", color = Color(0xFFFFD36A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Bizim üçün hər bir istifadəçi önəmlidir. İstifadəçilərin rahatlığı, məmnuniyyəti və platformanın davamlı inkişafı əsas prioritetlərimizdəndir.", color = Color.White, fontSize = 15.sp, lineHeight = 23.sp)
                        Text("✨ GƏLƏCƏYİ BİRLİKDƏ QURAQ!", color = Color(0xFFFFD36A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("TEXNO-TELEKOM ilə texnologiya dünyasını kəşf edin, yeniliklərdən geri qalmayın və rəqəmsal imkanlardan faydalanın!", color = Color.White, fontSize = 15.sp, lineHeight = 23.sp)
                        Text("TEXNO-TELEKOM — Texnologiya hər kəs üçün!", color = Color(0xFFFFD36A), fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("© 2026 TEXNO-TELEKOM\nBütün hüquqlar qorunur.", color = Color(0xFFCBD6EB), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        } else if (showGroupChat) {
            GroupChatScreen(
                backgroundColor = liveBg,
                onClose = { showGroupChat = false },
                onOpenBubbleAdmin = {
                    if (loggedIn && isAuthorizedDevice) {
                        showGroupChat = false
                        showGroupManagementAdmin = true
                    }
                },
                onNicknameChanged = { enteredName ->
                    profileNickname = enteredName
                    groupNickname = enteredName
                }
            )
        } else if (showGroupManagementAdmin && loggedIn && isAuthorizedDevice) {            Column(
                Modifier.fillMaxSize()
                    .background(liveBg)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 32.sp,
                        modifier = Modifier.clickable { showGroupManagementAdmin = false }.padding(end = 12.dp))
                    Text("QRUP RƏHBƏRLİYİ", color = Color(0xFFFFD36A), fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                GroupManagementAdminPanel()
            }
        } else if (showFeedback) {
            Column(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFF030712),
                            0.12f to Color(0xFF071329),
                            0.23f to Color(0xFF081B38),
                            0.38f to Color(0xFF0B2550),
                            0.52f to Color(0xFF050B18),
                            0.66f to Color(0xFF081A35),
                            0.79f to Color(0xFF0A2450),
                            0.91f to Color(0xFF071329),
                            1.00f to Color(0xFF030712)
                        ),
                        start = Offset.Zero,
                        end = Offset(900f, 1500f)
                    )
                ).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.clickable { showFeedback = false }.padding(end = 14.dp))
                    Text("ADMİNLƏ-ƏLAQƏ", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1425)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MR_R3HB3R", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                            HorizontalDivider(color = Color.DarkGray)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("ADI", color = Color.LightGray, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text("АГШИН", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Color.DarkGray)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("CİNSİ", color = Color.LightGray, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text("Kişi", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Color.DarkGray)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("RÜTBƏSİ", color = Color.LightGray, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text("⚜️ PLATFORM SAHİBİ", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("MESAJLAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (loggedIn) TextButton(onClick = { showClearFeedbackConfirm = true }) {
                            Text("HAMISINI SİL", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showClearFeedbackConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearFeedbackConfirm = false },
                            title = { Text("Bütün mesajlar silinsin?") },
                            text = { Text("ADMİNLƏ-ƏLAQƏ bölməsindəki bütün mesaj və səs yazıları silinəcək. Bu əməliyyat geri qaytarılmır.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    db.collection("admin_feedback").get().addOnSuccessListener { snap ->
                                        val chunks = snap.documents.chunked(400)
                                        fun deleteFeedbackChunk(index: Int) {
                                            if (index >= chunks.size) {
                                                showClearFeedbackConfirm = false
                                                message = "Bütün mesajlar silindi."
                                                return
                                            }
                                            val batch = db.batch()
                                            chunks[index].forEach { batch.delete(it.reference) }
                                            batch.commit()
                                                .addOnSuccessListener { deleteFeedbackChunk(index + 1) }
                                                .addOnFailureListener { error = "Mesajlar silinmədi: ${it.localizedMessage}" }
                                        }
                                        deleteFeedbackChunk(0)
                                    }.addOnFailureListener { error = "Mesajlar yüklənmədi: ${it.localizedMessage}" }
                                }) { Text("SİL", color = Color(0xFFD32F2F)) }
                            },
                            dismissButton = { TextButton(onClick = { showClearFeedbackConfirm = false }) { Text("LƏĞV ET") } }
                        )
                    }
                    if (feedbackItems.isEmpty()) Text("Hələ mesaj göndərilməyib.", color = Color.LightGray, fontSize = 13.sp)
                    else feedbackItems.reversed().forEach { item ->
                        val parts = item.split("|", limit = 5)
                        val sender = parts.getOrNull(0).orEmpty()
                        val body = parts.getOrNull(1).orEmpty()
                        val audio = parts.getOrNull(2).orEmpty()
                        val sentAt = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                        val feedbackId = parts.getOrNull(4).orEmpty()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (sender == groupNickname.trim()) Arrangement.End else Arrangement.Start) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth(0.92f)
                                    .border(3.dp, Color(0xFF35140F), RoundedCornerShape(24.dp))
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(sender, color = Color(0xFF6B3A20), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        if (loggedIn && feedbackId.isNotBlank()) IconButton(onClick = {
                                            db.collection("admin_feedback").document(feedbackId).delete()
                                                .addOnSuccessListener { message = "Mesaj silindi." }
                                                .addOnFailureListener { error = "Mesaj silinmədi: ${it.localizedMessage}" }
                                        }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Mesajı sil", tint = Color(0xFFD94A4A))
                                        }
                                    }
                                    if (body.isNotBlank()) Text(body, color = Color(0xFF171717), fontSize = 17.sp, lineHeight = 24.sp)
                                    if (audio.isNotBlank()) GroupVoiceMessagePlayer(audio, sentAt.takeIf { it > 0L } ?: System.currentTimeMillis())
                                    if (sentAt > 0L) Text(
                                        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sentAt)),
                                        color = Color(0xFF243650),
                                        fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
                if (feedbackRecording) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFFFFFFF), RoundedCornerShape(30.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            String.format(java.util.Locale.getDefault(), "%d:%02d", feedbackRecordingSeconds / 60, feedbackRecordingSeconds % 60),
                            color = Color(0xFF161616), fontSize = 21.sp, fontWeight = FontWeight.Bold
                        )
                        Row(Modifier.weight(1f).height(52.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            feedbackWaveform.forEachIndexed { index, height ->
                                Box(Modifier.padding(horizontal = 1.dp).width(3.dp).height((if (feedbackRecordingPaused) 7 else height).dp)
                                    .clip(RoundedCornerShape(3.dp)).background(if (feedbackRecordingPaused) Color.LightGray else Color(0xFF737B83)))
                            }
                        }
                        IconButton(
                            onClick = {
                                val rec = feedbackRecorder
                                if (rec != null) {
                                    try {
                                        if (!feedbackRecordingPaused) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                rec.pause()
                                                feedbackPauseStartedMs = SystemClock.elapsedRealtime()
                                                feedbackRecordingPaused = true
                                            }
                                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            rec.resume()
                                            feedbackPausedTotalMs += SystemClock.elapsedRealtime() - feedbackPauseStartedMs
                                            feedbackPauseStartedMs = 0L
                                            feedbackRecordingPaused = false
                                        }
                                    } catch (e: Exception) { error = "Səs yazısı dayandırıla bilmədi: ${e.localizedMessage}" }
                                }
                            },
                            modifier = Modifier.size(52.dp).background(Color(0xFFF2F2F2), CircleShape)
                        ) {
                            Text(
                                if (feedbackRecordingPaused) "▶" else "Ⅱ",
                                color = Color(0xFF202124),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = {
                                try { feedbackRecorder?.stop() } catch (_: Exception) { }
                                try { feedbackRecorder?.release() } catch (_: Exception) { }
                                feedbackRecorder = null
                                feedbackFile?.delete()
                                feedbackFile = null
                                feedbackRecording = false
                                feedbackRecordingPaused = false
                                feedbackRecordingSeconds = 0
                                feedbackWaveform.clear()
                            },
                            modifier = Modifier.size(52.dp).background(Color(0xFFFCE7EC), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Səs yazısını sil", tint = Color(0xFFE91E63), modifier = Modifier.size(25.dp))
                        }
                        IconButton(
                            onClick = { stopAndSendFeedbackVoice() },
                            enabled = !feedbackBusy,
                            modifier = Modifier.size(58.dp).background(Color(0xFF20A66A), CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Səsi göndər", tint = Color.White, modifier = Modifier.size(27.dp))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF4F0E8), RoundedCornerShape(30.dp)).padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        IconButton(onClick = { feedbackDraft += "🙂" }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = Color(0xFF60666B))
                        }
                        BasicTextField(
                            value = feedbackDraft,
                            onValueChange = { feedbackDraft = it },
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                            singleLine = true,
                            enabled = !feedbackBusy,
                            textStyle = TextStyle(color = Color(0xFF202124), fontSize = 16.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF159B68)),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (feedbackDraft.isEmpty()) Text("Mesaj", color = Color(0xFF777D80), fontSize = 16.sp)
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_GET_CONTENT).setType("*/*")) }
                            catch (_: Exception) { error = "Fayl seçimi açıla bilmədi." }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Fayl seç", tint = Color(0xFF60666B))
                        }
                        IconButton(onClick = {
                            try { context.startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
                            catch (_: Exception) { error = "Kamera açıla bilmədi." }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Kamera", tint = Color(0xFF60666B))
                        }
                        IconButton(
                            onClick = {
                                if (feedbackDraft.isNotBlank()) sendFeedbackText()
                                else feedbackAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = !feedbackBusy,
                            modifier = Modifier.size(48.dp).background(Color(0xFF20A66A), RoundedCornerShape(50))
                        ) {
                            if (feedbackDraft.isNotBlank()) Icon(Icons.Default.Send, contentDescription = "Göndər", tint = Color.White)
                            else Icon(Icons.Default.Mic, contentDescription = "Səs yaz", tint = Color.White)
                        }
                    }
                }

            }
        } else {
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFF030712),
                            0.12f to Color(0xFF071329),
                            0.23f to Color(0xFF081B38),
                            0.38f to Color(0xFF0B2550),
                            0.52f to Color(0xFF050B18),
                            0.66f to Color(0xFF081A35),
                            0.79f to Color(0xFF0A2450),
                            0.91f to Color(0xFF071329),
                            1.00f to Color(0xFF030712)
                        ),
                        start = Offset.Zero,
                        end = Offset(900f, 1500f)
                    )
                )
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF168BFF).copy(alpha = 0.24f), Color(0xFF168BFF).copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(size.width * 0.28f, size.height * 0.78f),
                            radius = size.maxDimension * 0.62f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF008DFF).copy(alpha = 0.22f), Color(0xFF0054C8).copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.86f, size.height * 0.42f),
                            radius = size.maxDimension * 0.58f
                        )
                    )
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color(0xFF36A9FF).copy(alpha = 0.12f), Color.Transparent),
                            start = Offset(0f, size.height * 0.84f),
                            end = Offset(size.width, size.height * 0.58f)
                        )
                    )
                }
                .statusBarsPadding()
        ) {
        val homeScrollState = rememberScrollState()
        BackHandler(enabled = selectedBottomTab == 3 && isAuthorizedDevice && adminEntry != null) {
            adminEntry = null
            clearForm()
            clearGameForm()
            clearPostForm()
        }
        var bottomBarVisible by remember { mutableStateOf(true) }
        LaunchedEffect(selectedBottomTab) {
            bottomBarVisible = true
            homeScrollState.scrollTo(0)
        }
        LaunchedEffect(homeScrollState, selectedBottomTab) {
            var previousScroll = homeScrollState.value
            snapshotFlow { homeScrollState.value }.collect { currentScroll ->
                if (selectedBottomTab == 0) {
                    when {
                        currentScroll <= 0 -> bottomBarVisible = true
                        currentScroll > previousScroll -> bottomBarVisible = false
                        currentScroll < previousScroll -> bottomBarVisible = true
                    }
                } else {
                    bottomBarVisible = true
                }
                previousScroll = currentScroll
            }
        }
        Column(Modifier.fillMaxSize()) {
            if (showPhoneInfoFeed) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState).padding(horizontal = 16.dp, vertical = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.clickable { showPhoneInfoFeed = false }.padding(end = 14.dp))
                        Text("TEXNO-MƏLUMAT", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    if (phoneInfos.isEmpty()) Text("Hələ TEXNO-MƏLUMAT paylaşılmayıb.", color = Color(0xFFCBD6EB), modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (pendingDeletePhoneInfoId != null && loggedIn && isAuthorizedDevice) {
                        AlertDialog(
                            onDismissRequest = { pendingDeletePhoneInfoId = null; pendingDeletePhoneInfoTitle = "" },
                            title = { Text("TEXNO-MƏLUMAT paylaşımını sil") },
                            text = { Text("\"$pendingDeletePhoneInfoTitle\" paylaşımını silmək istədiyinizə əminsiniz? Bu əməliyyat geri qaytarılmır.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    val deleteId = pendingDeletePhoneInfoId
                                    if (deleteId != null) {
                                        db.collection("phone_information").document(deleteId).delete()
                                            .addOnSuccessListener { message = "TEXNO-MƏLUMAT paylaşımı silindi." }
                                            .addOnFailureListener { error = "Paylaşım silinmədi: ${it.localizedMessage}" }
                                    }
                                    pendingDeletePhoneInfoId = null
                                    pendingDeletePhoneInfoTitle = ""
                                }) { Text("BƏLİ, SİL", color = Color(0xFFE05252)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingDeletePhoneInfoId = null; pendingDeletePhoneInfoTitle = "" }) { Text("LƏĞV ET") }
                            }
                        )
                    }
                    phoneInfos.filter { selectedPhoneInfoId == null || it.id == selectedPhoneInfoId }.forEach { info ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text(info.title, color = Color(0xFFFFD36A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                if (info.imageUrl.isNotBlank()) AsyncImage(model = info.imageUrl, contentDescription = info.title, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                                if (info.body.isNotBlank()) Text(info.body, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                                if (info.stickerUrl.isNotBlank()) AsyncImage(model = info.stickerUrl, contentDescription = "Stiker", modifier = Modifier.size(120.dp), contentScale = ContentScale.Fit)
                                if (loggedIn && isAuthorizedDevice) {
                                    OutlinedButton(
                                        onClick = {
                                            pendingDeletePhoneInfoId = info.id
                                            pendingDeletePhoneInfoTitle = info.title.ifBlank { "Adsız paylaşım" }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7777))
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("SİL")
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (showPrograms || showGames) {
                val listing = if (showGames) games.filter { it.isActive || loggedIn } else programs.filter { it.isActive || loggedIn }
                val heading = if (showGames) "OYUNLAR" else "PROQRAMLAR"
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState).padding(horizontal = 16.dp, vertical = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.clickable { showPrograms = false; showGames = false }.padding(end = 14.dp))
                        Text(heading, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    if (listing.isEmpty()) Text(if (showGames) "Hələ oyun əlavə edilməyib." else "Hələ proqram əlavə edilməyib.", color = Color(0xFFCBD6EB), modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (pendingDeleteProgramId != null && loggedIn && isAuthorizedDevice) {
                        AlertDialog(
                            onDismissRequest = { pendingDeleteProgramId = null; pendingDeleteProgramName = "" },
                            title = { Text("Proqramı sil") },
                            text = { Text("${pendingDeleteProgramName} proqramını silmək istədiyinizə əminsiniz? Bu əməliyyat geri qaytarılmır.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    val deleteId = pendingDeleteProgramId
                                    if (deleteId != null) {
                                        db.collection("programs").document(deleteId).delete()
                                            .addOnSuccessListener { message = "Proqram silindi." }
                                            .addOnFailureListener { error = "Proqram silinmədi: ${it.localizedMessage}" }
                                    }
                                    pendingDeleteProgramId = null
                                    pendingDeleteProgramName = ""
                                }) { Text("BƏLİ, SİL", color = Color(0xFFE05252)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingDeleteProgramId = null; pendingDeleteProgramName = "" }) { Text("LƏĞV ET") }
                            }
                        )
                    }
                    listing.forEach { app ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    ProgramIcon(app.iconUrl, app.name, 64)
                                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(app.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        if (app.version.isNotBlank()) Text("Versiya: ${app.version}", color = Color(0xFFFFD36A), fontSize = 13.sp)
                                        if (app.fileSize.isNotBlank()) Text(app.fileSize, color = Color(0xFF9FB0CC), fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { toggleFavorite(app.id) }) {
                                        Text(if (favoriteIds.contains(app.id)) "⭐" else "☆", color = Color(0xFFFFD36A), fontSize = 25.sp)
                                    }
                                }
                                if (app.description.isNotBlank()) Text(app.description, color = Color(0xFFCBD6EB), fontSize = 14.sp, lineHeight = 20.sp)
                                if (app.downloadUrl.isNotBlank()) Button(onClick = {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.downloadUrl))) }
                                    catch (_: Exception) { error = "Yükləmə linki açıla bilmədi." }
                                }, modifier = Modifier.fillMaxWidth()) { Text("YÜKLƏ") }
                                if (loggedIn && isAuthorizedDevice) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                name = app.name
                                                category = app.category
                                                version = app.version
                                                description = app.description
                                                link = app.downloadUrl
                                                fileSize = app.fileSize.ifBlank { "0 MB" }
                                                editingId = app.id
                                                currentIcon = app.iconUrl
                                                currentProgramStickerUrl = app.stickerUrl
                                                currentProgramStickerMime = app.stickerMime
                                                pickedImage = null
                                                programSticker = null
                                                error = ""; message = ""
                                                showPrograms = false
                                                showGames = false
                                                selectedBottomTab = 3
                                                adminEntry = "program"
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("REDAKTƏ ET")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pendingDeleteProgramId = app.id
                                                pendingDeleteProgramName = app.name
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7777))
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("SİL")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (showAppUpdates) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState).padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.clickable { showAppUpdates = false }.padding(end = 14.dp))
                        Text("Tətbiq yenilikləri", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    val appTypes = setOf("app_design", "app_feature", "app_bugfix", "app_version")
                    val appItems = appNoticeItems.filter { it.second.second in appTypes }
                    if (appItems.isEmpty()) Text("Hələ tətbiq yeniliyi paylaşılmayıb.", color = Color(0xFFCBD6EB), modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    appItems.forEach { entry ->
                        val item = entry.second.first
                        Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(when(entry.second.second) { "app_design" -> "DİZAYN YENİLİYİ"; "app_feature" -> "YENİ FUNKSİYA"; "app_bugfix" -> "XƏTA DÜZƏLİŞİ"; else -> "VERSİYA YENİLİYİ" }, color = Color(0xFFFFD36A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(item.first, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(item.second, color = Color(0xFFE2E8F0), fontSize = 14.sp, lineHeight = 21.sp)
                                if (item.third > 0L) Text(java.text.SimpleDateFormat("dd.MM.yyyy • HH:mm", java.util.Locale("az")).format(java.util.Date(item.third)), color = Color(0xFF8FA3C7), fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else if (showPhoneInfo) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState)
                        .padding(horizontal = 16.dp, vertical = 16.dp).padding(bottom = 20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("‹", color = Color.White, fontSize = 34.sp,
                            modifier = Modifier.clickable { showPhoneInfo = false }.padding(end = 14.dp))
                        Text("Yeniliklər", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    if (loggedIn && isAuthorizedDevice) {
                        OutlinedButton(
                            onClick = { if (!clearingUpdates) showClearUpdatesConfirm = true },
                            enabled = !clearingUpdates,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7777))
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (clearingUpdates) "TƏMİZLƏNİR..." else "TƏMİZLƏMƏ BÖLMƏSİ")
                        }
                    }
                    val contentNoticeTypes = setOf("program", "game", "phone_info", "program_added", "program_updated", "game_added", "game_updated", "phone_info_added", "phone_info_updated")
                    val visibleNotices = appNoticeItems.filter { entry ->
                        val type = entry.second.second
                        type in contentNoticeTypes && (noticeFilter == "all" ||
                            (noticeFilter == "phone_info" && type.startsWith("phone_info")) ||
                            (noticeFilter == "program" && type.startsWith("program")) ||
                            (noticeFilter == "game" && type.startsWith("game")))
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all" to "Hamısı", "phone_info" to "TEXNO-MƏLUMAT", "program" to "Proqramlar", "game" to "Oyunlar").forEach { option ->
                            FilterChip(selected = noticeFilter == option.first, onClick = { noticeFilter = option.first }, label = { Text(option.second, fontSize = 11.sp) })
                        }
                    }
                    if (visibleNotices.isEmpty()) {
                        Text("Bu bölmədə hələ paylaşım yoxdur.", color = Color(0xFFCBD6EB), fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    } else {
                        visibleNotices.forEach { entry ->
                            val item = entry.second.first
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                                    when (entry.second.second) {
                                        "phone_info" -> { showPhoneInfoFeed = true; showPhoneInfo = false; showPrograms = false; showGames = false }
                                        "game" -> { notificationProgramId = entry.first; showGames = true; showPrograms = false; selectedBottomTab = 1 }
                                        "program" -> { notificationProgramId = entry.first; showPrograms = true; showGames = false; selectedBottomTab = 1 }
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(item.first, color = Color(0xFFFFD36A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(item.second, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    if (item.third > 0L) {
                                        Text(
                                            java.text.SimpleDateFormat("dd.MM.yyyy • HH:mm", java.util.Locale("az"))
                                                .format(java.util.Date(item.third)),
                                            color = Color(0xFF8FA3C7), fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (showClearUpdatesConfirm && loggedIn && isAuthorizedDevice) {
                        AlertDialog(
                            onDismissRequest = { if (!clearingUpdates) showClearUpdatesConfirm = false },
                            title = { Text("Yenilikləri təmizlə") },
                            text = { Text("Bütün köhnə yeniliklər və tətbiq bildirişləri silinəcək. Bu əməliyyatı geri qaytarmaq mümkün deyil. Davam edək?") },
                            confirmButton = {
                                TextButton(onClick = { clearAllAppUpdates() }, enabled = !clearingUpdates) {
                                    Text(if (clearingUpdates) "SİLİNİR..." else "BƏLİ, TƏMİZLƏ")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearUpdatesConfirm = false }, enabled = !clearingUpdates) { Text("LƏĞV ET") }
                            }
                        )
                    }
                }
            } else if (selectedBottomTab == 1) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("KATEQORİYALAR", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("İstədiyiniz bölməni seçin", color = Color(0xFFCBD6EB), fontSize = 14.sp)
                    listOf(
                        Triple("📰", "TEXNO-MƏLUMAT", "phone_info"),
                        Triple("📱", "TEXNO-TELEKOM\nPROQRAMLAR", "programs"),
                        Triple("🎮", "OYUNLAR", "games")
                    ).forEach { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable {
                                when (item.third) {
                                    "phone_info" -> { showPhoneInfoFeed = true; showPhoneInfo = false; showPrograms = false; showGames = false }
                                    "programs" -> { showPrograms = true; showPhoneInfo = false; showGames = false }
                                    "games" -> { showGames = true; showPhoneInfo = false; showPrograms = false }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (item.third) {
                                    "phone_info" -> Image(
                                        painter = painterResource(id = R.drawable.texno_melumat_logo),
                                        contentDescription = "TEXNO-MƏLUMAT loqosu",
                                        modifier = Modifier.size(44.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    "programs" -> TexnoTelekomLogo(size = 44.dp)
                                    else -> Text(item.first, fontSize = 30.sp)
                                }
                                Text(
                                    item.second,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                                    maxLines = 3,
                                    softWrap = true,
                                )
                                Text("›", color = Color(0xFF168BFF), fontSize = 30.sp)
                            }
                        }
                    }
                }
            } else if (selectedBottomTab == 2) {
                val favoritePrograms = programs.filter { favoriteIds.contains(it.id) && (it.isActive || loggedIn) }
                val favoriteGames = games.filter { favoriteIds.contains(it.id) && (it.isActive || loggedIn) }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState)
                        .padding(horizontal = 16.dp, vertical = 20.dp).padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("⭐ FAVORİLƏR", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    if (favoritePrograms.isEmpty() && favoriteGames.isEmpty()) {
                        Text("Hələ sevimlilərə proqram əlavə edilməyib. Proqramlar bölməsindəki ☆ düyməsinə vuraraq yadda saxlayın.", color = Color(0xFFCBD6EB), modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    (favoritePrograms + favoriteGames).forEach { app ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    ProgramIcon(app.iconUrl, app.name, 64)
                                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(app.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        if (app.version.isNotBlank()) Text("Versiya: ${app.version}", color = Color(0xFFFFD36A), fontSize = 13.sp)
                                    }
                                    IconButton(onClick = { toggleFavorite(app.id) }) { Text("⭐", color = Color(0xFFFFD36A), fontSize = 25.sp) }
                                }
                                if (app.description.isNotBlank()) Text(app.description, color = Color(0xFFCBD6EB), fontSize = 14.sp)
                                if (app.downloadUrl.isNotBlank()) Button(onClick = {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.downloadUrl))) }
                                    catch (_: Exception) { error = "Yükləmə linki açıla bilmədi." }
                                }, modifier = Modifier.fillMaxWidth()) { Text("YÜKLƏ") }
                            }
                        }
                    }
                }
            } else if (selectedBottomTab == 3 && isAuthorizedDevice) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (adminEntry != null) {
                            Text("‹", color = Color.White, fontSize = 32.sp, modifier = Modifier.clickable { adminEntry = null; clearForm(); clearGameForm(); clearPostForm() }.padding(end = 12.dp))
                        }
                        Text(if (adminEntry == null) "ADMİN PANELİ" else adminEntry!!.uppercase(), color = Color(0xFFFFD36A), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    if (adminEntry == null) {
                        Text("TEXNO-TELEKOM idarəetmə bölməsi", color = Color.White, fontSize = 14.sp)
                        listOf(
                            Triple("📱", "Proqram əlavə et", "program"),
                            Triple("🎮", "Oyun əlavə et", "game"),
                            Triple("📰", "Texno məlumat paylaş", "info")
                        ).forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    adminEntry = item.third
                                    error = ""; message = ""
                                    if (item.third == "program") clearForm()
                                    if (item.third == "game") clearGameForm()
                                    if (item.third == "info") { phoneInfoTitle = ""; phoneInfoBody = ""; phoneInfoImage = null; phoneInfoSticker = null }
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                            ) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.first, fontSize = 28.sp)
                                    Text(item.second, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
                                    Spacer(Modifier.weight(1f))
                                    Text("›", color = Color.White, fontSize = 28.sp)
                                }
                            }
                        }
                            Card(modifier = Modifier.fillMaxWidth().clickable { showGroupChat = true }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))) {
                                Text("👥  Qrup rəhbərliyi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(18.dp))
                            }
                            if (loggedIn) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { adminEntry = "device_blocking" },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                                ) {
                                    Text("📵  Telefon bloklaması", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(18.dp))
                                }
                            }
                    } else {
                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD36A), unfocusedBorderColor = Color(0xFF64748B),
                            focusedLabelColor = Color(0xFFFFD36A), unfocusedLabelColor = Color(0xFFB8C5DC),
                            cursorColor = Color(0xFFFFD36A)
                        )
                        when (adminEntry) {
                            "device_blocking" -> {
                                DeviceBlockAdminPanel(db)
                            }
                            "program" -> {
                                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Proqramın adı") }, colors = fieldColors)
                                OutlinedTextField(category, { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Kateqoriya") }, colors = fieldColors)
                                OutlinedTextField(version, { version = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Versiya") }, colors = fieldColors)
                                OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Təsvir") }, colors = fieldColors)
                                OutlinedTextField(link, { link = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Yükləmə linki") }, colors = fieldColors)
                                OutlinedTextField(fileSize, { fileSize = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Fayl ölçüsü") }, colors = fieldColors)
                                OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (pickedImage == null) "Proqram şəklini seç" else "Şəkil seçildi") }
                                OutlinedButton(onClick = { programStickerPicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (programSticker == null) "Stiker seç (istəyə bağlı)" else "Stiker seçildi") }
                                Button(onClick = { saveProgram() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Yadda saxlanılır..." else if (editingId != null) "Dəyişiklikləri yadda saxla" else "Proqramı əlavə et") }
                            }
                            "game" -> {
                                OutlinedTextField(gameName, { gameName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Oyunun adı") }, colors = fieldColors)
                                OutlinedTextField(gameVersion, { gameVersion = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Versiya") }, colors = fieldColors)
                                OutlinedTextField(gameDescription, { gameDescription = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Təsvir") }, colors = fieldColors)
                                OutlinedTextField(gameLink, { gameLink = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Yükləmə linki") }, colors = fieldColors)
                                OutlinedButton(onClick = { gameImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (gameImage == null) "Oyun şəklini seç" else "Şəkil seçildi") }
                                Button(onClick = { saveGame() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Yadda saxlanılır..." else "Oyunu əlavə et") }
                            }
                            "info" -> {
                                OutlinedTextField(phoneInfoTitle, { phoneInfoTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Başlıq") }, colors = fieldColors)
                                OutlinedTextField(phoneInfoBody, { phoneInfoBody = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("Məlumat mətni") }, colors = fieldColors)
                                OutlinedButton(onClick = { phoneInfoImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (phoneInfoImage == null) "Şəkil əlavə et" else "Şəkil seçildi") }
                                OutlinedButton(onClick = { phoneStickerPicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (phoneInfoSticker == null) "Stiker əlavə et (istəyə bağlı)" else "Stiker seçildi") }
                                Button(onClick = { savePhoneInfo() }, enabled = !phoneInfoBusy, modifier = Modifier.fillMaxWidth()) { Text(if (phoneInfoBusy) "Paylaşılır..." else "TEXNO-MƏLUMATI paylaş") }
                            }
                        }
                        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7777), fontSize = 13.sp)
                        if (message.isNotBlank()) Text(message, color = Color(0xFF72D69A), fontSize = 13.sp)
                    }
                }
            } else if (selectedBottomTab == 3) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ADMİNLƏ ƏLAQƏ", color = Color(0xFFFFD36A), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Sual və təkliflərinizi adminə göndərin.", color = Color.White, fontSize = 14.sp)
                    Button(onClick = { showFeedback = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0868E8))) {
                        Text("Adminlə əlaqə saxla", color = Color.White)
                    }
                }
            } else if (selectedBottomTab == 4) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)
                            .clickable { showAbout = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                            TexnoTelekomLogo(size = 108.dp)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(profileNickname.ifBlank { "Profil adı" }, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(7.dp))
                            Text(if (isAuthorizedDevice) "ADMİN" else "İstifadəçi", color = Color.White, fontSize = 14.sp,
                                modifier = Modifier.background(Color(0xFF0868E8), RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
                        }
                        Text("›", color = Color.White, fontSize = 38.sp)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { selectedBottomTab = 1; showPrograms = true; showGames = false }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⬇️", fontSize = 27.sp)
                                Text("0", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Yükləmə", color = Color(0xFFCBD6EB), fontSize = 14.sp)
                            }
                            Box(Modifier.width(1.dp).height(78.dp).background(Color(0xFF26334D)))
                            Column(Modifier.weight(1f).clickable { selectedBottomTab = 2 }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⭐", fontSize = 27.sp)
                                Text("${favoriteIds.count { id -> programs.any { it.id == id } || games.any { it.id == id }}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Sevimli", color = Color(0xFFCBD6EB), fontSize = 14.sp)
                            }
                            Box(Modifier.width(1.dp).height(78.dp).background(Color(0xFF26334D)))
                            Column(Modifier.weight(1f).clickable { noticeFilter = "all"; showPhoneInfo = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🗓️", fontSize = 27.sp)
                                Text("${appNoticeItems.count { it.second.second in setOf("program", "game", "phone_info", "program_added", "program_updated", "game_added", "game_updated", "phone_info_added", "phone_info_updated") }}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Bildiriş", color = Color(0xFFCBD6EB), fontSize = 14.sp)
                            }
                        }
                    }
                    @Composable
                    fun ProfileItem(icon: String, title: String, tint: Color = Color.White, onClick: () -> Unit) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 5.dp)
                                .background(Color(0xFF111A2D), RoundedCornerShape(22.dp))
                                .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(icon, fontSize = 29.sp, color = tint, modifier = Modifier.width(78.dp))
                            Text(title, color = Color(0xFFF1F5FF), fontSize = 18.sp, modifier = Modifier.weight(1f))
                            Text("›", color = Color(0xFFDCE7FF), fontSize = 34.sp)
                        }
                    }
                    ProfileItem("⚡", "Yeniliklər", Color(0xFFFFD21C)) { showPhoneInfo = true }
                    ProfileItem("🛡️", "Təhlükəsizlik Məlumatı", Color(0xFF168BFF)) { showSecurity = true }
                    ProfileItem("💬", "Təklif Qutusu", Color(0xFF00D5C5)) { showFeedback = true }
                    ProfileItem("ℹ️", "Tətbiq Haqqında", Color(0xFF247BFF)) { showAbout = true }
                    ProfileItem("🔄", if (updateCheckInProgress) "Yeniləmə yoxlanılır..." else "Tətbiq yenilə", Color(0xFF20C997)) {
                        if (!updateCheckInProgress) {
                            updateCheckResult = null
                            updateCheckMessage = ""
                            updateCheckInProgress = true
                            checkForAppUpdate(true)
                        }
                    }
                    ProfileItem("⚙️", "Ayarlar", Color(0xFF247BFF)) { showSettings = true; settingsSelected = null }
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().height(94.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF063B91))
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF061B46), Color(0xFF087DEB), Color(0xFF061B46)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("➤", color = Color(0xFF19C5FF), fontSize = 52.sp, modifier = Modifier.padding(end = 18.dp))
                                Column {
                                    Text("TEXNO-TELEKOM", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Daha yaxşı bir gələcək üçün!", color = Color(0xFFE1EDFF), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(homeScrollState).padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 28.dp)) {
                if (availableUpdate != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF123B78))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Tətbiq yeniləməsi mövcuddur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("TEXNO-TELEKOM • Yeni Versiya: ${availableUpdate!!.versionName}", color = Color(0xFFE1EDFF))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val update = availableUpdate
                                        availableUpdate = null
                                        if (update != null) onInstallUpdate(update.apkUrl)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("İNDİ YENİLƏ") }
                                TextButton(onClick = { availableUpdate = null }) { Text("SONRA", color = Color.White) }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.texno_melumat_logo),
                            contentDescription = "TEXNO-MƏLUMAT loqosu",
                            modifier = Modifier.size(76.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            "TEXNO-MƏLUMAT",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    val bellTransition = rememberInfiniteTransition(label = "home_notification_bell")
                    val bellRotation by bellTransition.animateFloat(
                        initialValue = -12f,
                        targetValue = 12f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bell_rotation"
                    )
                    IconButton(
                        onClick = {
                            showAppUpdates = true
                            showPhoneInfo = false
                            showPhoneInfoFeed = false
                            showPhoneInfoFeed = false
                            showPrograms = false
                            showGames = false
                            showFeedback = false
                        },
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            "🔔",
                            fontSize = 25.sp,
                            modifier = Modifier.graphicsLayer { rotationZ = bellRotation }
                        )
                    }
                    Row(
                        Modifier.align(Alignment.TopEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SupportBadgeLogo(
                            size = 30.dp,
                            modifier = Modifier.clickable { showGroupChat = true }
                        )
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(Icons.Default.Search, contentDescription = "Axtarış", tint = Color.White)
                        }
                    }
                }
                if (phoneInfos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📢 Bu həftənin paylaşımları",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        phoneInfos.forEach { info ->
                            val isNew = info.createdAt > 0L &&
                                System.currentTimeMillis() - info.createdAt <= 7L * 24L * 60L * 60L * 1000L
                            val newBadgeTransition = rememberInfiniteTransition()
                            val newBadgeScale by newBadgeTransition.animateFloat(
                                initialValue = 0.92f,
                                targetValue = 1.08f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            val newBadgeAlpha by newBadgeTransition.animateFloat(
                                initialValue = 0.68f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            Card(
                                modifier = Modifier.width(104.dp).height(112.dp).clickable {
                                    selectedPhoneInfoId = info.id
                                    showPhoneInfoFeed = true
                                    showPhoneInfo = false
                                    showPrograms = false
                                    showGames = false
                                },
                                shape = RoundedCornerShape(11.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .graphicsLayer {
                                                scaleX = if (isNew) newBadgeScale else 1f
                                                scaleY = if (isNew) newBadgeScale else 1f
                                                alpha = if (isNew) newBadgeAlpha else 0.72f
                                            }
                                            .background(
                                                Color(0xFF123B61),
                                                RoundedCornerShape(7.dp)
                                            )
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "NEW",
                                            color = Color(0xFF35C6FF),
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Text(
                                        info.title.ifBlank { "Telefon məlumatı" },
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    if (isNew) {
                                        Text(
                                            "YENİ",
                                            color = Color(0xFF20B8FF),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (searchOpen) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Axtar...") }, singleLine = true)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeDesignCard("📰", "TEXNO-MƏLUMAT", Modifier.weight(1f), showMelumatLogo = true) { selectedBottomTab = 0; showPhoneInfoFeed = true; showPhoneInfo = false; showPrograms = false; showGames = false; showFeedback = false }
                    HomeDesignCard("📱", "TEXNO-TELEKOM PROQRAMLAR", Modifier.weight(1f), showLogo = true) { selectedBottomTab = 1; showPrograms = true; showGames = false; showPhoneInfo = false; showPhoneInfoFeed = false; showFeedback = false }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeDesignCard("🎮", "OYUNLAR", Modifier.weight(1f)) { selectedBottomTab = 2; showGames = true; showPrograms = false; showPhoneInfo = false; showPhoneInfoFeed = false; showFeedback = false }
                    HomeDesignCard("💬", "ADMİNLƏ-ƏLAQƏ", Modifier.weight(1f)) { selectedBottomTab = 3; showPrograms = false; showGames = false; showPhoneInfo = false; showPhoneInfoFeed = false; adminEntry = null; showFeedback = true }
                }
                if (weeklyHitPrograms.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔥", fontSize = 21.sp)
                            Text(
                                "Bu həftənin hitləri",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            "Hamısına bax  →",
                            color = Color(0xFF20B8FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clickable {
                                selectedBottomTab = 1
                                showPrograms = true
                                showGames = false
                                showPhoneInfo = false
                                showPhoneInfoFeed = false
                            }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        weeklyHitPrograms.forEach { app ->
                            Card(
                                modifier = Modifier.width(104.dp).height(112.dp),
                                shape = RoundedCornerShape(11.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D))
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    ProgramIcon(app.iconUrl, app.name, 32)
                                    Text(
                                        app.name,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    if (app.version.isNotBlank()) {
                                        Text("v${app.version}", color = Color(0xFFBFD8FF), fontSize = 8.sp, maxLines = 1, softWrap = false)
                                    }
                                    if (app.fileSize.isNotBlank()) {
                                        Text(app.fileSize, color = Color(0xFFBFD8FF), fontSize = 9.sp, maxLines = 1, softWrap = false)
                                    }
                                    if (app.downloadUrl.isNotBlank()) {
                                        Button(
                                            onClick = {
                                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.downloadUrl))) }
                                                catch (_: Exception) { error = "Yükləmə linki açıla bilmədi." }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(22.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) { Text("Yüklə", fontSize = 9.sp, maxLines = 1) }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (adminOpen && isAuthorizedDevice) { /* admin panel remains below in its existing section */ }
            }

            }
            if (deviceBlocked) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Cihaz bloklanıb") },
                    text = {
                        Text(
                            "Bu telefonun TEXNO-TELEKOM tətbiqinə girişi məhdudlaşdırılıb.\n\nSəbəb: " +
                                deviceBlockReason.ifBlank { "Tətbiq qaydalarının pozulması" } +
                                "\n\nƏgər bunun səhv olduğunu düşünürsünüzsə, adminlə əlaqə saxlayın."
                        )
                    },
                    confirmButton = { }
                )
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF080D1E)).navigationBarsPadding().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val tabs = listOf("🏠" to "Əsas səhifə", "📱" to "Kateqoriyalar", "⭐" to "Favorilər", (if (isAuthorizedDevice) "🛠️" to "Admin Paneli" else "💬" to "Adminlə əlaqə"), "👤" to "Profil")
                tabs.forEachIndexed { index, tab ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        selectedBottomTab = index
                        // Alt menyudan başqa bölməyə keçəndə kateqoriya daxilindəki ekranları bağla.
                        showPrograms = false
                        showGames = false
                        showPhoneInfoFeed = false
                        showPhoneInfo = false
                        if (index == 0) {
                            // Əsas səhifə seçiləndə bütün alt bölmələri bağla.
                            showPrograms = false
                            showGames = false
                            showPhoneInfo = false
                            showPhoneInfoFeed = false
                            showFeedback = false
                            showAbout = false
                            showSecurity = false
                            showSettings = false
                            settingsSelected = null
                            showGroupChat = false
                            showGroupManagementAdmin = false
                            adminOpen = false
                            adminEntry = null
                            showGameForm = false
                            searchOpen = false
                            query = ""
                            notificationProgramId = null
                        }
                        if (index == 3 && !isAuthorizedDevice) showFeedback = true
                    }) {
                        Text(tab.first, fontSize = 25.sp)
                        Text(tab.second, color = if (selectedBottomTab == index) Color(0xFF168BFF) else Color(0xFF8993A9), fontSize = 11.sp)
                    }
                }
            }
        }
}
}
}

}


private data class DeviceBlockRow(
    val hash: String,
    val blocked: Boolean,
    val reason: String
)

@Composable
private fun DeviceBlockAdminPanel(db: FirebaseFirestore) {
    val context = LocalContext.current
    val devices = remember { mutableStateListOf<DeviceBlockRow>() }
    var loading by remember { mutableStateOf(true) }
    var reason by remember { mutableStateOf("") }
    var durationDays by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }

    fun refreshDevices() {
        loading = true
        db.collection("device_registry").get()
            .addOnSuccessListener { result ->
                devices.clear()
                result.documents.forEach { doc ->
                    val hash = doc.getString("device_hash") ?: doc.id
                    db.collection("blocked_devices").document(hash).get()
                        .addOnSuccessListener { block ->
                            val until = block.getTimestamp("blocked_until")
                            val active = block.exists() && (until == null || until.toDate().time > System.currentTimeMillis())
                            val row = DeviceBlockRow(hash, active, if (active) block.getString("reason").orEmpty() else "")
                            val oldIndex = devices.indexOfFirst { it.hash == hash }
                            if (oldIndex >= 0) devices[oldIndex] = row else devices.add(row)
                            loading = false
                        }
                        .addOnFailureListener { loading = false }
                }
                if (result.isEmpty) loading = false
            }
            .addOnFailureListener {
                loading = false
                message = "Telefon siyahısını yükləmək mümkün olmadı: ${it.localizedMessage}"
            }
    }

    LaunchedEffect(Unit) { refreshDevices() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Qeydiyyatdan keçmiş telefonlar", color = Color.White, fontSize = 15.sp)
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bloklama səbəbi") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFFD36A), unfocusedBorderColor = Color(0xFF64748B),
                focusedLabelColor = Color(0xFFFFD36A), unfocusedLabelColor = Color(0xFFB8C5DC)
            )
        )
        Text("Bloklama müddəti", color = Color(0xFFFFD36A), fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Daimi", 1 to "1 gün", 7 to "7 gün", 30 to "30 gün").forEach { option ->
                OutlinedButton(
                    onClick = { durationDays = option.first },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (durationDays == option.first) Color(0xFFFFD36A) else Color.White
                    )
                ) { Text(option.second, fontSize = 11.sp) }
            }
        }
        if (loading) {
            CircularProgressIndicator(color = Color(0xFFFFD36A))
        } else if (devices.isEmpty()) {
            Text("Hələ heç bir telefon qeydə alınmayıb. İstifadəçilər tətbiqi açdıqca burada görünəcəklər.", color = Color(0xFFCBD6EB))
        } else {
            devices.sortedBy { it.hash }.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2D)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Telefon • ${item.hash.takeLast(8).uppercase()}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (item.blocked) "Bloklanıb: ${item.reason}" else "Aktiv", color = if (item.blocked) Color(0xFFFF7777) else Color(0xFF72D69A))
                        if (item.blocked) {
                            OutlinedButton(
                                onClick = {
                                    db.collection("blocked_devices").document(item.hash).delete()
                                        .addOnSuccessListener { message = "Telefon blokdan çıxarıldı."; refreshDevices() }
                                        .addOnFailureListener { message = "Blokdan çıxarmaq olmadı: ${it.localizedMessage}" }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("BLOKDAN ÇIXAR") }
                        } else {
                            Button(
                                onClick = {
                                    val cleanReason = reason.trim().ifBlank { "Tətbiq qaydalarının pozulması" }
                                    val blockedUntil: Any? = if (durationDays == 0) null
                                        else com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + durationDays * 24L * 60L * 60L * 1000L))
                                    db.collection("blocked_devices").document(item.hash).set(
                                        mapOf(
                                            "device_hash" to item.hash,
                                            "reason" to cleanReason,
                                            "created_at" to FieldValue.serverTimestamp(),
                                            "blocked_until" to blockedUntil
                                        )
                                    ).addOnSuccessListener { message = "Telefon bloklandı."; refreshDevices() }
                                        .addOnFailureListener { message = "Telefon bloklanmadı: ${it.localizedMessage}" }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("TELEFONU BLOKLA") }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { refreshDevices() }, modifier = Modifier.fillMaxWidth()) { Text("SİYAHINI YENİLƏ") }
        if (message.isNotBlank()) Text(message, color = Color(0xFFFFD36A))
    }
}

@Composable
private fun HomeDesignCard(emoji: String, label: String, modifier: Modifier = Modifier, showLogo: Boolean = false, showMelumatLogo: Boolean = false, onClick: () -> Unit) {
    Card(modifier = modifier.heightIn(min = 150.dp).clickable(onClick = onClick), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF151F3B))) {
        if (showMelumatLogo) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(Color(0xFF07152F)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.texno_melumat_logo),
                        contentDescription = "TEXNO-MƏLUMAT loqosu",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(label, color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        } else if (showLogo) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TexnoTelekomLogo(size = 64.dp)
                Text(
                    label,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(emoji, fontSize = 48.sp)
                Text(label, color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}