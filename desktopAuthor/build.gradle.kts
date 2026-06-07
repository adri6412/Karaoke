import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.swing)

    // Decodifica MP3 per javax.sound (SPI): permette ad AudioSystem di leggere gli .mp3.
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
}

kotlin {
    // Niente toolchain fissa: compila con la JDK in uso (17 su Windows, 21 in WSL)
    // mirando comunque al bytecode 17. Permette il build del .deb in WSL senza JDK 17.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

compose.desktop {
    application {
        mainClass = "karaoke.author.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "KaraokeAuthor"
            packageVersion = "1.1.0"
        }
    }
}
