package az.texnotelekom.app

import android.content.Intent
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private val bg = Color(0xFF080610)
private val panel = Color(0xFF151020)
private val accent = Color(0xFFAA4CFF)
private const val ADMIN_EMAIL = "aqsin.kerimov.2025@gmail.com"
private const val ADMIN_UID = "FflCErahavNIb8ZXsvHIyrxZYZH3"

data class AppItem(
    val id: String, val name: String, val category: String, val version: String,
    val description: String, val downloadUrl: String, val iconUrl: String,
    val isActive: Boolean, val fileSize: String
)


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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TexnoTelekomApp() }
    }
}

@Composable
fun TexnoTelekomApp() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var query by remember { mutableStateOf("") }
    var adminOpen by remember { mutableStateOf(false) }
    var showDeviceInfo by remember { mutableStateOf(false) }
    var revealDeviceCodeButton by remember { mutableStateOf(false) }
    val deviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Tapılmadı" }
    var loggedIn by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(ADMIN_EMAIL) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("0 MB") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var currentIcon by remember { mutableStateOf("") }
    var pickedImage by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val programs = remember { mutableStateListOf<AppItem>() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedImage = uri
        if (uri != null) message = "Şəkil qalereyadan seçildi."
    }

    DisposableEffect(db) {
        val listener = db.collection("programs").addSnapshotListener { snap, e ->
            if (e != null) error = "Firebase xətası: ${e.localizedMessage}"
            else if (snap != null) {
                programs.clear()
                snap.documents.forEach { d ->
                    programs.add(AppItem(
                        d.id, d.getString("name") ?: "", d.getString("category") ?: "",
                        d.getString("version") ?: "", d.getString("description") ?: "",
                        d.getString("download_url") ?: "", d.getString("icon_url") ?: "",
                        d.getBoolean("is_active") ?: true, d.getString("file_size") ?: "0 MB"
                    ))
                }
            }
        }
        onDispose { listener.remove() }
    }

    fun clearForm() {
        name = ""; category = ""; version = ""; description = ""; link = ""
        fileSize = "0 MB"; editingId = null; currentIcon = ""; pickedImage = null
    }

    fun writeProgram(iconUrl: String) {
        val values = hashMapOf<String, Any>(
            "name" to name.trim(), "category" to category.trim(), "version" to version.trim(),
            "description" to description.trim(), "download_url" to link.trim(),
            "icon_url" to iconUrl, "is_active" to true, "file_size" to fileSize.trim(),
            "updated_at" to FieldValue.serverTimestamp()
        )
        val id = editingId
        if (id == null) values["created_at"] = FieldValue.serverTimestamp()
        val task = if (id == null) db.collection("programs").add(values)
                   else db.collection("programs").document(id).set(values, SetOptions.merge())
        task.addOnSuccessListener {
            message = if (id == null) "Proqram əlavə edildi." else "Proqram yeniləndi."
            clearForm(); busy = false
        }.addOnFailureListener {
            error = "Yadda saxlamaq alınmadı: ${it.localizedMessage}"; busy = false
        }
    }

    fun saveProgram() {
        if (name.isBlank() || category.isBlank()) { error = "Ad və kateqoriya mütləqdir."; return }
        if (link.isNotBlank() && !link.startsWith("https://") && !link.startsWith("http://")) {
            error = "Yükləmə linki http:// və ya https:// ilə başlamalıdır."; return
        }
        error = ""; message = ""; busy = true
        val uri = pickedImage
        if (uri == null) {
            writeProgram(currentIcon)
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
            writeProgram(encoded)
        } catch (e: Exception) {
            error = "Şəkli hazırlamaq alınmadı: " + (e.localizedMessage ?: "naməlum xəta")
            busy = false
        }
    }

    val shown = programs.filter { (it.isActive || loggedIn) && "${it.name} ${it.category} ${it.description}".contains(query, true) }

    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent, onBackground = Color.White, onSurface = Color.White)) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF251039), bg, bg)))
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.fillMaxWidth().clickable { revealDeviceCodeButton = !revealDeviceCodeButton },
                contentAlignment = Alignment.Center
            ) {
                TexnoTelekomLogo()
            }
            if (revealDeviceCodeButton) {
                TextButton(onClick = { showDeviceInfo = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cihaz kodunu göstər", color = Color(0xFFBBAACB), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = { adminOpen = !adminOpen; error = ""; message = "" },
                modifier = Modifier.fillMaxWidth().height(92.dp),
                shape = RoundedCornerShape(30.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, accent),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (adminOpen) "Admin panelini bağla" else "Admin paneli",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("+", color = accent, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(42.dp))
            Text(
                "TEXNO-TELEKOM",
                style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF557CFF), Color(0xFFB63CDB)))),
                fontSize = 31.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(36.dp))
            Text(
                "✅ TEXNO-TELEKOM RƏSMİ SAYTINA XOŞ GƏLMİSİNİZ...",
                color = Color.White, fontSize = 19.sp, lineHeight = 27.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = Color(0xFF40334D), thickness = 1.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                "TEXNO-TELEKOM RƏSMİ SAYT MƏRKƏZİ",
                color = Color.White, fontSize = 21.sp, lineHeight = 29.sp,
                fontWeight = FontWeight.ExtraBold
            )

            if (adminOpen) {
                Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
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
                            Text(if (editingId == null) "Proqram əlavə et" else "Proqramı redaktə et", fontWeight = FontWeight.Bold)
                            OutlinedTextField(name, { name = it }, label = { Text("Proqramın adı") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(category, { category = it }, label = { Text("Kateqoriya") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(version, { version = it }, label = { Text("Versiya") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(description, { description = it }, label = { Text("Təsvir") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
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
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF21182E)), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (p.iconUrl.isNotBlank()) ProgramIcon(p.iconUrl, p.name, 40)
                                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text(p.name, fontWeight = FontWeight.Bold)
                                            Text("${p.category} • ${p.version}", fontSize = 11.sp, color = Color.LightGray)
                                        }
                                        IconButton(onClick = {
                                            editingId = p.id; name = p.name; category = p.category; version = p.version
                                            description = p.description; link = p.downloadUrl; fileSize = p.fileSize
                                            currentIcon = p.iconUrl; pickedImage = null; message = "Redaktə üçün seçildi."
                                        }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFD5B0FF)) }
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

            Spacer(Modifier.height(20.dp))
            Text("Proqramlar", color = Color(0xFFE7D5FF), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                query, { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Proqram axtar...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp)
            )
            Spacer(Modifier.height(18.dp))
            shown.forEach { p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF100B19)),
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(74.dp).background(Color(0xFF21152E), RoundedCornerShape(23.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (p.iconUrl.isNotBlank()) ProgramIcon(p.iconUrl, p.name, 58)
                                else Text("✦", color = Color(0xFFD9B5FF), fontSize = 38.sp)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    if (p.version.isNotBlank()) "${p.category} • ${p.version}" else p.category,
                                    color = Color(0xFFBBAACB), fontSize = 13.sp
                                )
                            }
                        }
                        Text(p.description, color = Color(0xFFD0C7DC), fontSize = 14.sp, lineHeight = 20.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (p.downloadUrl.isNotBlank()) {
                                Box(
                                    Modifier.weight(1f).height(54.dp)
                                        .background(
                                            Brush.horizontalGradient(listOf(Color(0xFF278CFF), Color(0xFFE12BC8))),
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.downloadUrl))) }
                                            catch (_: Exception) { error = "Yükləmə linki açıla bilmədi." }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Yüklə", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            } else {
                                Text("Link əlavə edilməyib", color = Color(0xFFBBAACB), modifier = Modifier.weight(1f), fontSize = 12.sp)
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
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                                ) { Text("Redaktə", fontWeight = FontWeight.Bold) }
                                Button(
                                    onClick = {
                                        db.collection("programs").document(p.id).delete().addOnSuccessListener {
                                            if (editingId == p.id) clearForm()
                                            message = "Proqram silindi."
                                        }.addOnFailureListener { error = "Silmək alınmadı: ${it.localizedMessage}" }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF641B3C)),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                                ) { Text("Sil", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) Text("Proqram tapılmadı.", color = Color.LightGray)
            Spacer(Modifier.height(28.dp))
        }
    }
}
