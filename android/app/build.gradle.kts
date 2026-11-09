import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mekronize.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mekronize.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // Sunucu adresi: gradle.properties veya -Pmekronize.baseUrl=... ile verilir; uygulamada da değiştirilebilir.
        val baseUrl = (project.findProperty("mekronize.baseUrl") as String?) ?: "https://api.example.com"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures { buildConfig = true; viewBinding = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.version", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST", "META-INF/AL2.0", "META-INF/LGPL2.1", "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json", "**/*.kotlin_module", "META-INF/versions/**",
        )
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("UnsafeOptInUsageError", "UseAppTint", "MissingIntentFilterForMediaSearch")
    }
}

androidComponents {
    onVariants { v ->
        v.instrumentation.transformClassesWith(CompatFactory::class.java, InstrumentationScope.ALL) {}
        v.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.guava:guava:33.3.1-android")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.0")

    testImplementation("junit:junit:4.13.2")
}
