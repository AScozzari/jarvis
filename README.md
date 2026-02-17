# Jarvis - App Android EDG VoIP

App Android per la piattaforma EDG VoIP con telefonia SIP, Agenti AI ElevenLabs e notifiche push.

## Funzionalita

### Telefono SIP
- Dialer numerico premium con tema scuro
- Cronologia chiamate con indicatori di direzione
- Rubrica contatti CRM con ricerca
- Schermata chiamata attiva con muto, attesa, altoparlante, DTMF, trasferimento
- Registrazione SIP automatica tramite configurazione dal backend
- Servizio foreground per mantenere la connessione SIP attiva

### Agente AI (ElevenLabs)
- Chat testuale con agenti AI configurati nel backend
- Nomi conversazioni auto-generati dal primo messaggio (primi 40 caratteri)
- Funzioni rinomina e elimina conversazione
- Timestamp su ogni messaggio e conversazione
- Modalita vocale WebRTC con ElevenLabs
- Cache offline tramite Room Database

### Notifiche Push
- Firebase Cloud Messaging per notifiche in tempo reale
- Canali separati: Chiamate, Lead, Messaggi, Sistema
- Badge contatore non lette
- Deep linking alle sezioni rilevanti

### Sicurezza
- Autenticazione JWT con refresh token automatico
- Login biometrico (impronta/riconoscimento facciale) dopo primo accesso
- Token salvati in EncryptedSharedPreferences
- Gestione sessioni concorrenti (force login)

## Requisiti

- Android Studio Hedgehog (2023.1.1) o superiore
- JDK 17
- Android SDK 34
- Dispositivo o emulatore Android 8.0+ (API 26)

## Setup Progetto

### 1. Clona e apri il progetto
```bash
cd packages/android-app
```
Apri la cartella `packages/android-app` in Android Studio come progetto.

### 2. Configura Firebase
1. Vai alla [Firebase Console](https://console.firebase.google.com/)
2. Crea un progetto o usa uno esistente
3. Aggiungi un'app Android con package name `it.edgvoip.jarvis`
4. Scarica `google-services.json` e copialo in `packages/android-app/app/`
5. Abilita Cloud Messaging nel progetto Firebase

### 3. PJSIP (Opzionale - per telefonia SIP nativa)
La libreria PJSIP richiede compilazione nativa. I passi sono:
1. Scarica PJSIP source da https://www.pjsip.org/
2. Compila per Android (ARM64, ARMv7, x86_64) con NDK
3. Copia le librerie .so in `app/src/main/jniLibs/`
4. L'app funziona anche senza PJSIP (le funzionalita SIP sono in modalita placeholder)

### 4. Build APK
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (richiede keystore configurato)
./gradlew assembleRelease
```

L'APK debug si trova in: `app/build/outputs/apk/debug/app-debug.apk`

### 5. Installa su dispositivo
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Architettura

```
app/src/main/java/it/edgvoip/jarvis/
  JarvisApplication.kt          # Application class (Hilt)
  MainActivity.kt               # Activity principale con navigazione

  ai/
    ElevenLabsWebRtcManager.kt  # Gestione WebRTC ElevenLabs

  data/
    api/
      JarvisApi.kt              # Interfaccia Retrofit
      ApiClient.kt              # Client HTTP con auth
      TokenManager.kt           # Gestione token crittografati
    db/
      JarvisDatabase.kt         # Room Database
      Entities.kt               # Entita Room
      Daos.kt                   # Data Access Objects
    model/
      Models.kt                 # Data classes
    repository/
      AuthRepository.kt         # Autenticazione
      AiAgentRepository.kt      # Chat AI
      NotificationRepository.kt # Notifiche
      PhoneRepository.kt        # Telefonia

  di/
    AppModule.kt                # Modulo Hilt DI

  notifications/
    JarvisMessagingService.kt   # Servizio FCM

  sip/
    SipManager.kt               # Wrapper PJSIP
    SipService.kt               # Servizio foreground SIP

  ui/
    navigation/
      JarvisNavigation.kt       # Route di navigazione
      JarvisNavHost.kt          # NavHost Compose
    screens/
      LoginScreen.kt            # Schermata login
      LoginViewModel.kt         # ViewModel login
      BiometricHelper.kt        # Helper autenticazione biometrica
      PhoneScreen.kt            # Tab telefono (dialer, recenti, contatti)
      PhoneViewModel.kt         # ViewModel telefono
      InCallScreen.kt           # Schermata chiamata attiva
      AiAgentScreen.kt          # Tab agente AI (lista chat, chat, voce)
      AiAgentViewModel.kt       # ViewModel agente AI
      NotificationsScreen.kt    # Tab notifiche
      NotificationsViewModel.kt # ViewModel notifiche
      SettingsScreen.kt         # Impostazioni
      SettingsViewModel.kt      # ViewModel impostazioni
      ProfileScreen.kt          # Profilo utente
    theme/
      Color.kt                  # Palette colori
      Theme.kt                  # Tema Material 3
      Type.kt                   # Tipografia
```

## Stack Tecnologico

| Componente | Tecnologia |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigazione | Navigation Compose |
| DI | Hilt |
| HTTP | Retrofit + OkHttp |
| Database locale | Room |
| Preferenze | DataStore + EncryptedSharedPreferences |
| Push | Firebase Cloud Messaging |
| Biometria | AndroidX Biometric |
| Immagini | Coil |
| VoIP | PJSIP (compilazione esterna) |
| AI Voce | ElevenLabs WebRTC (WebView) |

## Backend Endpoints

L'app comunica con il backend EDG VoIP (`https://edgvoip.it`) tramite questi endpoint:

| Metodo | Endpoint | Descrizione |
|---|---|---|
| POST | `/{slug}/login` | Login con email/password |
| POST | `/{slug}/refresh-token` | Rinnovo token JWT |
| POST | `/{slug}/logout` | Logout |
| GET | `/{slug}/mobile/sip/mobile-config` | Configurazione SIP per l'estensione dell'utente |
| POST | `/{slug}/mobile/devices/register` | Registrazione dispositivo FCM |
| DELETE | `/{slug}/mobile/devices/unregister` | Rimozione dispositivo FCM |
| GET | `/{slug}/mobile/devices` | Lista dispositivi registrati |
| GET | `/{slug}/chatbot/agents` | Lista agenti AI disponibili |
| GET | `/{slug}/crm/contacts` | Contatti CRM |
| GET | `/{slug}/cdr` | Cronologia chiamate |
| GET | `/{slug}/notifications` | Lista notifiche |
| PUT | `/{slug}/notifications/{id}/read` | Segna notifica come letta |
