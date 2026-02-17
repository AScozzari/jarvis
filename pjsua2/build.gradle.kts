plugins {
    id("com.android.library")
}

android {
    namespace = "it.edgvoip.pjsua2"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(files("pjsua2.aar"))
}
