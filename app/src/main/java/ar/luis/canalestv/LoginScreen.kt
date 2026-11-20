package ar.luis.canalestv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

/**
 * Pantalla de inicio de sesión. La primera vez pide crear usuario y contraseña;
 * después, cada vez que se abre la app, pide ingresarlos.
 */
@Composable
fun LoginScreen(auth: AuthStore, onLoggedIn: () -> Unit) {
    val creating = remember { !auth.hasAccount }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    fun submit() {
        error = when {
            user.isBlank() || pass.isBlank() -> "Completá usuario y contraseña."
            creating && pass.length < 4 -> "La contraseña tiene que tener al menos 4 caracteres."
            creating && pass != pass2 -> "Las contraseñas no coinciden."
            !creating && !auth.check(user, pass) -> "Usuario o contraseña incorrectos."
            else -> null
        }
        if (error == null) {
            if (creating) auth.create(user, pass)
            onLoggedIn()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E1116)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).background(Color(0xFF171B22), RoundedCornerShape(24.dp)).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(44.dp).background(Color(0xFFE5383B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", color = Color.White, fontSize = 20.sp) }
                Text("Canales en Vivo", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                if (creating) "Creá tu usuario para usar la app" else "Iniciá sesión",
                color = Color(0xFFC9D1DC), fontSize = 20.sp,
            )
            Field("Usuario", user, Modifier.focusRequester(first)) { user = it; error = null }
            Field("Contraseña", pass, password = true) { pass = it; error = null }
            if (creating) Field("Repetir contraseña", pass2, password = true) { pass2 = it; error = null }
            error?.let { Text(it, color = Color(0xFFFF6B6E), fontSize = 18.sp) }
            Button(onClick = ::submit) {
                Text(if (creating) "Crear y entrar" else "Entrar", fontSize = 20.sp)
            }
        }
    }
}
