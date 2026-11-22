import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.application); alias(libs.plugins.compose.multiplatform); alias(libs.plugins.compose.compiler) }
kotlin {
    androidTarget { @OptIn(ExperimentalKotlinGradlePluginApi::class) compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3)
            implementation(libs.koin.compose); implementation(libs.koin.compose.viewmodel)
            implementation(project(":core:robot")); implementation(project(":core:designsystem")); implementation(project(":features:companion"))
        }
        androidMain.dependencies { implementation(libs.activity.compose); implementation(libs.koin.android) }
    }
}
android {
    namespace = "com.evandro.roboolhos"; compileSdk = 36
    defaultConfig { applicationId = "com.evandro.roboolhos"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
