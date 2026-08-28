import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Keys live in local.properties, which is gitignored. Absent keys fall back to
// empty strings so the project still builds for anyone who clones it.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.fittrack"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        applicationId = "com.example.fittrack"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "XAI_API_KEY", "\"${localProps.getProperty("XAI_API_KEY", "")}\"")
        buildConfigField("String", "AI_PROVIDER", "\"${localProps.getProperty("AI_PROVIDER", "gemini")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${localProps.getProperty("GEMINI_MODEL", "gemini-flash-lite-latest")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    implementation(libs.mpandroidchart)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)

    // Firebase: the BoM pins every Firebase artifact to one compatible set.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    // Google sign-in through Credential Manager; GoogleSignInClient is deprecated.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Assistant networking. Provider-agnostic, so the base URL and DTOs are the
    // only things that change when swapping model vendors.
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android ships org.json as a stub that throws in JVM unit tests; this puts
    // the real implementation on the test classpath so parsing can be tested.
    testImplementation(libs.org.json)

}