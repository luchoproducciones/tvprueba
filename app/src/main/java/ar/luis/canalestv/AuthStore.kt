package ar.luis.canalestv

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cuenta local de la app (usuario y contraseña guardados en la tele).
 * La contraseña nunca se guarda tal cual: se guarda un hash PBKDF2 con sal aleatoria.
 */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("cuenta", Context.MODE_PRIVATE)

    val hasAccount: Boolean get() = prefs.contains("user")

    fun create(user: String, pass: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("user", user.trim())
            .putString("salt", b64(salt))
            .putString("hash", b64(hash(pass, salt)))
            .apply()
    }

    fun check(user: String, pass: String): Boolean {
        val savedUser = prefs.getString("user", null) ?: return false
        val salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP)
        val saved = Base64.decode(prefs.getString("hash", ""), Base64.NO_WRAP)
        return savedUser.equals(user.trim(), ignoreCase = true) &&
            MessageDigest.isEqual(saved, hash(pass, salt))
    }

    /** Borra la cuenta (y con eso se vuelve a pedir crear usuario). */
    fun reset() = prefs.edit().clear().apply()

    private fun hash(pass: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(pass.toCharArray(), salt, 20_000, 256))
            .encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}
