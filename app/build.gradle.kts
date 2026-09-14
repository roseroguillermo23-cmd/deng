plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.noqira.driver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noqira.driver"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-test"

        buildConfigField("String", "SUPABASE_URL", "\"https://isplaovynkkzlfynqvsp.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_ZUchVL8-v5R3s48aeFBqLg_L6aCmvGo\"")
        buildConfigField("String", "BUSINESS_KEY", "\"licoreria-danny-cardona\"")
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
