import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.library); alias(libs.plugins.compose.multiplatform); alias(libs.plugins.compose.compiler) }
kotlin {
    androidTarget { @OptIn(ExperimentalKotlinGradlePluginApi::class) compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosArm64(); iosSimulatorArm64()
    sourceSets { commonMain.dependencies { implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3) } }
}
android { namespace = "com.evandro.roboolhos.core.designsystem"; compileSdk = 36; defaultConfig { minSdk = 26 } }
