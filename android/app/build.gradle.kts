plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val websiteRoot = rootProject.projectDir.parentFile
val syncWebsiteAssets by tasks.registering(Copy::class) {
    from(websiteRoot) {
        include("*.html")
        include("css/**", "js/**", "images/**", "fonts/**")
    }
    into(layout.projectDirectory.dir("src/main/assets/site"))
}
tasks.named("preBuild").configure { dependsOn(syncWebsiteAssets) }

android {
    namespace = "ir.halgheyar.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "ir.halgheyar.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("com.google.android.material:material:1.13.0")
}
