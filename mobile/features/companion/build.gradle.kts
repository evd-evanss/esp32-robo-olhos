import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.library); alias(libs.plugins.compose.multiplatform); alias(libs.plugins.compose.compiler) }
kotlin {
    androidTarget { @OptIn(ExperimentalKotlinGradlePluginApi::class) compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3)
            implementation(compose.materialIconsExtended); implementation(libs.lifecycle.viewmodel); implementation(libs.lifecycle.runtime); implementation(libs.coroutines.core)
            implementation(libs.koin.core); implementation(libs.koin.compose); implementation(libs.koin.compose.viewmodel)
            implementation(project(":core:robot")); implementation(project(":core:designsystem"))
        }
        commonTest.dependencies { implementation(libs.kotlin.test); implementation(libs.coroutines.test) }
    }
}
android { namespace = "com.evandro.roboolhos.features.companion"; compileSdk = 36; defaultConfig { minSdk = 26 } }
