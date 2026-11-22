import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    androidTarget { @OptIn(ExperimentalKotlinGradlePluginApi::class) compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation(libs.coroutines.core); implementation(libs.serialization.json) }
        androidMain.dependencies { implementation(libs.coroutines.android) }
    }
}
android { namespace = "com.evandro.roboolhos.core.robot"; compileSdk = 36; defaultConfig { minSdk = 26 } }
