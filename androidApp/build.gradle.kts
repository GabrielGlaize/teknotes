plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Documents is synced by iCloud, which leaves "x 2" copies in build folders and breaks the
// next build. iCloud ignores anything named *.nosync.
layout.buildDirectory = layout.projectDirectory.dir("build.nosync")

dependencies {
    implementation(projects.shared)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

android {
    namespace = "dev.gabrielglaize.teknotes.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.gabrielglaize.teknotes"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        // Local Supabase (supabase start) as seen from the Android emulator. The key is the
        // public demo key every local Supabase uses. Override with -Pteknotes.supabaseUrl=...
        // e.g. the Mac's address on the Wi-Fi to test on a real phone.
        getByName("debug") {
            buildConfigField("String", "SUPABASE_URL", "\"${providers.gradleProperty("teknotes.supabaseUrl").getOrElse("http://10.0.2.2:55321")}\"")
            buildConfigField("String", "SUPABASE_KEY", "\"${providers.gradleProperty("teknotes.supabaseKey").getOrElse("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0")}\"")
        }
        // No production project yet: the release build works offline until one exists.
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "SUPABASE_URL", "\"${providers.gradleProperty("teknotes.supabaseUrl").getOrElse("")}\"")
            buildConfigField("String", "SUPABASE_KEY", "\"${providers.gradleProperty("teknotes.supabaseKey").getOrElse("")}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}
