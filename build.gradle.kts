import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileOutputStream

plugins {
  id("com.android.application") version "8.13.0"
  id("org.jetbrains.kotlin.android") version "1.9.22"
}

dependencies {
  implementation("androidx.window:window-java:1.3.0")
  implementation("androidx.core:core:1.16.0")
  implementation("androidx.core:core-ktx:1.16.0")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.cardview:cardview:1.0.0")
  implementation("com.google.android.material:material:1.12.0")


  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


  val roomVersion = "2.6.1"
  implementation("androidx.room:room-runtime:$roomVersion")
  implementation("androidx.room:room-ktx:$roomVersion")
  annotationProcessor("androidx.room:room-compiler:$roomVersion")






  implementation("com.google.code.gson:gson:2.10.1")


  implementation("androidx.biometric:biometric:1.1.0")


  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")


  implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

  implementation("com.google.android.gms:play-services-auth:20.7.0")
  implementation("com.google.api-client:google-api-client-android:2.2.0")
  implementation("com.google.http-client:google-http-client-android:1.43.3")
  implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")
  implementation("androidx.work:work-runtime:2.9.0")


  testImplementation("junit:junit:4.13.2")
}

configurations.all {
    exclude(group = "org.apache.httpcomponents")
    exclude(group = "commons-logging")
}







android {
  namespace = "juloo.keyboard2"
  compileSdkVersion = "android-35"

  defaultConfig {
    applicationId = "juloo.keyboard2"
    minSdk = 21
    targetSdk { version = release(35) }
    versionCode = 50
    versionName = "1.32.1"


    javaCompileOptions {
      annotationProcessorOptions {
        arguments["room.schemaLocation"] = "$projectDir/schemas"
      }
    }
  }

  packaging {
    resources {
      excludes += "META-INF/DEPENDENCIES"
    }
  }

  sourceSets {
    named("main") {
      manifest.srcFile("AndroidManifest.xml")
      java.srcDirs("srcs/juloo.keyboard2")



      res.srcDirs("res", "build/generated-resources")
      assets.srcDirs(projectDir.resolve("build/generated-assets/main"))
      assets.srcDirs(projectDir.resolve("build/generated-assets/readme"))
    }

    named("test") {
      java.srcDirs("test")
    }
  }

  signingConfigs {




    named("debug") {
      storeFile = file(System.getenv("DEBUG_KEYSTORE") ?: "debug.keystore")
      storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "debug0"
      keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "debug"
      keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "debug0"
    }

    create("release") {
      val ks = System.getenv("RELEASE_KEYSTORE")
      if (ks != null) {
        storeFile = file(ks)
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      resValue("string", "app_name", "@string/app_name_release")
      signingConfig = signingConfigs["release"]
    }

    named("debug") {
      isMinifyEnabled = false
      isShrinkResources = false
      isDebuggable = true
      applicationIdSuffix = ".debug"
      resValue("string", "app_name", "@string/app_name_debug")
      resValue("bool", "debug_logs", "true")
      signingConfig = signingConfigs["debug"]
    }
  }


  android.applicationVariants.forEach { variant ->
    variant.outputs.forEach {
      it as BaseVariantOutputImpl
      it.outputFileName = "${variant.applicationId}.apk"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }
}

val buildKeyboardFont by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/special_font")
  val out = layout.projectDirectory.file("assets/special_font.ttf")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nBuilding assets/special_font.ttf") }
  workingDir = `in`
  val svgFiles = `in`.listFiles()!!.filter {
    it.isFile && it.name.endsWith(".svg")
  }.toTypedArray()
  commandLine("fontforge", "-lang=ff", "-script", "build.pe", out.asFile.absolutePath, *svgFiles)
}

val genEmojis by tasks.registering(Exec::class) {
  doFirst { println("\nGenerating res/raw/emojis.txt") }
  workingDir = projectDir
  commandLine("python", "gen_emoji.py")
}

val genLayoutsList by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  outputs.file(projectDir.resolve("res/values/layouts.xml"))
  doFirst { println("\nGenerating res/values/layouts.xml") }
  workingDir = projectDir
  commandLine("python", "gen_layouts.py")
}

val checkKeyboardLayouts by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  inputs.file(projectDir.resolve("srcs/juloo.keyboard2/KeyValue.java"))
  outputs.file(projectDir.resolve("check_layout.output"))
  doFirst { println("\nChecking layouts") }
  workingDir = projectDir
  commandLine("python", "check_layout.py")
}

val compileComposeSequences by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/compose")
  val out = projectDir.resolve("srcs/juloo.keyboard2/ComposeKeyData.java")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nGenerating $out") }
  val sequences = `in`.listFiles { it: File ->
    !it.name.endsWith(".py") && !it.name.endsWith(".md")
  }!!.map { it.absolutePath }.toTypedArray()
  workingDir = projectDir
  commandLine("python", `in`.resolve("compile.py").absolutePath, *sequences)
  doFirst { standardOutput = FileOutputStream(out) }
}

tasks.withType(Test::class).configureEach {
  dependsOn(genEmojis, genLayoutsList, checkKeyboardLayouts, compileComposeSequences)
}

val initDebugKeystore by tasks.registering(Exec::class) {
  doFirst { println("Initializing default debug keystore") }
  isEnabled = !file("debug.keystore").exists()

  commandLine("keytool", "-genkeypair", "-dname", "cn=d, ou=e, o=b, c=ug", "-alias", "debug", "-keypass", "debug0", "-keystore", "debug.keystore", "-keyalg", "rsa", "-storepass", "debug0", "-validity", "10000")
}


val copyRawQwertyUS by tasks.registering(Copy::class) {
  from("srcs/layouts/latn_qwerty_us.xml")
  into("build/generated-resources/raw")
}

val copyLayoutDefinitions by  tasks.registering(Copy::class) {
  from("srcs/layouts")
  include("*.xml")
  into("build/generated-resources/xml")
}

val copyReadme by tasks.registering(Copy::class) {
  from("README.md")
  into(projectDir.resolve("build/generated-assets/readme"))
}

val copyAssets by tasks.registering(Copy::class) {
  from("assets")
  into(projectDir.resolve("build/generated-assets/main"))
}

tasks.named("preBuild") {
  dependsOn(initDebugKeystore, copyRawQwertyUS, copyLayoutDefinitions, compileComposeSequences, genLayoutsList, genEmojis, copyReadme, copyAssets)
}
