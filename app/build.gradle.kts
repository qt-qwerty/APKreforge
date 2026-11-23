plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.localbuild"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.localbuild"
        minSdk = 26
        // targetSdk 28 volontaire : depuis Android 10 (API 29+), le système interdit
        // d'exécuter des binaires situés dans le stockage privé de l'app (règle W^X).
        // Le JDK / Gradle embarqués sont donc exécutables seulement avec targetSdk <= 28.
        // Conséquence : app destinée au sideload, pas au Play Store.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "plugin.properties", "about.html"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Git en Java pur (5.13 = dernière branche compatible Android sans API Java 11+)
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    implementation("org.slf4j:slf4j-nop:1.7.36")

    // Extraction des archives de toolchain (.tar.gz / .tar.xz / .zip)
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
}
