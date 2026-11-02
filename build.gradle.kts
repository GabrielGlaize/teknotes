plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Documents is synced by iCloud, which leaves "x 2" copies in build folders and breaks the
// next build. iCloud ignores anything named *.nosync.
layout.buildDirectory = layout.projectDirectory.dir("build.nosync")
