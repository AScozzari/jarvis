# Audit completo – App Android Jarvis (EDG VoIP)

**Data audit:** 16 febbraio 2025  
**Progetto:** `it.edgvoip.jarvis` – client VoIP/SIP con AI Agent, notifiche push, biometrico

---

## 1. Riepilogo esecutivo

L’app segue un’architettura moderna (Compose, Hilt, Room, Retrofit) e ha diverse buone pratiche (token in EncryptedSharedPreferences, refresh token, foreground service per SIP). Sono però presenti **problemi critici** (stato di autenticazione sbagliato, logging in chiaro, cleartext traffic) e varie aree da migliorare (ProGuard, dipendenze, Room, permessi).

| Area           | Esito   | Priorità |
|----------------|---------|----------|
| Sicurezza      | Critica | Alta     |
| Autenticazione | Critica | Alta     |
| Architettura   | Buona   | Media   |
| Dipendenze     | Attenzione | Media |
| Qualità codice | Buona   | Bassa   |

---

## 2. Sicurezza

### 2.1 Critico: logging HTTP a livello BODY

**File:** `app/src/main/java/it/edgvoip/jarvis/data/api/ApiClient.kt`

```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

- **Problema:** In tutte le build (anche release) vengono loggati body di richieste e risposte. Rischio di esporre **token JWT, refresh token, password, dati personali** in logcat e in eventuali log di crash.
- **Azione:** Usare `Level.BODY` solo in debug (es. `if (BuildConfig.DEBUG)`) e in release usare `Level.NONE` o non aggiungere l’interceptor.

### 2.2 Critico: Cleartext traffic abilitato

**File:** `app/src/main/AndroidManifest.xml`

```xml
android:usesCleartextTraffic="true"
```

- **Problema:** Consente traffico HTTP non cifrato. Su Android 9+ è bloccato di default; abilitarlo espone a MITM e intercettazione.
- **Azione:** Rimuovere l’attributo (o impostarlo a `false`) e usare solo HTTPS. Se serve HTTP solo in debug, usare un `network_security_config` con cleartext limitato a localhost/debug.

### 2.3 Medio: Credenziali SIP in log

**File:** `app/src/main/java/it/edgvoip/jarvis/sip/SipManager.kt`

- Vengono loggati `config.server`, `config.port`, `config.username`, `config.realm` (non la password). In ambiente sensibile è meglio evitare di loggare anche lo username in produzione.
- **Azione:** In release non loggare dati di configurazione SIP; in debug eventualmente mascherare parte dello username.

### 2.4 Positivo: gestione token

- **TokenManager** usa `EncryptedSharedPreferences` (AES256-GCM/SIV) per token, refresh token e dati utente.
- **AuthRepository** gestisce refresh token, 401 e logout in modo coerente.
- **ApiClient** ha un `Authenticator` che fa refresh su 401 e riprova la richiesta.

### 2.5 Positivo: biometrico

- **BiometricHelper** usa `BIOMETRIC_STRONG` e `DEVICE_CREDENTIAL`; non si sostituisce alla sicurezza del server, ma l’uso è corretto per sblocco locale.

---

## 3. Autenticazione e flusso utente

### 3.1 Critico: stato “autenticato” non legato al token

**File:** `app/src/main/java/it/edgvoip/jarvis/MainActivity.kt`

```kotlin
var isAuthenticated by rememberSaveable { mutableStateOf(true) }
```

- **Problema:** All’avvio `isAuthenticated` è sempre `true`. La destinazione iniziale diventa **Phone** invece di **Login**, quindi:
  - Al primo avvio (senza token) l’utente vede la schermata Telefono senza essere loggato.
  - Le chiamate API falliranno con 401; l’esperienza è incoerente e confusa.
- **Azione:** Derivare lo stato di autenticazione da una fonte unica di verità (es. `AuthRepository.isLoggedIn()` o uno StateFlow esposto da un ViewModel/SavedStateHandle). Esempio: alla prima composizione leggere `authRepository.isLoggedIn()` e impostare `isAuthenticated` di conseguenza; aggiornare `isAuthenticated` quando l’utente fa login/logout.

### 3.2 Medio: TokenManager istanziato a mano nella UI

**File:** `app/src/main/java/it/edgvoip/jarvis/ui/screens/LoginScreen.kt`

```kotlin
val tokenManager = TokenManager(context)
tokenManager.setBiometricEnabled(true)
```

- **Problema:** Si crea una seconda istanza di `TokenManager` invece di usare quella iniettata da Hilt. Rischio di inconsistenza (due “fonti” delle stesse preferenze) e violazione dell’architettura (DI).
- **Azione:** Iniettare `TokenManager` nel ViewModel (o passarlo al composable) e chiamare `tokenManager.setBiometricEnabled(true)` dal ViewModel dopo il successo del biometrico; oppure esporre un’azione `enableBiometric()` nel ViewModel che usa il `TokenManager` iniettato.

### 3.3 Basso: notifiche conteggio hardcoded

**File:** `MainActivity.kt`

```kotlin
val notificationCount by remember { mutableIntStateOf(3) }
```

- Il badge delle notifiche è fissato a 3; non riflette il numero reale.
- **Azione:** Leggere il conteggio da `NotificationRepository`/ViewModel (es. notifiche non lette) e collegare il badge a quello stato.

---

## 4. AndroidManifest e permessi

### 4.1 Permessi

- Permessi dichiarati sono coerenti con le funzionalità: INTERNET, RECORD_AUDIO, CAMERA, USE_BIOMETRIC, FOREGROUND_SERVICE_*, POST_NOTIFICATIONS, ecc.
- **Suggerimento:** Verificare che RECORD_AUDIO, CAMERA e USE_BIOMETRIC siano richiesti solo quando servono (runtime permissions già gestiti dove necessario).

### 4.2 Backup

- `android:allowBackup="true"`: i dati possono essere inclusi nel backup. Con `EncryptedSharedPreferences` i token sono cifrati, ma per policy aziendali molto restrittive si può valutare `allowBackup="false"` o backup esclusivo di dati non sensibili.

### 4.3 Exported

- `MainActivity` è `android:exported="true"` (necessario per launcher).
- I service sono `android:exported="false"`: corretto.

---

## 5. Build e release

### 5.1 ProGuard / R8

**File:** `app/build.gradle.kts`

- In release è abilitato `isMinifyEnabled = true` e sono referenziati:
  - `proguard-android-optimize.txt`
  - `proguard-rules.pro`
- **Problema:** Nel progetto non risulta un file `app/proguard-rules.pro`. Senza regole custom, Retrofit/Gson/Hilt/Room potrebbero richiedere keep rules per evitare crash in release.
- **Azione:** Creare `app/proguard-rules.pro` con le regole necessarie per:
  - Retrofit, OkHttp, Gson (modelli API, annotazioni)
  - Hilt/Dagger
  - Room (entity, DAO)
  - Modelli serializzati (es. `it.edgvoip.jarvis.data.model.**`)
  - Eventuali classi native PJSIP

### 5.2 Versione e namespace

- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`: allineati e aggiornati.
- `namespace = "it.edgvoip.jarvis"` e `applicationId` coerenti.

---

## 6. Architettura e dipendenze

### 6.1 Struttura

- Separazione chiara: `data` (api, db, repository, model), `di`, `ui` (screens, navigation, theme), `sip`, `notifications`, `ai`.
- Uso di Hilt per DI, ViewModel con `@HiltViewModel`, Repository come singleton.
- Navigation con Compose e `JarvisNavHost` ben definito.

### 6.2 ApiClient come object

- **ApiClient** è un `object` con stato mutabile (`tokenManager`, `retrofit`, `okHttpClient`) e metodi che ricostruiscono il client in base al `TokenManager`.
- In `AppModule` si usa `ApiClient.getOkHttpClient(tokenManager)` e poi si costruisce un `Retrofit` separato con `ApiClient.BASE_URL`; l’uso è funzionante ma un po’ ibrido (singleton + parametro).
- **Suggerimento:** Valutare di fornire `OkHttpClient` e `Retrofit` solo tramite Hilt (con qualifier se servono più client) e rimuovere lo stato globale da `ApiClient`, per test e chiarezza.

### 6.3 Room

- **JarvisDatabase:** `exportSchema = false` semplifica il build ma disincentiva migration controllate.
- **Azione:** Per evoluzioni future dello schema, abilitare `exportSchema = true`, salvare gli schema in una cartella (es. `schemas/`) e definire migration invece di `fallbackToDestructiveMigration()` in produzione, dove i dati devono essere conservati.

### 6.4 Dipendenze (versioni)

- Compose BOM 2024.02.00, Material3, Navigation, Lifecycle, Room 2.6.1, Retrofit 2.9.0, OkHttp 4.12.0, Hilt 2.50, Firebase BOM 32.7.2: in generale aggiornate.
- **gradle.properties:** `android.enableJetifier=true` – Jetifier è deprecato; se non servono più librerie che richiedono Jetifier, si può provare a disattivarlo e verificare il build.

---

## 7. Qualità del codice

### 7.1 Punti di forza

- Uso di `StateFlow`/`Flow` per stato reattivo (SIP, chiamate, UI).
- Gestione errori in `AuthRepository` con `Result` e messaggi utente chiari (409, 401, timeout, ecc.).
- Uso di `collectAsStateWithLifecycle` nella UI dove rilevante.
- Servizi (SipService, JarvisMessagingService) con scope e lifecycle gestiti (WakeLock, audio focus, canali notifiche).

### 7.2 WebView (ElevenLabs)

**File:** `app/src/main/java/it/edgvoip/jarvis/ai/ElevenLabsWebRtcManager.kt`

- `setJavaScriptEnabled = true` e `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` sono necessari per il widget ma aumentano la superficie di attacco.
- **Suggerimento:** Caricare solo URL fidati (es. dominio ElevenLabs); evitare di esporre contenuti arbitrari nella stessa WebView.

### 7.3 SipService e notifiche

- Foreground service con tipo `phoneCall` e notifiche per chiamata in arrivo/attiva sono configurati in modo coerente.
- Uso di `PendingIntent.FLAG_IMMUTABLE` dove appropriato.

---

## 8. Test e manutenibilità

- Dipendenze di test presenti: JUnit 4, AndroidX Test, Espresso, Compose UI test.
- Non è stato verificato l’effettivo utilizzo (test unitari/integrazione/UI). Consigliato:
  - Test unitari su `AuthRepository`, `TokenManager` (con Context mock), e su ViewModel (login, logout, refresh).
  - Test di integrazione su navigazione (da Login a Phone dopo login).
  - Verifica che in release il logging non esponga dati sensibili (anche tramite ispezione di build release).

---

## 9. Checklist interventi prioritari

| # | Priorità | Azione |
|---|----------|--------|
| 1 | Alta     | Derivare `isAuthenticated` da `AuthRepository.isLoggedIn()` (o stato equivalente) in `MainActivity`/JarvisApp e rimuovere il default `true`. |
| 2 | Alta     | Limitare `HttpLoggingInterceptor.Level.BODY` solo a `BuildConfig.DEBUG`; in release usare `NONE` o non aggiungere l’interceptor. |
| 3 | Alta     | Rimuovere o restringere `usesCleartextTraffic="true"` e usare solo HTTPS (o network config dedicato per debug). |
| 4 | Media    | Creare `app/proguard-rules.pro` e aggiungere keep rules per Retrofit, Gson, Hilt, Room e modelli API. |
| 5 | Media    | In `LoginScreen`, usare `TokenManager` iniettato (tramite ViewModel) invece di `TokenManager(context)` per `setBiometricEnabled`. |
| 6 | Media    | Collegare il badge notifiche nella bottom bar al conteggio reale (repository/ViewModel). |
| 7 | Bassa    | Valutare `exportSchema = true` per Room e migration esplicite al posto di `fallbackToDestructiveMigration` per release. |
| 8 | Bassa    | Ridurre o eliminare i log in produzione che contengono dati di configurazione SIP (es. username). |
| 9 | Bassa    | Valutare la rimozione di `android.enableJetifier=true` se non più necessaria. |

---

## 10. Conclusioni

L’app ha una base solida (architettura, cifratura token, biometrico, foreground service SIP) ma **due punti critici vanno corretti prima del rilascio**:

1. **Stato di autenticazione:** deve riflettere il reale stato di login (token presente e valido), non un valore iniziale `true`.
2. **Sicurezza rete e log:** niente cleartext in produzione e niente logging BODY in release, per evitare esposizione di credenziali e dati personali.

Dopo questi interventi e l’aggiunta delle regole ProGuard, l’app sarà in uno stato adeguato per un utilizzo in produzione dal punto di vista sicurezza e coerenza del flusso utente.
