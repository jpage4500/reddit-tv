import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

// return value for the first available key in: env variable, then ~/.gradle/gradle.properties.
// multiple keys can be passed — the first one that resolves wins
fun getValue(vararg keys: String): String {
    for (key in keys) {
        val value = System.getenv(key) ?: readGradleProperty(key)
        if (!value.isNullOrBlank()) return value
    }
    return ""
}

fun readGradleProperty(key: String): String? {
    val homeDir = System.getProperty("user.home") ?: return null
    val gradleProps = File(homeDir, ".gradle/gradle.properties")
    if (!gradleProps.exists()) return null

    return runCatching {
        Properties().apply {
            gradleProps.inputStream().use { load(it) }
        }.getProperty(key)?.trim()
    }.getOrNull()
}

abstract class GitCommitCountValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        return try {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("sh", "-c", "git rev-list --count HEAD")
                standardOutput = output
            }
            output.toString().trim()
        } catch (e: Exception) {
            "0"
        }
    }
}

// Safely fetch the cached git count
val commitCount = providers.of(GitCommitCountValueSource::class) {}.get()
val appVersion = "1.0.$commitCount"

// true only for a build that actually ships: the jDeploy fat jar, a full Android release, or
// an Xcode Release framework. analytics sends `is_dev = !IS_RELEASE`, so without this every run from
// Gradle, every debug install and every local 'beta' APK would count as a user
// (see manager/AnalyticsManager and docs/Analytics.md)
val isReleaseBuild = run {
    val taskNames = gradle.startParameter.taskNames.joinToString(" ")
    // desktop: the one jar jDeploy publishes
    val isJDeploy = taskNames.contains("buildExecutableJar", ignoreCase = true)
    // android: any task naming the google or full flavor's release variant — assemble, bundle, or a
    // publish task that rebuilds the bundle it uploads. the 'beta' flavor is local dev builds, so it
    // stays dev even though it's built as a release build type
    val isAndroidShip = Regex("FullRelease").containsMatchIn(taskNames)
    // ios: the Xcode "Build Compose Framework" phase exports CONFIGURATION to the gradle it runs
    val isXcodeRelease = System.getenv("CONFIGURATION") == "Release"
    isJDeploy || isAndroidShip || isXcodeRelease
}

// brand identity for the desktop distribution. keep the app name in sync with Branding.appName
// (commonMain), which supplies the in-app title
val brandAppName = "RedditTV"
val brandBundleId = "com.jpage4500.reddittv"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.buildconfig)
}

buildConfig {
    packageName("com.jpage4500.reddittv")

    buildConfigField("APP_VERSION", appVersion)
    buildConfigField("BUILD_DATE", SimpleDateFormat("yyyy.MM.dd hh:mm a").format(Date()))
    buildConfigField("IS_RELEASE", isReleaseBuild)

    // supabase log upload + analytics. one project serves every app, so these keys are shared and
    // carry no app prefix — see /Users/jpage/working/projects/supabase
    buildConfigField("LOGGING_URL", getValue("LOGGING_URL"))
    buildConfigField("LOGGING_API", getValue("LOGGING_API"))

    useKotlinOutput {
        topLevelConstants = true
        internalVisibility = false
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    val xcf = XCFramework("ComposeApp")
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += "-Xbinary=bundleId=$brandBundleId"
            xcf.add(this)
        }
        iosTarget.compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    @Suppress("unused")
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.runtime)
                implementation(libs.foundation)
                implementation(libs.material3)
                implementation(libs.ui)
                implementation(libs.components.resources)
                implementation(libs.ui.tooling.preview)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                // the TV's sign-in page: a phone on the same Wi-Fi opens it from the QR code (PhoneSignInServer)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kermit)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)
                implementation(libs.coil.svg)
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.runtime.compose)
                // the sign-in screen's QR code
                implementation(libs.qrose)
            }
        }

        val androidMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.coil.network.okhttp)
                // video posts (VideoPreview)
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.exoplayer.hls)
                implementation(libs.media3.exoplayer.dash)
                implementation(libs.media3.ui)
                implementation(libs.media3.datasource.okhttp)
                implementation(libs.androidx.lifecycle.process)
                // SLF4J logging implementation
                implementation(libs.slf4j.simple)
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        val iosArm64Main by getting {
            dependsOn(iosMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.runtime)
                // cross-platform compose dependencies for jDeploy
                implementation(libs.desktop.jvm.linux.x64)
                implementation(libs.desktop.jvm.linux.arm64)
                implementation(libs.desktop.jvm.macos.x64)
                implementation(libs.desktop.jvm.macos.arm64)
                implementation(libs.desktop.jvm.windows.x64)

                implementation(libs.ktor.client.okhttp)
                implementation(libs.coil.network.okhttp)
                implementation(libs.slf4j.simple)
                // Add this for Dispatchers.Main support on desktop
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.composenativetray)
                // FFmpeg JNI bindings for the video preview (VideoPreview.desktop). every platform's
                // natives go in the fat jar; each jDeploy bundle drops the others' (.jdpignore.*)
                implementation(libs.javacv)
                val ffmpegVersion = libs.versions.ffmpeg.get()
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:windows-x86_64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-x86_64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-arm64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:linux-x86_64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:linux-arm64")
            }
        }
    }
}

// exclude lifecycle-runtime-compose for iOS compatibility
// NOTE: without this xcode build fails
configurations.all {
    if (name.contains("ios")) {
        exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-compose")
    }
}

android {
    namespace = "com.jpage4500.reddittv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time doesn't exist below API 26 but kotlinx-datetime's jvm/android implementation is
        // built on it — without this, anything that touches TimeZone/Instant dies with
        // NoClassDefFoundError: java.time.ZoneOffset. minSdk is 24, so API 24/25 devices (Fire OS 6
        // tablets, for one) crash in Application.onCreate on the very first log line.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}


compose.desktop {
    application {
        mainClass = "com.jpage4500.reddittv.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(true)
            optimize.set(true)
            obfuscate.set(false) // Obfuscation often breaks Compose desktop, keep it false
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = brandAppName
            packageVersion = appVersion

            // icon files are optional - only set if they exist (scripts/generate-icons.sh writes them)
            val macOSIcon = project.file("src/desktopMain/resources/icon.icns")
            val windowsIcon = project.file("src/desktopMain/resources/icon.ico")
            val linuxIcon = project.file("src/desktopMain/resources/icon.png")

            macOS {
                bundleID = brandBundleId
                if (macOSIcon.exists()) {
                    iconFile.set(macOSIcon)
                }
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>$brandAppName serves its sign-in page on your local network.</string>
                    """.trimIndent()
                }
            }

            if (windowsIcon.exists()) {
                windows {
                    iconFile.set(windowsIcon)
                }
            }

            if (linuxIcon.exists()) {
                linux {
                    iconFile.set(linuxIcon)
                }
            }
        }
    }
}

// task to print version for CI/CD
tasks.register("printVersion") {
    // Save to a local variable inside the task definition to prevent
    // the doLast block from capturing the entire Gradle script object
    val versionToPrint = appVersion

    doLast {
        println(versionToPrint)
    }
}


// create fat JAR with all dependencies for jDeploy
val desktopJarTask = tasks.named<Jar>("desktopJar")

tasks.register<Jar>("buildExecutableJar") {
    group = "build"
    description = "Build cross-platform executable JAR for jDeploy"
    dependsOn(desktopJarTask)

    archiveBaseName.set("rtv")
    archiveClassifier.set("all")

    // set main class in manifest
    manifest {
        attributes["Main-Class"] = "com.jpage4500.reddittv.MainKt"
    }

    // include all runtime dependencies
    val runtimeClasspath = configurations.getByName("desktopRuntimeClasspath")
    from({
             runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) }
         })

    // include compiled classes from desktopJar
    from(zipTree(desktopJarTask.get().archiveFile))

    // handle duplicate files
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    doLast {
        val jarFile = archiveFile.get().asFile
        if (jarFile.exists()) {
            println("✅ Built executable JAR: ${jarFile.absolutePath}")
            println("   Size: ${jarFile.length() / 1024 / 1024} MB")
        } else {
            println("❌ JAR file not found at expected location")
        }
    }
}
