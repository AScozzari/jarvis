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
        maven {
            name = "linphone"
            url = uri("https://download.linphone.org/maven_repository")
            content {
                includeGroup("org.linphone")
                includeGroup("org.linphone.no-video")
            }
        }
    }
}

rootProject.name = "Jarvis"
include(":app")
// Riattiva quando usi il modulo pjsua2 (con pjsua2.aar nella cartella pjsua2/)
// include(":pjsua2")
