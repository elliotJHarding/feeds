import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "com.harding.feeds"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.harding.feeds"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${property("feeds.googleWebClientId")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${property("feeds.apiBaseUrl")}\"")
    }

    signingConfigs {
        create("release") {
            // Credentials come from ~/.gradle/gradle.properties or env — never committed.
            val storeFilePath = (findProperty("FEEDS_RELEASE_STORE_FILE") as String?)
                ?: System.getenv("FEEDS_RELEASE_STORE_FILE")
            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = (findProperty("FEEDS_RELEASE_STORE_PASSWORD") as String?)
                    ?: System.getenv("FEEDS_RELEASE_STORE_PASSWORD")
                keyAlias = (findProperty("FEEDS_RELEASE_KEY_ALIAS") as String?)
                    ?: System.getenv("FEEDS_RELEASE_KEY_ALIAS")
                keyPassword = (findProperty("FEEDS_RELEASE_KEY_PASSWORD") as String?)
                    ?: System.getenv("FEEDS_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Fall back to debug signing when release creds aren't present, so the
            // build never silently emits an unsigned APK on a machine without them.
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning
                else signingConfigs.getByName("debug")
            isMinifyEnabled = false
            // Released build talks to the deployed server over HTTPS (debug keeps the
            // LAN/emulator URL from feeds.apiBaseUrl in defaultConfig).
            buildConfigField("String", "API_BASE_URL", "\"https://feeds.grubplanner.co.uk/api/\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// API client generated from the monorepo contract - the exact setup documented in
// ../model/README.md (kotlin generator, jvm-retrofit2, gson, coroutines).
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/../model/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    packageName.set("com.harding.feeds.client")
    library.set("jvm-retrofit2")
    configOptions.set(mapOf(
        "serializationLibrary" to "gson",
        "dateLibrary" to "java8",
        "useCoroutines" to "true"
    ))
}

tasks.named("preBuild") { dependsOn("openApiGenerate") }

dependencies {
    // Required by the generated client (see model/README.md - the generator does not add these)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.glance.appwidget)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.security.crypto)

    implementation(libs.coroutines.android)
}
