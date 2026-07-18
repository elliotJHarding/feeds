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
        versionName = "0.1"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${property("feeds.googleWebClientId")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${property("feeds.apiBaseUrl")}\"")
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
