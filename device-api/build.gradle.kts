plugins { id("com.android.library") }
android {
    namespace = "com.xiaomi.mitv.light.deviceapi"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
}
dependencies { api(project(":core")) }
