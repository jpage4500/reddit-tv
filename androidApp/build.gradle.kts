import java.io.ByteArrayOutputStream
import java.util.*

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
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

// at least 1: a fresh clone with no commits yet counts 0, which Android rejects as a versionCode
val commitCount = (providers.of(GitCommitCountValueSource::class) {}.get().toIntOrNull() ?: 0).coerceAtLeast(1)
val appVersionName = "1.0.$commitCount"

// launcher label — overrides @string/app_name. keep in sync with Branding.appName (commonMain),
// which supplies the in-app title on every platform.
val brandAppName = "RedditTV"

// return the value for a key from an env variable, then ~/.gradle/gradle.properties. the lookup
// names keep the KMP_ prefix (that keystore is shared across my KMP apps); the code below drops it
fun getValue(key: String): String =
    (System.getenv(key) ?: project.findProperty(key) as String?)?.trim().orEmpty()

val KEYSTORE = getValue("KMP_KEYSTORE")
val SIGNING_STORE_PASSWORD = getValue("KMP_SIGNING_STORE_PASSWORD")
val SIGNING_KEY_ALIAS = getValue("KMP_SIGNING_KEY_ALIAS")
val SIGNING_KEY_PASSWORD = getValue("KMP_SIGNING_KEY_PASSWORD")

// the keystore arrives base64-encoded. decode to a temp file under build/ so Android Studio +
// direct ./gradlew invocations work without needing a wrapper script. release signingConfig is only
// created when the keystore is actually present (debug builds and CI branches without it still work)
val keystoreFile: File? = KEYSTORE.takeIf { it.isNotBlank() }?.let { encoded ->
    val out = File(layout.buildDirectory.get().asFile, "tmp/rtv-release.jks").apply {
        parentFile.mkdirs()
    }
    out.writeBytes(Base64.getDecoder().decode(encoded))
    out
}

android {
    namespace = "com.jpage4500.reddittv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.jpage4500.reddittv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = commitCount
        versionName = appVersionName
        // launcher label — the composeApp library ships no strings.xml, so this is the only source
        resValue("string", "app_name", brandAppName)
    }
    // full = sideload release kept current from GitHub Releases by the in-app updater; beta = local
    // dev builds (scripts/build-android.sh), a distinct package so it never replaces the 'full'
    // install. there's no Play Store build
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            applicationIdSuffix = ".full"
        }
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            // its own label (and green launcher background) so it's told apart from 'full' when
            // both are installed
            resValue("string", "app_name", "$brandAppName Beta")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = SIGNING_STORE_PASSWORD
                keyAlias      = SIGNING_KEY_ALIAS
                keyPassword   = SIGNING_KEY_PASSWORD
            }
        }
    }
    buildTypes {
        // R8 shrinking, applied to all three distribution flavors. proguard-rules.pro next to this
        // file keeps our *names* (-keepnames), so unused code is still stripped but the stack traces
        // LogManager uploads stay readable without mapping.txt.
        // NOTE: composeApp/proguard-rules.pro is the *desktop* ProGuard config and is not used here.
        // see docs/Android R8 Shrinking.md
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro"),
            )
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // must match composeApp — the desugared java.time backport is dexed into the app, so the
        // app module is where it actually ships. see the note there.
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    jvmToolchain(17)
}


dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":composeApp"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kermit)
    debugImplementation(compose.uiTooling)
}
