package ar.luis.listasm3u

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {

    private lateinit var player: TvPlayer
    private var onFile: (Uri?) -> Unit = {}

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { onFile(it) }
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { onFile(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))

        // Pantalla = video (abajo) + interfaz Compose (arriba, transparente mientras se ve un canal).
        val root = FrameLayout(this)
        player = TvPlayer(this, root)
        val ui = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(ui)
        setContentView(root)

        val store = PlaylistStore(this)
        ui.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(store, player, pickFile = ::pickFile, onExit = ::finish)
            }
        }
    }

    /** Abre el explorador de archivos de la tele. Devuelve false si la tele no tiene ninguno. */
    private fun pickFile(callback: (Uri?) -> Unit): Boolean {
        onFile = callback
        return try {
            openDocument.launch(arrayOf("*/*")); true
        } catch (_: ActivityNotFoundException) {
            try { getContent.launch("*/*"); true } catch (_: ActivityNotFoundException) { false }
        }
    }

    override fun onStop() { super.onStop(); player.pause() }
    override fun onStart() { super.onStart(); if (::player.isInitialized) player.resume() }
    override fun onDestroy() { super.onDestroy(); player.release() }
}
