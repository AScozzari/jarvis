package it.edgvoip.jarvis.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import it.edgvoip.jarvis.data.model.SipConfig
import it.edgvoip.jarvis.data.model.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)
    private val gson = Gson()

    private val _sessionExpired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<String> = _sessionExpired.asSharedFlow()

    fun emitSessionExpired(reason: String = "Sessione scaduta") {
        Log.w("TokenManager", "Session expired: $reason")
        _sessionExpired.tryEmit(reason)
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("TokenManager", "EncryptedSharedPreferences non disponibile, uso SharedPreferences normale", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveRefreshToken(refreshToken: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply()
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun saveUserData(user: User) {
        val json = gson.toJson(user)
        prefs.edit().putString(KEY_USER_DATA, json).apply()
    }

    fun getUserData(): User? {
        val json = prefs.getString(KEY_USER_DATA, null) ?: return null
        return try {
            gson.fromJson(json, User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getTenantSlug(): String? {
        return getUserData()?.tenantSlug
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /** Credenziali salvate per accesso con impronta (email, password, tenantSlug). */
    fun saveBiometricCredentials(email: String, password: String, tenantSlug: String) {
        prefs.edit()
            .putString(KEY_BIOMETRIC_EMAIL, email)
            .putString(KEY_BIOMETRIC_PASSWORD, password)
            .putString(KEY_BIOMETRIC_TENANT, tenantSlug)
            .apply()
    }

    /** Restituisce le credenziali per login con impronta, o null se non salvate. */
    fun getBiometricCredentials(): Triple<String, String, String>? {
        val email = prefs.getString(KEY_BIOMETRIC_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_BIOMETRIC_PASSWORD, null) ?: return null
        val tenant = prefs.getString(KEY_BIOMETRIC_TENANT, null) ?: return null
        if (email.isBlank() || password.isBlank() || tenant.isBlank()) return null
        return Triple(email, password, tenant)
    }

    fun clearBiometricCredentials() {
        prefs.edit()
            .remove(KEY_BIOMETRIC_EMAIL)
            .remove(KEY_BIOMETRIC_PASSWORD)
            .remove(KEY_BIOMETRIC_TENANT)
            .apply()
    }

    /** Svuota sessione (logout) mantenendo impronta e credenziali per prossimo accesso. */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_DATA)
            .remove(KEY_SIP_CONFIG)
            .remove(KEY_API_BASE_URL)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    /** Base URL per le API (da risposta di login). Se presente, usata al posto della default. */
    fun saveApiBaseUrl(url: String?) {
        prefs.edit().putString(KEY_API_BASE_URL, url).apply()
    }

    fun getApiBaseUrl(): String? {
        return prefs.getString(KEY_API_BASE_URL, null)?.takeIf { it.isNotBlank() }
    }

    /** Config SIP dalla risposta di login. Usata per registrazione senza chiamata mobile-config. */
    fun saveSipConfig(config: SipConfig?) {
        if (config == null) {
            prefs.edit().remove(KEY_SIP_CONFIG).apply()
        } else {
            prefs.edit().putString(KEY_SIP_CONFIG, gson.toJson(config)).apply()
        }
    }

    fun getSipConfig(): SipConfig? {
        val json = prefs.getString(KEY_SIP_CONFIG, null) ?: return null
        return try {
            gson.fromJson(json, SipConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** Svuota tutto (incluso impronta e credenziali salvate). */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "jarvis_secure_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_EMAIL = "biometric_email"
        private const val KEY_BIOMETRIC_PASSWORD = "biometric_password"
        private const val KEY_BIOMETRIC_TENANT = "biometric_tenant"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_SIP_CONFIG = "sip_config"
    }
}
