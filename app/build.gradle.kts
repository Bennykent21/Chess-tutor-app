// import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.chesstutor.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.github.bennykent21.chesstutor"
    minSdk = 24
    targetSdk = 36
    // CI builds carry the GitHub Actions run number, so Android's App info screen shows
    // exactly which build is installed. Local builds are "0.1-local".
    val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    versionCode = ciRunNumber ?: 1
    versionName = if (ciRunNumber != null) "0.1.$ciRunNumber" else "0.1-local"
    // App name shown under the launcher icon. Debug builds override it below.
    manifestPlaceholders["appLabel"] = "@string/app_name"

    // "Support the developer" in Settings. Shown only when a link is set (gradle.properties
    // ctSupportUrl=..., or -PctSupportUrl=...). Leave it empty for Google Play uploads: Play
    // has flagged in-app donation links (plan section 9).
    val supportUrl = (project.findProperty("ctSupportUrl") as String?).orEmpty().replace("\"", "")
    buildConfigField("String", "SUPPORT_URL", "\"$supportUrl\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ndk {
      // CI passes -PctAbis=arm64-v8a so test APKs carry a single copy of Stockfish, whose
      // embedded network is large. Local builds include the emulator ABI too.
      val requestedAbis = (project.findProperty("ctAbis") as String?)
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
      abiFilters += requestedAbis ?: listOf("arm64-v8a", "x86_64")
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (!keystorePath.isNullOrBlank()) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    create("debugConfig") {
      val rootKeystore = file("${rootDir}/debug.keystore")
      val homeKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
      storeFile = when {
        rootKeystore.exists() -> rootKeystore
        homeKeystore.exists() -> homeKeystore
        else -> rootKeystore
      }
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseSigning = signingConfigs.getByName("release")
      if (releaseSigning.storeFile != null) {
        signingConfig = releaseSigning
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      // Debug installs as a separate app ("Chess Tutor Debug") with its own data, so the
      // debug APK and the minified release APK can sit side by side on the phone.
      applicationIdSuffix = ".debug"
      manifestPlaceholders["appLabel"] = "Chess Tutor Debug"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    // The app uses java.time (Instant etc.), which needs API 26+. Desugaring lets it run on
    // our minSdk 24 (decision D10: keep the current minimum).
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  androidResources {
    noCompress += listOf("gz")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Robolectric needs Java 21 to run Android SDK 36. Only the unit tests run on a Java 21
// toolchain (found locally, or downloaded by the foojay resolver in settings.gradle.kts),
// so `./gradlew test` passes whichever JDK runs Gradle itself.
tasks.withType<Test>().configureEach {
  javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

// The Stockfish binaries are built by CI (or tools/build_stockfish_android.sh) and are not
// in git. Without them the app silently runs on the weak Lite engine, so every build says so.
val checkStockfishBinaries by tasks.registering {
  val abis = android.defaultConfig.ndk.abiFilters.toList()
  val jniLibs = layout.projectDirectory.dir("src/main/jniLibs").asFile
  outputs.upToDateWhen { false }
  doLast {
    val missing = abis.filter { !File(jniLibs, "$it/libstockfish.so").isFile }
    if (missing.isNotEmpty()) {
      logger.warn(
        "WARNING: Stockfish is missing for ${missing.joinToString()} " +
          "(src/main/jniLibs/<abi>/libstockfish.so). This build will use the Lite engine. " +
          "Build it with tools/build_stockfish_android.sh or download the stockfish-android CI artifact."
      )
    }
  }
}
tasks.named("preBuild") { dependsOn(checkStockfishBinaries) }



// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
