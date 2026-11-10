package az.texnotelekom.app

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.DownloadManager
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private val bg = Color(0xFF101010)
private val panel = Color(0xFF454545)
private val accent = Color(0xFFFFB900)
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
                extension == "webm" || mime.startsWith("video/") -> "video"
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
private const val ADMIN_ICON_BASE64 = "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"
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
data class PhoneInfo(val id: String, val title: String, val body: String, val imageUrl: String, val stickerUrl: String, val stickerMime: String)


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

class MainActivity : ComponentActivity() {
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
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(updateReceiver, filter)
        }
        setContent { TexnoTelekomApp(onInstallUpdate = { url -> installUpdate(url) }) }
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

@Composable
fun TexnoTelekomApp(onInstallUpdate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    // Server-managed design values update live without publishing a new APK.
    var designBgHex by remember { mutableStateOf("#101010") }
    var designPanelHex by remember { mutableStateOf("#454545") }
    var designAccentHex by remember { mutableStateOf("#FFB900") }
    var designGoldCards by remember { mutableStateOf(false) }
    val chameleon = rememberInfiniteTransition(label = "chameleon_theme")
    val liveBg by chameleon.animateColor(
        initialValue = Color(0xFF101010), targetValue = Color(0xFF17102A),
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "background_color"
    )
    val livePanel by chameleon.animateColor(
        initialValue = Color(0xFF454545), targetValue = Color(0xFF34304A),
        animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse), label = "panel_color"
    )
    val liveAccent by chameleon.animateColor(
        initialValue = Color(0xFFFFB900), targetValue = Color(0xFF43D9FF),
        animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse), label = "accent_color"
    )

    var availableUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }
    LaunchedEffect(Unit) {
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
                if (remoteCode > currentCode && apkUrl.startsWith("https://")) {
                    Handler(Looper.getMainLooper()).post {
                        // Yeni versiya aşkarlananda istifadəçidən əlavə düymə tələb etmədən yükləməni başlat.
                        onInstallUpdate(apkUrl)
                    }
                }
            } catch (_: Exception) { }
        }.start()
    }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var showPhoneInfo by remember { mutableStateOf(false) }
    val texnoInfoLogo = remember {
        val bytes = Base64.decode(TEXNO_INFO_LOGO_BASE64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }
    var showGames by remember { mutableStateOf(false) }
    var phoneInfoTitle by remember { mutableStateOf("") }
    var phoneInfoBody by remember { mutableStateOf("") }
    var phoneInfoImage by remember { mutableStateOf<Uri?>(null) }
    var phoneInfoSticker by remember { mutableStateOf<Uri?>(null) }
    var phoneInfoStickerBusy by remember { mutableStateOf(false) }
    var phoneInfoBusy by remember { mutableStateOf(false) }
    var adminOpen by remember { mutableStateOf(false) }
    var showSiteTextEditor by remember { mutableStateOf(false) }
    var designAdminOpen by remember { mutableStateOf(false) }
    var showDeviceInfo by remember { mutableStateOf(false) }
    var revealDeviceCodeButton by remember { mutableStateOf(false) }
    val deviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Tapılmadı" }
    val isAuthorizedDevice = deviceId in ADMIN_DEVICE_IDS
    val savedAdminUser = remember { FirebaseAuth.getInstance().currentUser }
    var loggedIn by remember { mutableStateOf(isAuthorizedDevice && savedAdminUser?.uid == ADMIN_UID && savedAdminUser.email == ADMIN_EMAIL) }

    var email by remember { mutableStateOf(ADMIN_EMAIL) }
    var password by remember { mutableStateOf("") }

    var error by remember { mutableStateOf("") }
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
    val programs = remember { mutableStateListOf<AppItem>() }
    val games = remember { mutableStateListOf<AppItem>() }
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
    var appNoticeItems by remember { mutableStateOf(listOf<Pair<String, String>>()) }
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

    DisposableEffect(db) {
        val noticeListener = db.collection("app_notifications").orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, e ->
                if (e == null && snap != null) {
                    appNoticeItems = snap.documents.map { d -> (d.getString("type") ?: "") to (d.getString("title") ?: "Yeni bildiriş") }
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
                                d.getString("sticker_url") ?: "", d.getString("sticker_mime") ?: ""
                            )
                        )
                    }
                    // Automatically open TEXNO-MƏLUMAT when posts arrive from the admin panel.
                    if (phoneInfos.isNotEmpty()) showPhoneInfo = true
                }
            }
        onDispose { phoneListener.remove() }
    }

    DisposableEffect(db) {
        val listener = db.collection("programs").addSnapshotListener { snap, e ->
            if (e != null) error = "Firebase xətası: ${e.localizedMessage}"
            else if (snap != null) {
                programs.clear()
                games.clear()
                posts.clear()
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
                    designBgHex = snapshot.getString("design_background") ?: designBgHex
                    designPanelHex = snapshot.getString("design_panel") ?: designPanelHex
                    designAccentHex = snapshot.getString("design_accent") ?: designAccentHex
                    designGoldCards = snapshot.getBoolean("design_gold_cards") ?: designGoldCards
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
                db.collection("app_notifications").add(hashMapOf("type" to "phone_info", "title" to "Yeni TEXNO-MƏLUMAT paylaşımı", "body" to "Yeni məlumat paylaşıldı. Tətbiqə daxil olub baxın.", "created_at" to FieldValue.serverTimestamp()))
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
            "updated_at" to FieldValue.serverTimestamp()
        )
        db.collection("site_settings").document("content").set(values, SetOptions.merge())
            .addOnSuccessListener { message = "Sayt yazıları yadda saxlanıldı." }
            .addOnFailureListener { error = "Yadda saxlamaq alınmadı: ${it.localizedMessage}" }
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
        val task = if (id == null) db.collection("programs").add(values)
                   else db.collection("programs").document(id).set(values, SetOptions.merge())
        task.addOnSuccessListener {
            newProgramNotice = true
            db.collection("app_notifications").add(hashMapOf("type" to "program", "title" to if (id == null) "Yeni proqram əlavə edildi" else "Proqram yeniləndi", "body" to name.trim() + " — " + version.trim(), "created_at" to FieldValue.serverTimestamp()))
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
            val task = if (id == null) db.collection("programs").add(values) else db.collection("programs").document(id).set(values, SetOptions.merge())
            task.addOnSuccessListener {
                newProgramNotice = true
                db.collection("app_notifications").add(hashMapOf("type" to "game", "title" to if (id == null) "Yeni oyun əlavə edildi" else "Oyun yeniləndi", "body" to gameName.trim() + " — " + gameVersion.trim(), "created_at" to FieldValue.serverTimestamp()))
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
            surfaceVariant = Color(0xFF383838),
            primary = liveAccent,
            secondary = Color(0xFFFFD166),
            tertiary = Color(0xFFFFC928),
            onPrimary = Color(0xFF17130A),
            onSecondary = Color(0xFF17130A),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)
        )
    ) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(livePanel, liveBg, liveBg)))
                .statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp).basicMarquee(iterations = Int.MAX_VALUE),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            heroTitle,
                            style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFA000)))),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                        Text(
                            "TEXNO-MƏLUMAT",
                            style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFA000)))),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                        if (newPhoneInfoNotice) Text("🔔 YENİ", color = Color(0xFFFFD166), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                    Text(
                        "✦ TELEFON MƏLUMATLARI  •  PREMIUM PROQRAMLARIN ÜNVANI  •  TEXNOLOGİYA DÜNYASI  •  TELEFON MƏLUMATLARI  •  PREMIUM PROQRAMLARIN ÜNVANI  ✦",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp).basicMarquee(iterations = Int.MAX_VALUE),
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.clickable { revealDeviceCodeButton = !revealDeviceCodeButton },
                        contentAlignment = Alignment.Center
                    ) { TexnoTelekomLogo(size = 78.dp) }
                }
                if (isAuthorizedDevice) {
                    Text(
                        "ADMİN",
                        modifier = Modifier.align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 10.dp)
                            .clickable { adminOpen = !adminOpen; error = ""; message = "" },
                        color = Color(0xFFFFD76B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.width(170.dp).height(54.dp),
                            placeholder = { Text("Axtar...", color = Color(0xFFBDBDBD)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFB900),
                                unfocusedBorderColor = Color(0xFFFFB900),
                                cursorColor = Color.White
                            )
                        )
                    }
                    IconButton(
                        onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                        modifier = Modifier.size(48.dp)
                    ) { Icon(Icons.Default.Search, contentDescription = "Axtarış", tint = Color(0xFFFFD76B)) }
                }
            }
            if (revealDeviceCodeButton) {
                TextButton(onClick = { showDeviceInfo = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cihaz kodunu göstər", color = Color(0xFFBDBDBD), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (welcomeText.isNotBlank()) {
                Text(
                    welcomeText,
                    color = Color.White, fontSize = 19.sp, lineHeight = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = Color(0xFF555555), thickness = 1.dp)
            Spacer(Modifier.height(28.dp))
            if (infoTitle.isNotBlank()) {
                Text(
                    infoTitle,
                    color = Color.White, fontSize = 21.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (infoLine1.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(infoLine1, color = Color.White, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            }
            if (infoLine2.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(infoLine2, color = Color.White, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            }
            if (infoLine3.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(infoLine3, color = Color.White, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            }
            if (whatsappPhone.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "WhatsApp: $whatsappPhone",
                    color = Color(0xFFFFD166), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        val number = whatsappPhone.filter { it.isDigit() }
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$number"))
                        context.startActivity(intent)
                    }
                )
            }

            if (adminOpen && isAuthorizedDevice) {
                Card(colors = CardDefaults.cardColors(containerColor = livePanel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!loggedIn) {
                            Text("Admin girişi", fontWeight = FontWeight.Bold)
                            OutlinedTextField(email, { email = it }, label = { Text("E-poçt") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(password, { password = it }, label = { Text("Şifrə") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                                    .addOnCompleteListener { task ->
                                        val user = FirebaseAuth.getInstance().currentUser
                                        if (task.isSuccessful && user?.uid == ADMIN_UID && user.email == ADMIN_EMAIL) {
                                            loggedIn = true; password = ""; message = "Admin girişi uğurludur."
                                        } else {
                                            FirebaseAuth.getInstance().signOut(); error = "Admin hesabı və ya şifrə yanlışdır."
                                        }
                                    }
                            }, enabled = email.isNotBlank() && password.isNotBlank()) { Text("Daxil ol") }
                        } else {
                            OutlinedButton(onClick = { designAdminOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Canlı dizayn bölməsi") }
                            OutlinedButton(onClick = { showSiteTextEditor = !showSiteTextEditor }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (showSiteTextEditor) "SAYT YAZILARINI BAĞLA ▲" else "SAYT YAZILARINI DÜZƏLT ▼", fontWeight = FontWeight.Bold)
                            }
                            if (showSiteTextEditor) {
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF303030)), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("SAYT YAZILARI", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                        OutlinedTextField(heroTitle, { heroTitle = it }, label = { Text("Əsas başlıq") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(welcomeText, { welcomeText = it }, label = { Text("Qarşılama yazısı") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                        OutlinedTextField(infoTitle, { infoTitle = it }, label = { Text("Məlumat başlığı") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(infoLine1, { infoLine1 = it }, label = { Text("1-ci məlumat sətri") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(infoLine2, { infoLine2 = it }, label = { Text("2-ci məlumat sətri") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(infoLine3, { infoLine3 = it }, label = { Text("3-cü məlumat sətri") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(whatsappPhone, { whatsappPhone = it }, label = { Text("WhatsApp nömrəsi") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                                        Button(onClick = { saveSiteTexts() }, modifier = Modifier.fillMaxWidth()) { Text("Yazıları yadda saxla") }
                                    }
                                }
                            }
                            HorizontalDivider()
                            HorizontalDivider()
                            Text("TEXNO-MƏLUMAT paylaş", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            OutlinedTextField(phoneInfoTitle, { phoneInfoTitle = it }, label = { Text("Telefon modeli / başlıq") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(phoneInfoBody, { phoneInfoBody = it }, label = { Text("Telefon haqqında məlumat") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            }
                            if (phoneInfoSticker != null) {
                                Text("Stiker əlavə olunub", color = Color(0xFF8DE6A5))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { phoneInfoBody += if (phoneInfoBody.isBlank()) INLINE_STICKER_TOKEN else " $INLINE_STICKER_TOKEN" }) { Text("MƏTNƏ YERLƏŞDİR") }
                                    TextButton(onClick = { phoneInfoSticker = null; phoneInfoBody = phoneInfoBody.replace(INLINE_STICKER_TOKEN, "") }) { Text("Stikeri sil") }
                                }
                            }
                            OutlinedButton(onClick = { phoneInfoImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Image, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Qalereyadan TEXNO-MƏLUMAT şəkli seç")
                            }
                            if (phoneInfoImage != null) {
                                Text("Şəkil seçilib — paylaşım zamanı yüklənəcək", color = Color(0xFF8DE6A5), fontSize = 12.sp)
                                AsyncImage(model = phoneInfoImage, contentDescription = "Seçilmiş TEXNO-MƏLUMAT şəkli",
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit)
                                TextButton(onClick = { phoneInfoImage = null }) { Text("Şəkli sil") }
                            }
                            Button(onClick = { savePhoneInfo() }, enabled = !phoneInfoBusy, modifier = Modifier.fillMaxWidth()) {
                                if (phoneInfoBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("TEXNO-MƏLUMAT bölməsində paylaş")
                            }
                            HorizontalDivider()
                            Button(onClick = { clearGameForm(); showGameForm = true }) { Text("OYUN ƏLAVƏ ET") }
                            if (showGameForm) {
                                Text(if (editingGameId == null) "Oyun əlavə et" else "Oyunu redaktə et", fontWeight = FontWeight.Bold)
                                OutlinedTextField(gameName, { gameName = it }, label = { Text("Oyunun adı") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(gameVersion, { gameVersion = it }, label = { Text("Versiya") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(gameDescription, { gameDescription = it }, label = { Text("Təsvir") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                OutlinedTextField(gameLink, { gameLink = it }, label = { Text("APK yükləmə linki") }, modifier = Modifier.fillMaxWidth())
                                OutlinedButton(onClick = { gameImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Qalereyadan oyun şəkli seç") }
                                if (gameImage != null) AsyncImage(model = gameImage, contentDescription = "Oyun şəkli", modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit)
                                else if (gameImageUrl.isNotBlank()) ProgramIcon(gameImageUrl, gameName, 64)
                                Button(onClick = { saveGame() }, enabled = !busy && gameName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (editingGameId == null) "Oyunu əlavə et" else "Dəyişiklikləri yadda saxla") }
                                TextButton(onClick = { clearGameForm() }) { Text("Bağla") }
                            }
                            Text("Oyunlar: ${games.size}", fontWeight = FontWeight.Bold)
                            games.forEach { g ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF303030)), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (g.iconUrl.isNotBlank()) ProgramIcon(g.iconUrl, g.name, 40)
                                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text(g.name, fontWeight = FontWeight.Bold)
                                            Text("OYUN • ${g.version}", fontSize = 11.sp, color = Color.LightGray)
                                        }
                                        IconButton(onClick = { editingGameId = g.id; gameName = g.name; gameVersion = g.version; gameDescription = g.description; gameLink = g.downloadUrl; gameImageUrl = g.iconUrl; gameImage = null; showGameForm = true }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFFC928)) }
                                        IconButton(onClick = { db.collection("programs").document(g.id).delete().addOnSuccessListener { message = "Oyun silindi." }.addOnFailureListener { error = "Oyun silinmədi: ${it.localizedMessage}" } }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF7777)) }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Text(if (editingId == null) "Proqram əlavə et" else "Proqramı redaktə et", fontWeight = FontWeight.Bold)
                            OutlinedTextField(name, { name = it }, label = { Text("Proqramın adı") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(category, { category = it }, label = { Text("Kateqoriya") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(version, { version = it }, label = { Text("Versiya") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(description, { description = it }, label = { Text("Təsvir") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            }
                            if (programSticker != null || currentProgramStickerUrl.isNotBlank()) {
                                Text("Stiker əlavə olunub", color = Color(0xFF8DE6A5))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { description += if (description.isBlank()) INLINE_STICKER_TOKEN else " $INLINE_STICKER_TOKEN" }) { Text("MƏTNƏ YERLƏŞDİR") }
                                    TextButton(onClick = { programSticker = null; currentProgramStickerUrl = ""; currentProgramStickerMime = ""; description = description.replace(INLINE_STICKER_TOKEN, "") }) { Text("Stikeri sil") }
                                }
                            }
                            OutlinedTextField(link, { link = it }, label = { Text("Yükləmə linki") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(fileSize, { fileSize = it }, label = { Text("Fayl ölçüsü") }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Qalereyadan proqram şəkli seç")
                            }
                            if (pickedImage != null) Text("Yeni şəkil seçilib", color = Color(0xFF8DE6A5))
                            else if (currentIcon.isNotBlank()) Text("Mövcud proqram şəkli saxlanılacaq", color = Color.LightGray, fontSize = 11.sp)
                            Button(onClick = { saveProgram() }, enabled = !busy && name.isNotBlank() && category.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(if (editingId == null) "Proqramı əlavə et" else "Dəyişiklikləri yadda saxla")
                            }
                            if (editingId != null) TextButton(onClick = { clearForm() }) { Text("Redaktəni ləğv et") }
                            HorizontalDivider()
                            Text("Proqramlar: ${programs.size}", fontWeight = FontWeight.Bold)
                            programs.forEach { p ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF303030)), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (p.iconUrl.isNotBlank()) ProgramIcon(p.iconUrl, p.name, 40)
                                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text(p.name, fontWeight = FontWeight.Bold)
                                            Text("${p.category} • ${p.version}", fontSize = 11.sp, color = Color.LightGray)
                                        }
                                        IconButton(onClick = {
                                            editingId = p.id; name = p.name; category = p.category; version = p.version
                                            description = p.description; link = p.downloadUrl; fileSize = p.fileSize
                                            currentIcon = p.iconUrl; pickedImage = null; currentProgramStickerUrl = p.stickerUrl; currentProgramStickerMime = p.stickerMime; programSticker = null; message = "Redaktə üçün seçildi."
                                        }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFFC928)) }
                                        IconButton(onClick = {
                                            db.collection("programs").document(p.id).delete().addOnSuccessListener {
                                                if (editingId == p.id) clearForm()
                                                message = "Proqram silindi."
                                            }.addOnFailureListener { error = "Silmək alınmadı: ${it.localizedMessage}" }
                                        }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF7777)) }
                                    }
                                }
                            }
                            TextButton(onClick = { FirebaseAuth.getInstance().signOut(); loggedIn = false; clearForm() }) { Text("Çıxış") }
                        }
                        if (message.isNotBlank()) Text(message, color = Color(0xFF8DE6A5), fontSize = 12.sp)
                        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7777), fontSize = 12.sp)
                    }
                }
            }

            if (designAdminOpen && isAuthorizedDevice && loggedIn) {
                Card(colors = CardDefaults.cardColors(containerColor = livePanel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CANLI DİZAYN İDARƏETMƏSİ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Bu ayrıca bölmə yalnız admin cihazında və admin hesabında görünür. Dəyişikliklər bütün istifadəçilərə tətbiq olunur.", color = Color.LightGray, fontSize = 12.sp)
                        OutlinedTextField(designBgHex, { designBgHex = it }, label = { Text("Arxa fon rəngi (#RRGGBB)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(designPanelHex, { designPanelHex = it }, label = { Text("Kart rəngi (#RRGGBB)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(designAccentHex, { designAccentHex = it }, label = { Text("Əsas vurğu rəngi (#RRGGBB)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = designGoldCards, onCheckedChange = { designGoldCards = it })
                            Text("Qızılı premium kart dizaynı")
                        }
                        Button(onClick = { saveLiveDesign() }, modifier = Modifier.fillMaxWidth()) { Text("Dizaynı hamıda yenilə") }
                        OutlinedButton(onClick = { designAdminOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Admin panelinə qayıt") }
                    }
                }
            }

            if (showDeviceInfo) {
                AlertDialog(
                    onDismissRequest = { showDeviceInfo = false },
                    title = { Text("Cihaz məlumatı") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                            Text("Cihaz kodu:")
                            Text(deviceId, fontWeight = FontWeight.Bold)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Cihaz kodu", deviceId))
                            showDeviceInfo = false
                        }) { Text("Kodu kopyala") }
                    },
                    dismissButton = { TextButton(onClick = { showDeviceInfo = false }) { Text("Bağla") } }
                )
            }

            Spacer(Modifier.height(22.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF303030)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().clickable { showPhoneInfo = !showPhoneInfo; newPhoneInfoNotice = false }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Image(bitmap = texnoInfoLogo, contentDescription = "TEXNO-MƏLUMAT loqosu", modifier = Modifier.size(78.dp), contentScale = ContentScale.Fit)
                        Column(Modifier.weight(1f)) {
                            Text("TEXNO-MƏLUMAT", color = Color(0xFFFFD76B), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Spacer(Modifier.height(7.dp))
                            Text(
                            if (showPhoneInfo) "Telefon məlumatları bölməsini bağlamaq üçün toxunun"
                            else "Telefonlar haqqında məlumatları oxumaq üçün bölməyə daxil olun",
                                color = Color(0xFFBDBDBD), fontSize = 13.sp, lineHeight = 19.sp
                            )
                        }
                    }
                    Text(if (showPhoneInfo) "↑" else "➜", color = Color(0xFFFFC928), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (showPhoneInfo) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("PAYLAŞIMLAR", color = Color(0xFFFFD76B), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (phoneInfos.isEmpty()) {
                            Text("Hələ telefon məlumatı paylaşılmayıb.", color = Color.LightGray)
                        }
                        phoneInfos.forEach { info ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF383838)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (info.imageUrl.isNotBlank()) AsyncImage(model = info.imageUrl, contentDescription = info.title, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit)
                                    if (info.stickerUrl.isNotBlank() && !info.body.contains(INLINE_STICKER_TOKEN)) StickerPreview(info.stickerUrl, info.stickerMime, Modifier.fillMaxWidth().height(220.dp))
                                    Text(info.title.ifBlank { "TEXNO-MƏLUMAT" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    if (info.body.isNotBlank()) TextWithInlineSticker(info.body, info.stickerUrl, info.stickerMime, Color(0xFFE0E0E0), 14.sp, 21.sp)
                                    if (loggedIn) TextButton(onClick = { db.collection("phone_information").document(info.id).delete() }) {
                                        Text("Sil", color = Color(0xFFFF7777))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!showPhoneInfo) {
            Spacer(Modifier.height(18.dp))
            if (!showGames && posts.isNotEmpty()) {
                Text("Kanal paylaşımları", color = Color(0xFFFFD76B), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                posts.forEach { post ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF292929)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(post.title + if (post.isVip) "  👑 VIP PREMIUM" else "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (post.version.isNotBlank()) Text("Versiya: ${post.version}", color = Color(0xFFFFC928), fontSize = 12.sp)
                            Text(post.description, color = Color(0xFFE0E0E0), fontSize = 14.sp)
                            if (post.features.isNotBlank()) Text(post.features, color = Color(0xFFE0E0E0), fontSize = 13.sp)
                            if (post.fileSize.isNotBlank()) Text("Fayl ölçüsü: ${post.fileSize}", color = Color.LightGray, fontSize = 12.sp)
                            if (post.androidRequirement.isNotBlank()) Text("Android: ${post.androidRequirement}", color = Color.LightGray, fontSize = 12.sp)
                            if (post.downloadUrl.isNotBlank()) Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.downloadUrl))) } catch (_: Exception) { error = "APK linki açıla bilmədi." } }) { Text("APK yüklə") }
                            if (post.channelUrl.isNotBlank()) OutlinedButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.channelUrl))) } catch (_: Exception) { error = "Kanal linki açıla bilmədi." } }) { Text("Telegram kanalına qoşul") }
                            if (loggedIn) Row {
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF303030)), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().clickable { showGames = !showGames }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🎮 OYUNLAR", color = Color(0xFFFFD76B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(if (showGames) "Oyunlar bölməsini bağlamaq üçün toxunun" else "Oyunların APK versiyalarına baxmaq üçün daxil olun", color = Color(0xFFBDBDBD), fontSize = 13.sp)
                    }
                    Text(if (showGames) "↑" else "➜", color = Color(0xFFFFC928), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (showGames) {
                Spacer(Modifier.height(10.dp))
                if (games.isEmpty()) Text("Hələ oyun əlavə edilməyib.", color = Color.LightGray)
                games.filter { it.isActive || loggedIn }.forEach { g ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF292929)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (g.iconUrl.isNotBlank()) ProgramIcon(g.iconUrl, g.name, 64)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(g.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    if (g.version.isNotBlank()) Text("Versiya: ${g.version}", color = Color(0xFFFFC928), fontSize = 12.sp)
                                }
                            }
                            if (g.description.isNotBlank()) Text(g.description, color = Color(0xFFE0E0E0), fontSize = 14.sp)
                            if (g.downloadUrl.isNotBlank()) Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(g.downloadUrl))) } catch (_: Exception) { error = "Oyun APK linki açıla bilmədi." } }) { Text("OYUNU YÜKLƏ (APK)") }
                        }
                    }
                }
            }
            if (!showGames) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Proqramlar", color = Color(0xFFFFD76B), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                if (newProgramNotice) Text("  🔔 YENİ", color = Color(0xFFFFD166), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            shown.forEach { p ->
                val isGoldBankCard = designGoldCards
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isGoldBankCard) Color(0xFF6B4B16) else Color(0xFF303030)),
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(
                                if (isGoldBankCard) Brush.linearGradient(
                                    listOf(Color(0xFFFFE8A3), Color(0xFFD5A63D), Color(0xFF805516), Color(0xFFE8C66D))
                                ) else Brush.linearGradient(listOf(Color(0xFF303030), Color(0xFF303030))),
                                RoundedCornerShape(28.dp)
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (isGoldBankCard) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TEXNO-TELEKOM", color = Color(0xFF4A310B), fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.weight(1f))
                                Text("✦ GOLD", color = Color(0xFF4A310B), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(width = 48.dp, height = 34.dp)
                                        .background(
                                            Brush.linearGradient(listOf(Color(0xFFFFF1C2), Color(0xFFB8892B))),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) { Text("▦", color = Color(0xFF65430D), fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.weight(1f))
                                Text("PREMIUM MEMBER", color = Color(0xFF4A310B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(74.dp).background(Color(0xFF383838), RoundedCornerShape(23.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (p.iconUrl.isNotBlank()) ProgramIcon(p.iconUrl, p.name, 58)
                                else Text("✦", color = Color(0xFFFFC928), fontSize = 38.sp)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = if (isGoldBankCard) Color(0xFF382507) else Color.White)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    if (p.version.isNotBlank()) "${p.category} • ${p.version}" else p.category,
                                    color = if (isGoldBankCard) Color(0xFF5B3E0D) else Color(0xFFBDBDBD), fontSize = 13.sp
                                )
                            }
                        }
                        TextWithInlineSticker(p.description, p.stickerUrl, p.stickerMime, if (isGoldBankCard) Color(0xFF4A310B) else Color(0xFFE0E0E0), 14.sp, 20.sp)
                        if (p.stickerUrl.isNotBlank() && !p.description.contains(INLINE_STICKER_TOKEN)) StickerPreview(p.stickerUrl, p.stickerMime, Modifier.fillMaxWidth().height(220.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (p.downloadUrl.isNotBlank()) {
                                Box(
                                    Modifier.weight(1f).height(54.dp)
                                        .background(
                                            if (isGoldBankCard) Brush.horizontalGradient(listOf(Color(0xFF8A5B12), Color(0xFFFFD76B), Color(0xFF9A6A18))) else Brush.horizontalGradient(listOf(Color(0xFFFFC928), Color(0xFFFFA000))),
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.downloadUrl))) }
                                            catch (_: Exception) { error = "Yükləmə linki açıla bilmədi." }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Yüklə", color = if (isGoldBankCard) Color(0xFF382507) else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            } else {
                                Text("Link əlavə edilməyib", color = Color(0xFFBDBDBD), modifier = Modifier.weight(1f), fontSize = 12.sp)
                            }
                            if (loggedIn) {
                                OutlinedButton(
                                    onClick = {
                                        editingId = p.id; name = p.name; category = p.category; version = p.version
                                        description = p.description; link = p.downloadUrl; fileSize = p.fileSize
                                        currentIcon = p.iconUrl; pickedImage = null; adminOpen = true
                                        message = "Redaktə üçün seçildi."
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD5A63D)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD76B)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                                ) { Text("Redaktə", fontWeight = FontWeight.Bold) }
                                Button(
                                    onClick = {
                                        db.collection("programs").document(p.id).delete().addOnSuccessListener {
                                            if (editingId == p.id) clearForm()
                                            message = "Proqram silindi."
                                        }.addOnFailureListener { error = "Silmək alınmadı: ${it.localizedMessage}" }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD5A63D),
                                        contentColor = Color(0xFF382507)
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                                ) { Text("Sil", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) Text("Proqram tapılmadı.", color = Color.LightGray)
            Spacer(Modifier.height(18.dp))
            }
            }
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "TEXNO-TELEKOM",
                    color = Color(0xFFFFD76B),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "PROQRAMİST { !*ОПТИМИСТ*! }",
                    color = Color(0xFFBDBDBD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
