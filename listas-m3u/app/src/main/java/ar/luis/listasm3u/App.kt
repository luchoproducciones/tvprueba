package ar.luis.listasm3u

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF171B22)
private val Panel = Color(0xF50E1116)
private val Accent = Color(0xFF2EC4B6)
private val Muted = Color(0xFFA7B0BE)

private const val ALL = "\u0000todos"
private const val FAVS = "\u0000favs"
private const val SEARCH = "\u0000buscar"

private enum class Screen { Lists, Channels, Player }

/** Pide foco con una pequeña espera, para que el elemento ya esté en pantalla. */
@Composable
private fun AutoFocus(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) { delay(80); runCatching { requester.requestFocus() } }
}

@Composable
private fun itemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    selectedContainerColor = Color(0xFF1B2029),
    focusedContainerColor = Color.White,
    focusedContentColor = Bg,
    focusedSelectedContainerColor = Color.White,
    focusedSelectedContentColor = Bg,
)

@Composable
fun App(store: PlaylistStore, player: TvPlayer, pickFile: ((Uri?) -> Unit) -> Boolean, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf(store.lists()) }
    var screen by remember { mutableStateOf(Screen.Lists) }
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var favs by remember { mutableStateOf(store.favorites()) }
    var group by remember { mutableStateOf(ALL) }
    var query by remember { mutableStateOf("") }
    var queue by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val groups = remember(channels) { channels.groupBy { it.group } }
    fun channelsOf(g: String): List<M3uChannel> = when (g) {
        ALL -> channels
        FAVS -> channels.filter { it.url in favs }
        SEARCH -> channels.filter { it.name.contains(query, ignoreCase = true) }
        else -> groups[g].orEmpty()
    }

    fun open(pl: Playlist, autoplay: Boolean) {
        scope.launch {
            busy = "Cargando ${pl.name}…"
            store.load(pl)
                .onSuccess { list ->
                    channels = list
                    playlist = pl
                    store.lastListId = pl.id
                    group = ALL
                    screen = Screen.Channels
                    val last = store.lastChannelUrl
                    val ch = if (autoplay) list.firstOrNull { it.url == last } else null
                    if (ch != null) {
                        group = ch.group
                        queue = list.filter { it.group == ch.group }
                        index = queue.indexOf(ch).coerceAtLeast(0)
                        screen = Screen.Player
                    }
                }
                .onFailure { message = "No se pudo abrir la lista.\n${it.message ?: ""}" }
            busy = null
        }
    }

    // Al abrir la app: vuelve a la última lista y al último canal que se estaba viendo.
    LaunchedEffect(Unit) {
        store.lastListId?.let { id -> lists.firstOrNull { it.id == id }?.let { open(it, autoplay = true) } }
    }
    LaunchedEffect(screen) { if (screen != Screen.Player) player.stop() }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Lists -> ListsScreen(
                lists = lists,
                onOpen = { open(it, autoplay = false) },
                onAddUrl = { name, url, done ->
                    scope.launch {
                        busy = "Bajando la lista…"
                        store.addFromUrl(name, url)
                            .onSuccess { lists = store.lists(); done(); open(it, autoplay = false) }
                            .onFailure { message = "No se pudo agregar la lista.\n${it.message ?: ""}" }
                        busy = null
                    }
                },
                onAddFile = {
                    val ok = pickFile { uri ->
                        if (uri != null) scope.launch {
                            busy = "Leyendo el archivo…"
                            store.addFromFile(uri)
                                .onSuccess { lists = store.lists(); open(it, autoplay = false) }
                                .onFailure { message = "No se pudo agregar el archivo.\n${it.message ?: ""}" }
                            busy = null
                        }
                    }
                    if (!ok) message = "Esta tele no tiene explorador de archivos.\n" +
                        "Instalá uno desde la tienda de apps o agregá la lista por dirección (URL)."
                },
                onRemove = { pl -> store.remove(pl); lists = store.lists() },
                onExit = onExit,
                blocked = busy != null || message != null,
            )

            Screen.Channels -> ChannelsScreen(
                title = playlist?.name.orEmpty(),
                groups = groups,
                favCount = channels.count { it.url in favs },
                group = group,
                query = query,
                shown = channelsOf(group),
                favs = favs,
                focusUrl = queue.getOrNull(index)?.url,
                onGroup = { group = it },
                onSearch = { query = it; group = SEARCH },
                onPlay = { list, i -> queue = list; index = i; screen = Screen.Player },
                onFav = { favs = store.toggleFavorite(it.url) },
                onBack = { screen = Screen.Lists },
                blocked = busy != null || message != null,
            )

            Screen.Player -> if (queue.isNotEmpty()) PlayerScreen(
                player = player,
                store = store,
                queue = queue,
                index = index.coerceIn(0, queue.lastIndex),
                onIndex = { index = it },
                onBack = { screen = Screen.Channels },
            ) else LaunchedEffect(Unit) { screen = Screen.Channels }
        }

        // Mientras carga, Atrás no hace nada (si no, cerraría la app).
        BackHandler(busy != null) { }
        busy?.let {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Text(it, color = Color.White, fontSize = 26.sp)
            }
        }
        message?.let { MessagePanel(it) { message = null } }
    }
}

// ---------------------------------------------------------------------------------------------
// Mis listas
// ---------------------------------------------------------------------------------------------

@Composable
private fun ListsScreen(
    lists: List<Playlist>,
    onOpen: (Playlist) -> Unit,
    onAddUrl: (name: String, url: String, done: () -> Unit) -> Unit,
    onAddFile: () -> Unit,
    onRemove: (Playlist) -> Unit,
    onExit: () -> Unit,
    blocked: Boolean,
) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Playlist?>(null) }
    val first = remember { FocusRequester() }
    if (!adding && removing == null && !blocked) AutoFocus(first, lists.size)

    BackHandler(!blocked) {
        when {
            adding -> adding = false
            removing != null -> removing = null
            else -> onExit()
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(18.dp).background(Accent, RoundedCornerShape(5.dp)))
                Text("Listas M3U", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                if (lists.isEmpty()) "Todavía no agregaste ninguna lista. Agregá la tuya por dirección o desde un archivo."
                else "Elegí una lista. Mantené apretado OK sobre una lista para borrarla.",
                color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            LazyColumn(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(lists) { i, pl ->
                    ListItem(
                        selected = false,
                        onClick = { onOpen(pl) },
                        onLongClick = { removing = pl },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        leadingContent = { Badge(pl.name, 64.dp, 40.dp) },
                        headlineContent = { Text(pl.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(pl.url ?: "Desde archivo", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = itemColors(),
                    )
                }
                itemsIndexed(listOf("url", "file")) { i, kind ->
                    ListItem(
                        selected = false,
                        onClick = { if (kind == "url") adding = true else onAddFile() },
                        modifier = Modifier
                            .padding(top = if (i == 0) 16.dp else 0.dp)
                            .then(if (lists.isEmpty() && i == 0) Modifier.focusRequester(first) else Modifier),
                        leadingContent = {
                            Box(
                                Modifier.size(64.dp, 40.dp).border(2.dp, Muted, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        },
                        headlineContent = {
                            Text(
                                if (kind == "url") "Agregar lista por dirección (URL)" else "Agregar lista desde un archivo",
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            )
                        },
                        colors = itemColors(),
                    )
                }
            }
        }

        if (adding) AddUrlPanel(onCancel = { adding = false }) { name, url -> onAddUrl(name, url) { adding = false } }
        removing?.let { pl ->
            ConfirmPanel(
                "¿Borrar la lista \"${pl.name}\"?",
                onYes = { onRemove(pl); removing = null },
                onNo = { removing = null },
            )
        }
    }
}

@Composable
private fun AddUrlPanel(onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    AutoFocus(first)

    Overlay {
        Text("Agregar lista por dirección", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Escribí la dirección de tu lista M3U, por ejemplo http://servidor.com/lista.m3u", color = Muted, fontSize = 18.sp)
        Field("Dirección (URL) de la lista", url, Modifier.focusRequester(first)) { url = it; error = null }
        Field("Nombre para reconocerla (opcional)", name) { name = it }
        error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 18.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                if (url.isBlank()) error = "Falta la dirección." else onSave(name, url)
            }) { Text("Guardar y abrir", fontSize = 20.sp) }
            OutlinedButton(onClick = onCancel) { Text("Cancelar", fontSize = 20.sp) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Canales de una lista
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChannelsScreen(
    title: String,
    groups: Map<String, List<M3uChannel>>,
    favCount: Int,
    group: String,
    query: String,
    shown: List<M3uChannel>,
    favs: Set<String>,
    focusUrl: String?,
    onGroup: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPlay: (List<M3uChannel>, Int) -> Unit,
    onFav: (M3uChannel) -> Unit,
    onBack: () -> Unit,
    blocked: Boolean,
) {
    var searching by remember { mutableStateOf(false) }
    BackHandler(!blocked) { if (searching) searching = false else onBack() }

    // Grupos del costado: Buscar, Favoritos, Todos y los grupos de la lista.
    val side = remember(groups, favCount, query) {
        buildList {
            add(SEARCH to if (query.isBlank()) "Buscar canal" else "Buscar: $query")
            if (favCount > 0) add(FAVS to "Favoritos ($favCount)")
            add(ALL to "Todos (${groups.values.sumOf { it.size }})")
            groups.forEach { (g, l) -> add(g to "$g (${l.size})") }
        }
    }
    val focusIndex = shown.indexOfFirst { it.url == focusUrl }.takeIf { it >= 0 }
    val channelFocus = remember { FocusRequester() }
    val groupFocus = remember { FocusRequester() }
    if (!searching && !blocked) {
        if (shown.isNotEmpty()) AutoFocus(channelFocus, group) else AutoFocus(groupFocus, group)
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Mantené OK sobre un canal para marcarlo como favorito", color = Muted, fontSize = 16.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                LazyColumn(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(side) { _, (key, label) ->
                        val sel = key == group
                        ListItem(
                            selected = sel,
                            onClick = { if (key == SEARCH) searching = true else onGroup(key) },
                            modifier = if (sel) Modifier.focusRequester(groupFocus) else Modifier,
                            headlineContent = {
                                Text(label, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            },
                            trailingContent = { if (sel) Box(Modifier.size(8.dp).background(Accent, RoundedCornerShape(50))) },
                            colors = itemColors(),
                        )
                    }
                }
                key(group, query) {
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex ?: 0)
                    if (shown.isEmpty()) {
                        Text(
                            if (group == SEARCH) "No hay canales con ese nombre." else "No hay canales acá.",
                            color = Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 16.dp),
                        )
                    } else LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(shown, key = { i, c -> "$i|${c.url}" }) { i, ch ->
                            ListItem(
                                selected = false,
                                onClick = { onPlay(shown, i) },
                                onLongClick = { onFav(ch) },
                                modifier = if (i == (focusIndex ?: 0)) Modifier.focusRequester(channelFocus) else Modifier,
                                leadingContent = { Logo(ch, 84.dp, 48.dp) },
                                headlineContent = {
                                    Text(ch.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = if (group == ALL || group == FAVS || group == SEARCH) ({
                                    Text(ch.group, fontSize = 13.sp, maxLines = 1)
                                }) else null,
                                trailingContent = { if (ch.url in favs) Text("★", color = Color(0xFFFFC857), fontSize = 22.sp) },
                                colors = itemColors(),
                            )
                        }
                    }
                }
            }
        }

        if (searching) SearchPanel(query, onCancel = { searching = false }) { searching = false; onSearch(it) }
    }
}

@Composable
private fun SearchPanel(initial: String, onCancel: () -> Unit, onSearch: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val first = remember { FocusRequester() }
    AutoFocus(first)
    Overlay {
        Text("Buscar canal", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Field("Parte del nombre del canal", text, Modifier.focusRequester(first)) { text = it }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onSearch(text.trim()) }) { Text("Buscar", fontSize = 20.sp) }
            OutlinedButton(onClick = onCancel) { Text("Cancelar", fontSize = 20.sp) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Reproductor a pantalla completa
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlayerScreen(
    player: TvPlayer,
    store: PlaylistStore,
    queue: List<M3uChannel>,
    index: Int,
    onIndex: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val ch = queue[index]
    var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(ch) {
        playing = false
        error = null
        player.onPlaying = { playing = true; error = null }
        player.onBuffering = { }
        player.onError = { error = it }
        player.play(ch)
        store.lastChannelUrl = ch.url
        infoTick++
    }
    LaunchedEffect(infoTick) { showInfo = true; delay(5000); showInfo = false }
    LaunchedEffect(menuOpen) { if (!menuOpen) { delay(80); runCatching { rootFocus.requestFocus() } } }

    BackHandler { if (menuOpen) menuOpen = false else onBack() }

    fun step(d: Int) = onIndex((index + d + queue.size) % queue.size)

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { e ->
                if (menuOpen || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft, Key.Menu -> { menuOpen = true; true }
                    Key.ChannelUp, Key.DirectionUp -> { step(1); true }
                    Key.ChannelDown, Key.DirectionDown -> { step(-1); true }
                    else -> { infoTick++; false }
                }
            },
    ) {
        if (!playing && error == null) {
            Text("Cargando ${ch.name}…", Modifier.align(Alignment.Center), color = Color(0xFFC9D1DC), fontSize = 24.sp)
        }
        error?.let {
            Column(
                Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(ch.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(it, color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
                Text("▲▼ probar otro canal · OK: lista de canales", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }

        AnimatedVisibility(showInfo && !menuOpen, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xB3000000)).padding(horizontal = 48.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Logo(ch, 128.dp, 72.dp)
                Column(Modifier.weight(1f)) {
                    Text("${index + 1} / ${queue.size} · ${ch.group}", color = Muted, fontSize = 16.sp, maxLines = 1)
                    Text(ch.name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    player.quality?.takeIf { playing }?.let { Text(it, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    Text("▲▼ cambiar · OK lista · Atrás salir", color = Color.White, fontSize = 16.sp)
                }
            }
        }

        AnimatedVisibility(menuOpen, enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut()) {
            QuickMenu(queue, index) { onIndex(it); menuOpen = false }
        }
    }
}

@Composable
private fun QuickMenu(queue: List<M3uChannel>, current: Int, onPick: (Int) -> Unit) {
    val focus = remember { FocusRequester() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    AutoFocus(focus)
    Column(Modifier.fillMaxHeight().width(460.dp).background(Panel).padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text(queue[current].group, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(queue, key = { i, c -> "$i|${c.url}" }) { i, ch ->
                val watching = i == current
                ListItem(
                    selected = watching,
                    onClick = { onPick(i) },
                    modifier = if (watching) Modifier.focusRequester(focus) else Modifier,
                    leadingContent = { Logo(ch, 64.dp, 36.dp) },
                    headlineContent = { Text(ch.name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { if (watching) Text("VIENDO", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = itemColors(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Piezas comunes
// ---------------------------------------------------------------------------------------------

/** Logo del canal (de la lista) o, si no tiene, sus iniciales. */
@Composable
private fun Logo(ch: M3uChannel, w: Dp, h: Dp) {
    Box(Modifier.size(w, h).background(Color(0xFF232935), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        if (ch.logo != null) {
            AsyncImage(
                model = ch.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Text(initials(ch.name), color = Color.White, fontSize = (h.value * 0.36f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Badge(name: String, w: Dp, h: Dp) {
    Box(Modifier.size(w, h).background(Accent, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Text(initials(name), color = Bg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun initials(name: String): String =
    name.split(' ', '-', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }

@Composable
private fun Overlay(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(760.dp).background(Card, RoundedCornerShape(24.dp)).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
private fun MessagePanel(text: String, onOk: () -> Unit) {
    val focus = remember { FocusRequester() }
    AutoFocus(focus)
    BackHandler { onOk() }
    Overlay {
        Text(text, color = Color.White, fontSize = 22.sp, lineHeight = 32.sp)
        Button(onClick = onOk, modifier = Modifier.focusRequester(focus)) { Text("Aceptar", fontSize = 20.sp) }
    }
}

@Composable
private fun ConfirmPanel(text: String, onYes: () -> Unit, onNo: () -> Unit) {
    val focus = remember { FocusRequester() }
    AutoFocus(focus)
    Overlay {
        Text(text, color = Color.White, fontSize = 24.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onNo, modifier = Modifier.focusRequester(focus)) { Text("No", fontSize = 20.sp) }
            Button(onClick = onYes) { Text("Sí, borrar", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = modifier
                .fillMaxWidth()
                .background(Bg, RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF2A303B), RoundedCornerShape(12.dp))
                .padding(16.dp),
        )
    }
}
