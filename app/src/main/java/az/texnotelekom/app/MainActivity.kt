package az.texnotelekom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg = Color(0xFF080610)
private val panel = Color(0xFF151020)
private val accent = Color(0xFFAA4CFF)
data class AppItem(val id: Int, val name: String, val category: String, val version: String, val description: String, val link: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TexnoTelekomApp() }
    }
}

@Composable
fun TexnoTelekomApp() {
    var query by remember { mutableStateOf("") }
    var adminOpen by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("TEXNO-TELEKOM") }
    var welcome by remember { mutableStateOf("✅ TEXNO-TELEKOM RƏSMİ TƏTBİQİNƏ XOŞ GƏLMİSİNİZ...") }
    var info by remember { mutableStateOf("TEXNO-TELEKOM RƏSMİ SAYT MƏRKƏZİ") }
    var line1 by remember { mutableStateOf("1️⃣ TƏTBİQLƏRİN PREMİUM VƏ PRO VERSİYALARI 👨‍💻") }
    var line2 by remember { mutableStateOf("2️⃣ GOOGLE PLAY'DA OLMAYAN TƏTBİQLƏRİ") }
    var line3 by remember { mutableStateOf("3️⃣ ŞİKAYƏT VƏ REKLAM ÜÇÜN ƏLAQƏ") }
    var phone by remember { mutableStateOf("+994558053305") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var nextId by remember { mutableIntStateOf(3) }
    val apps = remember { mutableStateListOf(
        AppItem(1, "YouTube Music Premium", "youtube_premium", "v7.x", "Reklamsız musiqi və oflayn dinləmə.", ""),
        AppItem(2, "Telegram", "messaging", "", "Mesajlaşma və fayl paylaşımı.", "https://telegram.org/")
    ) }
    val filtered = apps.filter { "${it.name} ${it.category} ${it.description}".contains(query, true) }

    MaterialTheme(colorScheme = darkColorScheme(
        background = bg, surface = panel, primary = accent,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF251039), bg, bg))).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("✦", color = Color(0xFFD5B0FF), fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("TEXNO-TELEKOM", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("PREMIUM • PRO • TEXNOLOGİYA", color = Color.LightGray, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = { adminOpen = !adminOpen; error = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                Text(if (adminOpen) "Admin panelini bağla  −" else "Admin paneli  +", fontWeight = FontWeight.Bold)
            }
            if (adminOpen) {
                Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!loggedIn) {
                            Text("Admin girişi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            OutlinedTextField(password, { password = it }, label = { Text("Admin şifrəsi") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                            Text("Təhlükəsiz admin girişi server bağlantısı qoşulduqdan sonra aktiv ediləcək.", color = Color(0xFFFFC46B), fontSize = 12.sp)
                            if (error.isNotBlank()) Text(error, color = Color.Red)
                            Button(onClick = { error = "Admin autentifikasiyası hələ serverə qoşulmayıb." }) { Text("Daxil ol") }
                        } else {
                            Text("Sayt yazılarını düzəlt", fontWeight = FontWeight.Bold)
                            listOf(
                                "Əsas başlıq" to title,
                                "Qarşılama yazısı" to welcome,
                                "Məlumat başlığı" to info,
                                "1-ci məlumat sətri" to line1,
                                "2-ci məlumat sətri" to line2,
                                "3-cü məlumat sətri" to line3,
                                "WhatsApp nömrəsi" to phone
                            ).forEach { (label, value) -> OutlinedTextField(value, { v -> when(label) {
                                "Əsas başlıq" -> title = v; "Qarşılama yazısı" -> welcome = v; "Məlumat başlığı" -> info = v
                                "1-ci məlumat sətri" -> line1 = v; "2-ci məlumat sətri" -> line2 = v
                                "3-cü məlumat sətri" -> line3 = v; else -> phone = v
                            } }, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) }
                            Divider(color = Color.DarkGray)
                            Text("Proqram əlavə et", fontWeight = FontWeight.Bold)
                            OutlinedTextField(name, { name = it }, label = { Text("Proqramın adı") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(category, { category = it }, label = { Text("Kateqoriya") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(version, { version = it }, label = { Text("Versiya") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(description, { description = it }, label = { Text("Təsvir") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(link, { link = it }, label = { Text("Yükləmə linki") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { if (name.isNotBlank() && category.isNotBlank()) { apps.add(AppItem(nextId++, name, category, version, description, link)); name=""; category=""; version=""; description=""; link="" } }, enabled = name.isNotBlank() && category.isNotBlank()) { Text("Proqramı əlavə et") }
                            Text("Redaktə/silmə və şəkil yükləmə server və fayl yaddaşı qoşulandan sonra tamamlanacaq.", color = Color.LightGray, fontSize = 12.sp)
                            TextButton(onClick = { loggedIn = false }) { Text("Çıxış") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(title, color = Color(0xFFCA9BFF), fontSize = 29.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(welcome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(18.dp))
            Text(info, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(line1, color = Color.White, fontWeight = FontWeight.Bold)
            Text(line2, color = Color.White, fontWeight = FontWeight.Bold)
            Text(line3, color = Color.White, fontWeight = FontWeight.Bold)
            Text(phone, color = Color(0xFF40C8FF), fontSize = 19.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Proqram axtar...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            Text("✦ Proqramlar", color = Color(0xFFE7D5FF), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(filtered, key = { it.id }) { app ->
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            Text("✦  ${app.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${app.category}  ${app.version}", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(app.description, color = Color(0xFFD0C7DC), fontSize = 13.sp)
                            if (app.link.isNotBlank()) Text(app.link, color = Color(0xFF55C8FF), fontSize = 12.sp)
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text("Axtarışa uyğun proqram tapılmadı", color = Color.LightGray) }
            }
        }
    }
}
