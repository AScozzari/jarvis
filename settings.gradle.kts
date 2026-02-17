pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Jarvis"
include(":app")
// Riattiva quando usi il modulo pjsua2 (con pjsua2.aar nella cartella pjsua2/)
// include(":pjsua2")
