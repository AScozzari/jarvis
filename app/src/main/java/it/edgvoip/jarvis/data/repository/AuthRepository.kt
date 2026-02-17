package it.edgvoip.jarvis.data.repository

import android.util.Log
import it.edgvoip.jarvis.data.api.ApiClient
import it.edgvoip.jarvis.data.api.JarvisApi
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.model.LoginRequest
import it.edgvoip.jarvis.data.model.LogoutRequest
import it.edgvoip.jarvis.data.model.RefreshRequest
import it.edgvoip.jarvis.data.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: JarvisApi,
    private val tokenManager: TokenManager
) {

    suspend fun login(
        email: String,
        password: String,
        tenantSlug: String,
        forceLogin: Boolean = false
    ): Result<User> {
        return try {
            val request = LoginRequest(
                email = email,
                password = password,
                forceLogin = if (forceLogin) true else null
            )
            val response = api.login(tenantSlug, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    val loginData = body.data
                    tokenManager.saveToken(loginData.token)
                    tokenManager.saveRefreshToken(loginData.refreshToken)
                    tokenManager.saveUserData(loginData.user)
                    val sipToSave = if (loginData.sipConfig != null && loginData.sipConfig.isValid()) loginData.sipConfig else null
                    tokenManager.saveSipConfig(sipToSave)
                    Log.d("AuthRepository", "Login: sipConfig from backend present=${loginData.sipConfig != null}, valid=${loginData.sipConfig?.isValid()}, saved=${sipToSave != null}")
                    tokenManager.saveApiBaseUrl(loginData.apiBaseUrl)
                    ApiClient.invalidate()
                    Result.success(loginData.user)
                } else {
                    Result.failure(Exception(body?.error ?: "Errore durante il login"))
                }
            } else {
                val code = response.code()
                if (code == 409) {
                    Result.failure(ForceLoginRequiredException())
                } else {
                    val errorBody = response.errorBody()?.string()
                    val message = try {
                        val gson = com.google.gson.Gson()
                        val errorResponse = gson.fromJson(errorBody, ErrorBody::class.java)
                        errorResponse?.error ?: "Credenziali non valide"
                    } catch (e: Exception) {
                        when (code) {
                            401 -> "Credenziali non valide"
                            403 -> "Accesso non autorizzato"
                            404 -> "Tenant non trovato"
                            429 -> "Troppi tentativi. Riprova più tardi"
                            else -> "Errore del server ($code)"
                        }
                    }
                    Result.failure(Exception(message))
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Impossibile connettersi al server. Controlla la connessione internet"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Timeout della connessione. Riprova"))
        } catch (e: Exception) {
            Result.failure(Exception("Errore di rete: ${e.localizedMessage}"))
        }
    }

    suspend fun refreshToken(): Result<String> {
        return try {
            val currentRefreshToken = tokenManager.getRefreshToken()
                ?: return Result.failure(Exception("Nessun refresh token disponibile"))

            val slug = tokenManager.getTenantSlug()
                ?: return Result.failure(Exception("Nessun tenant configurato"))

            val request = RefreshRequest(refreshToken = currentRefreshToken)
            val response = api.refreshToken(slug, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    tokenManager.saveToken(body.data.token)
                    tokenManager.saveRefreshToken(body.data.refreshToken)
                    Result.success(body.data.token)
                } else {
                    Result.failure(Exception(body?.error ?: "Errore nel refresh del token"))
                }
            } else {
                tokenManager.clearAll()
                Result.failure(Exception("Sessione scaduta. Effettua nuovamente il login"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Errore nel refresh del token: ${e.localizedMessage}"))
        }
    }

    suspend fun logout() {
        try {
            val refreshToken = tokenManager.getRefreshToken()
            val slug = tokenManager.getTenantSlug()
            if (refreshToken != null && slug != null) {
                val request = LogoutRequest(refreshToken = refreshToken)
                api.logout(slug, request)
            }
        } catch (_: Exception) {
        } finally {
            tokenManager.clearSession()
        }
    }

    fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    fun getCurrentUser(): User? {
        return tokenManager.getUserData()
    }

    fun getTenantSlug(): String {
        return tokenManager.getTenantSlug() ?: ""
    }
}

class ForceLoginRequiredException : Exception("Sessione attiva su un altro dispositivo")

private data class ErrorBody(
    val error: String? = null,
    val message: String? = null
)
