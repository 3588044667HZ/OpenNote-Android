plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.open.note"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.open.note"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.google.code.gson:gson:2.10.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ─── TipTap bundle 自动构建钩子 ───
// 在编译前运行 esbuild 将 TipTap 打包为单文件 UMD (assets/tiptap-bundle.js)
val bundleDir = rootProject.file("tiptap-bundle")
val bundleOutput = file("src/main/assets/tiptap-bundle.js")

val buildTiptapBundle by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds TipTap UMD bundle into assets using esbuild"

    workingDir = bundleDir

    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    commandLine(
        if (isWindows) "cmd" else "sh",
        if (isWindows) "/c" else "-c",
        "npm install --no-audit --no-fund && npm run build"
    )

    // 仅当 bundle 源码变更时重新构建，避免每次编译都跑 npm
    inputs.files(fileTree(bundleDir) { include("*.js", "package.json", "package-lock.json") })
    outputs.file(bundleOutput)
}

// 绑定到 preBuild，确保编译前 bundle 已就绪
tasks.matching { it.name == "preBuild" || it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
    .configureEach { dependsOn(buildTiptapBundle) }
