# Guida – Aprire e usare il progetto Jarvis in Android Studio

## 1. Aprire il progetto

1. Avvia **Android Studio**.
2. Scegli **File → Open** (oppure "Open" dalla schermata di benvenuto).
3. Vai in **`C:\Users\a.scozzari\Desktop\android-app`**.
4. Seleziona la **cartella `android-app`** (quella che contiene `build.gradle.kts` e la cartella `app`) e clicca **OK**.
5. Se chiede di fidarsi del progetto (Gradle), clicca **Trust Project**.

---

## 2. Sync Gradle

- Dopo l’apertura, Android Studio di solito avvia da solo il **Gradle Sync**.
- Se non parte: **File → Sync Project with Gradle Files** (oppure l’icona dell’elefante con la freccia nella toolbar).
- Attendi che finisca (in basso: "Gradle sync finished"). Se compaiono errori, vedi la sezione **Problemi comuni** sotto.

---

## 3. Configurare SDK e dispositivo

- **SDK**: Il progetto usa **compileSdk 35** e **targetSdk 35**. Se manca l’SDK 35:
  - **File → Settings** (o **Android Studio → Settings** su Mac) → **Languages & Frameworks → Android SDK**.
  - Scheda **SDK Platforms**: spunta **Android 15.0 (API 35)** e applica.
- **Emulatore o dispositivo**:
  - **Emulatore**: **Tools → Device Manager** → **Create Device** → scegli un modello (es. Pixel 7) → **Next** → scegli un system image con API 35 (o 34) → **Finish**. Poi avvia l’emulatore con il pulsante Play.
  - **Dispositivo reale**: Abilita **Opzioni sviluppatore** e **Debug USB**, collega il telefono, accetta il debugging sul telefono quando richiesto.

---

## 4. Eseguire l’app

1. In alto nella toolbar scegli il **run configuration** "app" e il **dispositivo/emulatore** dove vuoi installare l’app.
2. Clicca il pulsante **Run** (triangolo verde) oppure **Shift+F10**.
3. L’app viene compilata, installata e avviata. All’avvio dovresti vedere la schermata **Login** (se non c’è un token salvato).

---

## 5. Build Release (opzionale)

- Menu **Build → Select Build Variant**.
- Per la variante **release**: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
- L’APK release si trova in:  
  **`app/build/outputs/apk/release/app-release.apk`**.

---

## 6. Problemi comuni

| Problema | Cosa fare |
|----------|-----------|
| **Gradle sync fallisce** | Controlla di avere **JDK 17** (File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK: 17). |
| **"SDK location not found"** | Crea il file **`local.properties`** nella root del progetto con la riga: `sdk.dir=C\:\\Users\\a.scozzari\\AppData\\Local\\Android\\Sdk` (adatta il percorso al tuo SDK). |
| **Modulo `pjsua2` non trovato** | Il progetto include `implementation(project(":pjsua2"))`. Se non hai la cartella/libreria `pjsua2` nella root, il sync può fallire: verifica che esista la cartella **`pjsua2`** accanto a **`app`** e che contenga un `build.gradle`/`build.gradle.kts`. |
| **BuildConfig non trovato** | Dopo il primo sync, **Build → Rebuild Project**. BuildConfig viene generato automaticamente. |
| **Emulatore lento** | In Device Manager prova un’immagine con **API 34** invece di 35, o abilita l’accelerazione hardware (HAXM / Hyper-V) se non già attiva. |

---

## 7. Dove trovare il codice principale

- **Activity e stato auth**: `app/src/main/java/it/edgvoip/jarvis/MainActivity.kt`, `MainViewModel.kt`
- **Login**: `app/.../ui/screens/LoginScreen.kt`, `LoginViewModel.kt`
- **API e token**: `app/.../data/api/ApiClient.kt`, `TokenManager.kt`
- **Navigazione**: `app/.../ui/navigation/JarvisNavHost.kt`, `JarvisNavigation.kt`
- **Manifest**: `app/src/main/AndroidManifest.xml`

Se un passaggio non funziona, indica il messaggio di errore che vedi (o uno screenshot) e ti guido passo passo.
