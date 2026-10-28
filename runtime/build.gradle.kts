plugins { id("com.android.library") }
android {
    namespace = "com.xiaomi.mitv.light.runtime"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
}
dependencies {
    api(project(":device-api"))
    rootProject.allprojects.filter { it.path.startsWith(":devices:") && it.projectDir.resolve("module.json").isFile }.forEach { api(project(it.path)) }
}

val generateRegistry by tasks.registering {
    val descriptors = rootProject.fileTree("devices") { include("**/module.json") }
    inputs.files(descriptors)
    val destination = layout.buildDirectory.dir("generated/registry")
    outputs.dir(destination)
    doLast {
        val parser = groovy.json.JsonSlurper()
        val modules = descriptors.files.sortedBy { it.path }.map { parser.parse(it) as Map<*, *> }
        require(modules.map { it["id"] }.distinct().size == modules.size) { "Duplicate device module id" }
        modules.forEach {
            require(it["schema_version"] == 1 && it["api_version"] == 1) { "Unsupported device contract" }
            require(it["kind"] in listOf("display", "light")) { "Unknown device kind" }
            require(Regex("[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)+").matches(it["entry_class"].toString()))
        }
        val file = destination.get().file("com/xiaomi/mitv/light/runtime/GeneratedDevices.java").asFile
        file.parentFile.mkdirs()
        val entries = modules.joinToString(",\n") { "new ${it["entry_class"]}()" }
        val displays=modules.filter{it["kind"]=="display"}.joinToString(",\n"){"new ${it["entry_class"]}()"}
        val lights=modules.filter{it["kind"]=="light"}.joinToString(",\n"){"new ${it["entry_class"]}()"}
        file.writeText("""package com.xiaomi.mitv.light.runtime;
            public final class GeneratedDevices {
              private GeneratedDevices() {}
              public static com.xiaomi.mitv.light.deviceapi.DeviceModule[] create() {
                return new com.xiaomi.mitv.light.deviceapi.DeviceModule[] { $entries };
              }
              public static com.xiaomi.mitv.light.deviceapi.DisplayModule[] displays() {
                return new com.xiaomi.mitv.light.deviceapi.DisplayModule[] { $displays };
              }
              public static com.xiaomi.mitv.light.deviceapi.LightModule[] lights() {
                return new com.xiaomi.mitv.light.deviceapi.LightModule[] { $lights };
              }
            }
        """.trimIndent())
        val assets = destination.get().dir("assets").asFile
        assets.mkdirs()
        assets.resolve("device-catalog.json").writeText(groovy.json.JsonOutput.toJson(modules))
        val moduleAssets=assets.resolve("modules");moduleAssets.mkdirs()
        modules.forEach { moduleAssets.resolve("${it["id"]}.json").writeText(groovy.json.JsonOutput.toJson(it)) }
    }
}
android.sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated/registry"))
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/registry/assets"))
tasks.named("preBuild") { dependsOn(generateRegistry) }
tasks.withType<JavaCompile>().configureEach { options.debugOptions.debugLevel="source,lines" }
