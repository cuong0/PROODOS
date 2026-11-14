plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.proodos.faamwd"
    minSdk = 24
    targetSdk = 36
    versionCode = 22
    versionName = "22.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val customKeystorePath = System.getenv("KEYSTORE_PATH")
      val customKeystoreFile = if (customKeystorePath != null) file(customKeystorePath) else file("${rootDir}/my-upload-key.jks")
      if (customKeystoreFile.exists()) {
        storeFile = customKeystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
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
  // "ksp"(libs.moshi.kotlin.codegen)
}

abstract class KspSerialService : org.gradle.api.services.BuildService<org.gradle.api.services.BuildServiceParameters.None>

val kspSerialService = gradle.sharedServices.registerIfAbsent("kspSerialService", KspSerialService::class.java) {
    maxParallelUsages.set(1)
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    usesService(kspSerialService)
    if (name.contains("Test")) {
        enabled = false
    }
}

abstract class CleanLauncherCacheTask : DefaultTask() {
    @get:Internal
    abstract val intermediatesDir: DirectoryProperty

    @get:Internal
    abstract val foldersToClean: ListProperty<String>

    @TaskAction
    fun clean() {
        val dir = intermediatesDir.orNull?.asFile
        if (dir != null && dir.exists()) {
            foldersToClean.get().forEach { path ->
                val folder = File(dir, path)
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
            }
        }
    }
}

val cleanLauncherIconCache = tasks.register<CleanLauncherCacheTask>("cleanLauncherIconCache") {
    intermediatesDir.set(layout.buildDirectory.dir("intermediates"))
    foldersToClean.set(listOf(
        "packaged_res",
        "merged_res",
        "merged_res_blame_folder",
        "incremental/packageDebug",
        "incremental/mergeDebugResources"
    ))
    outputs.upToDateWhen { false }
}

abstract class EnsureStableIdsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        val dir = outputDir.get().asFile
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "stableIds.txt")
        if (!f.exists()) f.createNewFile()
    }
}

val ensureStableIds = tasks.register<EnsureStableIdsTask>("ensureStableIdsFile") {
    outputDir.set(layout.buildDirectory.dir("intermediates/stable_resource_ids_file/release/processReleaseResources"))
    mustRunAfter(tasks.matching { it.name.startsWith("process") && it.name.endsWith("Resources") })
}

tasks.matching { it.name.startsWith("bundle") && it.name.endsWith("Resources") }.configureEach {
    dependsOn(ensureStableIds)
    mustRunAfter(ensureStableIds)
}
