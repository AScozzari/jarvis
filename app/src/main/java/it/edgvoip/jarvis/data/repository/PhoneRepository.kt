package it.edgvoip.jarvis.data.repository

import android.util.Log
import it.edgvoip.jarvis.data.api.JarvisApi
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.db.CallLogDao
import it.edgvoip.jarvis.data.db.CallLogEntity
import it.edgvoip.jarvis.data.db.ContactDao
import it.edgvoip.jarvis.data.db.ContactEntity
import it.edgvoip.jarvis.data.model.SipConfig
import it.edgvoip.jarvis.sip.WebRtcPhoneManager
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneRepository @Inject constructor(
    private val api: JarvisApi,
    private val sipManager: WebRtcPhoneManager,
    private val callLogDao: CallLogDao,
    private val contactDao: ContactDao,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "PhoneRepository"
    }

    /**
     * Recupera la configurazione SIP per l'utente loggato.
     * L'API (autenticata con il token dell'utente) restituisce l'estensione interna e le credenziali
     * abbinati a quell'utente.
     */
    suspend fun getSipConfig(): Result<SipConfig> {
        return try {
            val slug = tokenManager.getTenantSlug()
                ?: return Result.failure(Exception("Nessun tenant configurato"))

            val response = api.getSipConfig(slug)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    val msg = body?.error ?: "Errore nel recupero della configurazione SIP (verifica formato risposta API)"
                    if (body != null && body.data == null) {
                        Log.w(TAG, "SIP config: success=true ma data=null. error=$msg")
                    }
                    Result.failure(Exception(msg))
                }
            } else {
                val code = response.code()
                val message = when (code) {
                    401 -> "Sessione scaduta. Effettua nuovamente il login"
                    403 -> "Non autorizzato ad accedere alla configurazione SIP"
                    404 -> "Configurazione SIP non trovata"
                    else -> "Errore del server ($code)"
                }
                Result.failure(Exception(message))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Impossibile connettersi al server. Controlla la connessione internet"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Timeout della connessione. Riprova"))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SIP config: ${e.message}", e)
            Result.failure(Exception("Errore di rete: ${e.localizedMessage}"))
        }
    }

    /**
     * Inizializza e registra l'estensione SIP usando solo la config salvata al login.
     * Non chiama l'API mobile-config (evita 500 e richieste inutili).
     */
    suspend fun initializeSip(): Result<Unit> {
        val config = tokenManager.getSipConfig()
        if (config == null || !config.isValid()) {
            Log.w(TAG, "Nessuna configurazione SIP disponibile (salvata al login). Verifica che il backend includa sipConfig nella risposta di login.")
            return Result.failure(Exception("Configurazione SIP non disponibile per questo account"))
        }
        val configResult = Result.success(config)
        return configResult.fold(
            onSuccess = { config ->
                val userExtension = tokenManager.getUserData()?.extension
                val sipConfig = if (!userExtension.isNullOrBlank()) {
                    config.copy(username = userExtension)
                } else {
                    config
                }
                sipManager.initialize(sipConfig)
                sipManager.register()
                Log.i(TAG, "SIP initialized and registration started for user extension ${sipConfig.sipUsername}@${sipConfig.effectiveRealm}")
                Result.success(Unit)
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to initialize SIP: ${error.message}")
                Result.failure(error)
            }
        )
    }

    fun getCallHistory(): Flow<List<CallLogEntity>> {
        return callLogDao.getAllCallLogs()
    }

    fun getRecentCalls(limit: Int = 50): Flow<List<CallLogEntity>> {
        return callLogDao.getRecentCallLogs(limit)
    }

    suspend fun syncCallHistory() {
        try {
            val slug = tokenManager.getTenantSlug() ?: return
            val response = api.getCallHistory(slug)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

                    val entities = body.data.map { record ->
                        val startTime = try {
                            val raw = record.startTime
                            val toParse = raw.replace(Regex("\\.\\d{3}Z?$"), "").replace("Z", "")
                            dateFormat.parse(toParse) ?: Date()
                        } catch (e: Exception) {
                            Date()
                        }
                        val caller = record.caller.ifBlank { "Sconosciuto" }
                        val callee = record.callee.ifBlank { "—" }
                        CallLogEntity(
                            caller = caller,
                            callee = callee,
                            startTime = startTime,
                            duration = record.duration.coerceAtLeast(0),
                            direction = record.directionNormalized.ifBlank { "unknown" },
                            disposition = record.dispositionNormalized.ifBlank { "unknown" }
                        )
                    }

                    callLogDao.deleteAllCallLogs()
                    callLogDao.insertCallLogs(entities)
                    Log.i(TAG, "Synced ${entities.size} call history records")
                }
            } else {
                Log.w(TAG, "Failed to sync call history: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing call history: ${e.message}", e)
        }
    }

    suspend fun syncContacts() {
        try {
            val slug = tokenManager.getTenantSlug() ?: return
            val response = api.getContacts(slug)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    val entities = body.data.map { contact ->
                        ContactEntity(
                            name = contact.name,
                            phone = contact.phone,
                            email = contact.email,
                            company = contact.company
                        )
                    }

                    contactDao.deleteAllContacts()
                    contactDao.insertContacts(entities)
                    Log.i(TAG, "Synced ${entities.size} contacts")
                }
            } else {
                Log.w(TAG, "Failed to sync contacts: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing contacts: ${e.message}", e)
        }
    }

    fun searchContacts(query: String): Flow<List<ContactEntity>> {
        return if (query.isBlank()) {
            contactDao.getAllContacts()
        } else {
            contactDao.searchContacts(query)
        }
    }

    fun getAllContacts(): Flow<List<ContactEntity>> {
        return contactDao.getAllContacts()
    }

    suspend fun findContactByNumber(number: String): ContactEntity? {
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        return try {
            val contacts = mutableListOf<ContactEntity>()
            contactDao.searchContacts(cleanNumber).collect { list ->
                contacts.addAll(list)
                return@collect
            }
            contacts.firstOrNull { contact ->
                val contactPhone = contact.phone?.replace(Regex("[^0-9+]"), "") ?: ""
                contactPhone == cleanNumber || contactPhone.endsWith(cleanNumber) || cleanNumber.endsWith(contactPhone)
            }
        } catch (e: Exception) {
            null
        }
    }
}
