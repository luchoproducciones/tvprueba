package ar.luis.canalestv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import kotlinx.coroutines.delay

private val Accent = Color(0xFFE5383B)
private val Panel = Color(0xF50E1116)
private val Muted = Color(0xFFA7B0BE)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        val resolver = ChannelResolver(this)
        val store = ChannelStore(this)
        val auth = AuthStore(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                // La sesión dura mientras la app está abierta: al volver a entrar, pide usuario y contraseña.
                var loggedIn by rememberSaveable { mutableStateOf(false) }
                if (!loggedIn) LoginScreen(auth) { loggedIn = true }
                else TvScreen(
                    store, resolver,
                    onOpenInYouTube = ::openInYouTube,
                    onLogout = { loggedIn = false },
                    onExit = ::finish,
                )
            }
        }
    }

    /** Plan B si un canal no deja reproducirse fuera de YouTube: abrir la app de YouTube. */
    private fun openInYouTube(channel: Channel) {
        val base = Intent(Intent.ACTION_VIEW, channel.liveUri)
        for (intent in listOf(
            Intent(base).setPackage("com.google.android.youtube.tv"),
            Intent(base).setPackage("com.google.android.youtube"),
            base,
        )) {
            try { startActivity(intent); return } catch (_: ActivityNotFoundException) { }
        }
    }
}

/**
 * Al abrir: el primer canal a pantalla completa.
 * OK, Menú o Izquierda abren el menú lateral para cambiar de canal; Atrás lo cierra.
 * Canal+ / Canal- del control cambian directo al siguiente o anterior.
 */
@Composable
fun TvScreen(
    store: ChannelStore,
    resolver: ChannelResolver,
    onOpenInYouTube: (Channel) -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    var mine by remember { mutableStateOf(store.load()) }
    val channels = CHANNELS + mine
    var current by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    var channelId by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    if (current >= channels.size) current = 0
    val channel = channels[current]

    LaunchedEffect(current) {
        channelId = null
        problem = null
        playing = false
        showInfo = true
        // Si el canal es un link a una transmisión puntual no hace falta buscar el ID del canal.
        channelId = if (channel.videoId != null) "" else resolver.channelId(channel)
        if (channelId == null) problem = "No se pudo encontrar el canal. Revisá la conexión."
        delay(5000)
        showInfo = false
        // Si a los 20 segundos no arrancó, avisar en vez de dejar la pantalla negra.
        delay(15000)
        if (!playing && problem == null)
            problem = "${channel.name} no arranca.\nOK: abrir en YouTube · Izquierda: cambiar canal"
    }
    LaunchedEffect(menuOpen) { if (!menuOpen) rootFocus.requestFocus() }

    BackHandler {
        when {
            adding -> adding = false
            menuOpen -> menuOpen = false
            else -> onExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Sin fondo propio: el negro lo pone la ventana (ver onCreate), así no tapa el video.
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { e ->
                if (menuOpen || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.Menu, Key.DirectionLeft -> {
                        if (problem != null && e.key != Key.DirectionLeft && e.key != Key.Menu) onOpenInYouTube(channel)
                        else menuOpen = true
                        true
                    }
                    Key.ChannelUp, Key.DirectionUp -> { current = (current + 1) % channels.size; true }
                    Key.ChannelDown, Key.DirectionDown -> { current = (current - 1 + channels.size) % channels.size; true }
                    else -> { showInfo = true; false }
                }
            },
    ) {
        channelId?.let { id ->
            LivePlayer(id, channel.videoId, Modifier.fillMaxSize(), onPlaying = { playing = true; problem = null }) { code ->
                problem = if (code == 101 || code == 150 || code == 152 || code == 153)
                    "Este canal no permite verse dentro de la app (error $code).\nOK: abrir en YouTube · Izquierda: cambiar canal"
                else "No se pudo reproducir (error $code)."
            }
        }

        if (!playing && problem == null) {
            Text(
                "Cargando ${channel.name}…",
                Modifier.align(Alignment.Center),
                color = Color(0xFFC9D1DC), fontSize = 24.sp,
            )
        }

        problem?.let {
            Text(
                it,
                Modifier.align(Alignment.Center).padding(48.dp),
                color = Color.White, fontSize = 26.sp, lineHeight = 36.sp,
            )
        }

        AnimatedVisibility(showInfo && !menuOpen, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            InfoBar(channel)
        }

        AnimatedVisibility(
            menuOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            ChannelMenu(
                channels, current,
                onPick = { picked -> current = picked; menuOpen = false },
                onAdd = { adding = true },
                onLogout = onLogout,
                onRemove = { ch ->
                    val wasCurrent = channels.getOrNull(current) == ch
                    mine = mine - ch
                    store.save(mine)
                    if (wasCurrent) current = 0
                },
            )
        }

        if (adding) {
            AddChannelPanel(
                index = mine.size,
                onCancel = { adding = false; menuOpen = false },
                onSave = { ch ->
                    mine = mine + ch
                    store.save(mine)
                    adding = false
                    menuOpen = false
                    current = CHANNELS.size + mine.size - 1
                },
            )
        }
    }
}

@Composable
private fun InfoBar(channel: Channel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ChannelChip(channel, 120.dp, 68.dp, 26)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(Accent, RoundedCornerShape(50)))
                    Text("EN VIVO", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(channel.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("OK: cambiar canal", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChannelChip(channel: Channel, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, size: Int) {
    Box(
        Modifier.size(w, h).background(Color(channel.colorHex), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(channel.short, color = Color.White, fontSize = size.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChannelMenu(
    channels: List<Channel>,
    current: Int,
    onPick: (Int) -> Unit,
    onAdd: () -> Unit,
    onLogout: () -> Unit,
    onRemove: (Channel) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val rows = remember(channels) {
        buildList<Pair<String?, Int>> {
            var lastCat: String? = null
            channels.forEachIndexed { i, c ->
                if (c.category != lastCat) { add(c.category to -1); lastCat = c.category }
                add(null to i)
            }
        }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Panel)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("Cambiar canal", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows) { (header, index) ->
                if (header != null) {
                    Text(
                        header.uppercase(),
                        Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                        color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    )
                } else {
                    val ch = channels[index]
                    val watching = index == current
                    ListItem(
                        selected = watching,
                        onClick = { onPick(index) },
                        // Mantener apretado OK sobre un canal propio lo borra.
                        onLongClick = if (ch.custom) ({ onRemove(ch) }) else null,
                        modifier = if (watching) Modifier.focusRequester(focus) else Modifier,
                        leadingContent = { ChannelChip(ch, 60.dp, 34.dp, 15) },
                        headlineContent = { Text(ch.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
                        supportingContent = if (ch.custom) ({ Text("Mantené OK para borrar", fontSize = 12.sp) }) else null,
                        trailingContent = {
                            if (watching) Text("VIENDO", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = Color(0xFF1B2029),
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color(0xFF0E1116),
                        ),
                    )
                }
            }
            item {
                ListItem(
                    selected = false,
                    onClick = onAdd,
                    modifier = Modifier.padding(top = 12.dp),
                    leadingContent = {
                        Box(
                            Modifier.size(60.dp, 34.dp).border(2.dp, Muted, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    },
                    headlineContent = { Text("Agregar canal", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color(0xFF0E1116),
                    ),
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = onLogout,
                    headlineContent = { Text("Cerrar sesión", fontSize = 17.sp, color = Muted) },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color(0xFF0E1116),
                    ),
                )
            }
        }
    }
}

/** Pantalla para agregar un canal pegando o escribiendo su URL de YouTube. */
@Composable
private fun AddChannelPanel(index: Int, onCancel: () -> Unit, onSave: (Channel) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(720.dp).background(Color(0xFF171B22), RoundedCornerShape(24.dp)).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Agregar canal", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Pegá la dirección del canal de YouTube, por ejemplo youtube.com/@todonoticias",
                color = Muted, fontSize = 18.sp,
            )
            Field("URL o @usuario del canal", url, Modifier.focusRequester(first)) { url = it; error = null }
            Field("Nombre que se va a mostrar (opcional)", name) { name = it }
            error?.let { Text(it, color = Accent, fontSize = 18.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    val ch = ChannelStore.parse(url, name, index)
                    if (ch == null) error = "No reconozco esa dirección. Tiene que tener @usuario o /channel/UC…"
                    else onSave(ch)
                }) { Text("Guardar y ver", fontSize = 20.sp) }
                OutlinedButton(onClick = onCancel) { Text("Cancelar", fontSize = 20.sp) }
            }
        }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF0E1116), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF2A303B), RoundedCornerShape(12.dp))
                .padding(16.dp),
        )
    }
}
