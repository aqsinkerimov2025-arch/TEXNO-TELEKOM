package az.texnotelekom.app

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person

@Composable
fun BottomMenu(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("Esas", "Kateqoriya", "Favori", "Premium", "Profil")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Category, Icons.Filled.Favorite, Icons.Filled.Star, Icons.Filled.Person)
    NavigationBar {
        items.forEachIndexed { index, label ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(icons[index], contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}