# Risolvere "Unable to delete directory" durante la build

## Soluzione rapida (consigliata)

1. **Chiudi Android Studio** (e qualsiasi IDE con il progetto aperto).
2. Doppio clic su **`build-debug.bat`** nella cartella del progetto.  
   Lo script ferma i daemon Gradle e compila con `--no-daemon`, così nessun processo tiene i file aperti dopo la build.

Oppure da PowerShell:

```powershell
cd C:\Users\a.scozzari\Desktop\android-app
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --stop
# Attendi 2-3 secondi
.\gradlew.bat assembleDebug --no-daemon
```

---

## 1. Ferma i daemon Gradle (manuale)

In PowerShell, dalla cartella del progetto:

```powershell
cd C:\Users\a.scozzari\Desktop\android-app
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --stop
```

Attendi il messaggio "Stopping Daemon(s)" e che i processi terminino.

---

## 2. Chiudi programmi che usano il progetto

- **Chiudi Android Studio** (o Cursor / altro IDE) se ha aperto la cartella `android-app`.
- In questo modo nessun processo terrà aperti i file in `app\build\...`.

---

## 3. Pulisci e ricompila

Sempre in PowerShell:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

Se `clean` fallisce con lo stesso errore "Unable to delete", elimina a mano la cartella di build:

1. Chiudi **tutto** (IDE, terminali nella cartella del progetto).
2. In Esplora file vai in `C:\Users\a.scozzari\Desktop\android-app`.
3. Elimina la cartella **`app\build`** (tasto Canc o click destro → Elimina).
4. Riapri PowerShell, imposta di nuovo `JAVA_HOME` e lancia:

```powershell
cd C:\Users\a.scozzari\Desktop\android-app
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

---

## 4. Se il blocco persiste

- **Antivirus**: aggiungi un’eccezione per la cartella `android-app` (in particolare `app\build`) oppure disattivalo temporaneamente durante la build.
- **OneDrive / cloud**: se la cartella Desktop è sincronizzata, prova a spostare il progetto in una cartella non sincronizzata (es. `C:\Progetti\android-app`) e compilare da lì.
