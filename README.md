# Canales en Vivo (Android TV)

App para Android TV para ver canales argentinos que transmiten en vivo por YouTube (TN, LN+, C5N, A24, etc.).

## Uso con el control
- La primera vez pide crear usuario y contraseña (se guardan solo en la tele, la contraseña con hash).
  Después los pide cada vez que se abre la app. "Cerrar sesión" está al final del menú lateral.
- Al abrir, arranca el primer canal a pantalla completa.
- OK, Menú o Izquierda: abre el menú lateral para cambiar de canal. Atrás lo cierra.
- Arriba/Abajo o Canal+/Canal-: siguiente o anterior canal directo.
- Si un canal no permite verse fuera de YouTube, OK lo abre en la app de YouTube.

## Cómo funciona
- Interfaz: Kotlin + Jetpack Compose for TV (navegable con el control remoto).
- Reproducción: reproductor oficial de YouTube (IFrame Player API) en un WebView, con `live_stream?channel=<ID>`,
  que muestra el vivo actual del canal. No hace falta clave de API; publicidad y restricciones las maneja YouTube.
- El ID `UC...` de cada canal se averigua solo desde su @handle la primera vez (ChannelResolver) y queda guardado.
  También se puede escribir fijo en `Channel.kt` (campo `channelId`).

## Agregar canales
- Desde la tele: menú lateral → "Agregar canal" → pegar la URL (youtube.com/@usuario o youtube.com/channel/UC…).
  Quedan en "Mis canales". Mantener OK sobre uno de ellos lo borra.
- Para cambiar la lista fija: editar `app/src/main/java/ar/luis/canalestv/Channel.kt` (lista `CHANNELS`).

## Compilar
Abrir la carpeta en Android Studio (Ladybug o más nuevo) y correr en un emulador Android TV o en el televisor
(con depuración ADB activada). Android Studio genera el Gradle wrapper si falta (`gradle wrapper`).
