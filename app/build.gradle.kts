import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val coroutinesVersion = "1.8.1"
val patchedCoroutinesJar = file("$buildDir/patched/kotlinx-coroutines-core-jvm-$coroutinesVersion.jar")

fun replaceAscii(src: ByteArray, from: ByteArray, to: ByteArray): ByteArray {
    require(from.size == to.size) { "replacement length ${from.size} vs ${to.size}" }
    val out = ByteArray(src.size)
    var i = 0
    var o = 0
    while (i < src.size) {
        var match = i + from.size <= src.size
        if (match) {
            var k = 0
            while (k < from.size) {
                if (src[i + k] != from[k]) {
                    match = false
                    break
                }
                k++
            }
        }
        if (match) {
            System.arraycopy(to, 0, out, o, to.size)
            i += from.size
            o += to.size
        } else {
            out[o++] = src[i++]
        }
    }
    return out
}

fun patchClassValueRefs(src: File, dst: File) {
    dst.parentFile.mkdirs()
    val from = "java/lang/ClassValue".toByteArray(Charsets.US_ASCII)
    val to = "shim/lang/ClassValue".toByteArray(Charsets.US_ASCII)
    ZipFile(src).use { zip ->
        ZipOutputStream(dst.outputStream().buffered()).use { out ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val raw = zip.getInputStream(entry).readBytes()
                val data = if (entry.name.endsWith(".class")) replaceAscii(raw, from, to) else raw
                val copy = ZipEntry(entry.name)
                out.putNextEntry(copy)
                out.write(data)
                out.closeEntry()
            }
        }
    }
}

val coroutinesCoreIncoming by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

configurations.configureEach {
    if (name != "coroutinesCoreIncoming") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }
}

val patchCoroutinesJar by tasks.registering {
    outputs.file(patchedCoroutinesJar)
    doLast {
        patchClassValueRefs(coroutinesCoreIncoming.single(), patchedCoroutinesJar)
    }
}

android {
    namespace = "io.github.coolcoderme.haphone"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.coolcoderme.haphone"
        minSdk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("sideload") {
            val ks = file("../keystore/haphone.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "haphone"
                keyAlias = "haphone"
                keyPassword = "haphone"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val sideload = signingConfigs.findByName("sideload")
            if (sideload?.storeFile?.exists() == true) {
                signingConfig = sideload
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    add("coroutinesCoreIncoming", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation(files(patchedCoroutinesJar).builtBy(patchCoroutinesJar))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation(files(patchedCoroutinesJar).builtBy(patchCoroutinesJar))
    testImplementation("org.json:json:20240303")
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
}
