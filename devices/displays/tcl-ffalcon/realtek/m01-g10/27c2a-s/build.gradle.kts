plugins { id("com.android.library") }
android {
    namespace = "com.xiaomi.mitv.light.devices.tcl.model27c2as"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    sourceSets["main"].assets.srcDir("config")
    sourceSets["test"].java.srcDir("tests")
}
dependencies { api(project(":device-api")); testImplementation("junit:junit:4.13.2") }
tasks.withType<JavaCompile>().configureEach { options.debugOptions.debugLevel="source,lines" }
