package az.texnotelekom.app

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

private const val TEXNO_LOGO_BASE64 = "${b64}"

@Composable
fun TexnoTelekomLogo() {
    val bitmap = remember {
        val bytes = Base64.decode(TEXNO_LOGO_BASE64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "TEXNO-TELEKOM logo",
            modifier = Modifier.size(112.dp),
            contentScale = ContentScale.Fit
        )
    }
}
