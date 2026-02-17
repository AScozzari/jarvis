# Fix crash "ClassNotFoundException: JarvisApplication" / "Hilt_JarvisApplication"

Il crash può dipendere da:
1. **Overlay** (deploy incrementale) che non contiene tutte le classi.
2. **Multidex**: `JarvisApplication` e `Hilt_JarvisApplication` devono stare nel **main dex**; se finiscono in un dex secondario, non sono ancora caricate quando Android crea l’Application.

Nel progetto sono stati aggiunti **app/multidex-keep.pro** e **multiDexKeepProguard** in `build.gradle.kts` per tenere queste classi nel main dex.

## Soluzione: pulizia completa e reinstallazione

### 1. Disinstalla l’app dal telefono

- **Impostazioni → App → Jarvis → Disinstalla**  
  oppure (telefono via USB):
  ```powershell
  adb uninstall it.edgvoip.jarvis
  ```

### 2. Pulizia forte e rebuild

In PowerShell, dalla cartella del progetto:

```powershell
cd C:\Users\a.scozzari\Desktop\android-app
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat clean
.\gradlew.bat assembleDebug --no-daemon --no-build-cache
```

Se hai Android Studio aperto: **File → Invalidate Caches → Invalidate and Restart**, poi dopo il riavvio ripeti clean e `assembleDebug` da terminale.

### 3. Reinstalla sul telefono

- **Da Android Studio:** seleziona il telefono e **Run** (triangolo verde). Non usare "Apply Changes".
- **Da terminale:**
  ```powershell
  .\gradlew.bat installDebug --no-daemon
  ```

### 4. Avvia l’app

Apri Jarvis dal launcher del telefono.

---

Se il crash continua, in Android Studio: **Build → Clean Project**, poi **Build → Rebuild Project**, e controlla che in **Build → Make Project** non ci siano errori (in particolare che KSP generi le classi Hilt in `app/build/generated/ksp/...`).
