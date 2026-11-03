import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins { id("com.android.library") }
extensions.configure<com.android.build.api.dsl.LibraryExtension> { namespace="frameluma.runtime" }
val applicationIdentity = groovy.json.JsonSlurper().parse(rootProject.file("gradle/config.json")) as Map<*, *>
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    buildFeatures { resValues = true }
    defaultConfig { resValue("string", "recovery_account_type", applicationIdentity["application_id"].toString()) }
}
dependencies {
    api(project(":device-api"));api(project(":layouts"))
    rootProject.allprojects.filter { (it.path.startsWith(":devices:") || it.path.startsWith(":transports:")) && it.projectDir.resolve("config.json").isFile }.forEach { api(project(it.path)) }
}

abstract class GenerateDeviceRegistry : DefaultTask() {
    @get:InputFiles abstract val descriptors: ConfigurableFileCollection
    @get:OutputDirectory abstract val javaDirectory: DirectoryProperty
    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty
    @TaskAction fun generate() {
        val parser = groovy.json.JsonSlurper()
        val modules=descriptors.files.sortedBy { it.path }.map { parser.parse(it) as Map<*,*> }
        require(modules.map { it["id"] }.distinct().size==modules.size) { "Duplicate adapter id" }
        val file = javaDirectory.get().file("frameluma/runtime/GeneratedDevices.java").asFile
        file.parentFile.mkdirs()
        
        val displays = modules.filter { it["kind"] == "display" }.joinToString(",\n") { "new ${it["entry_class"]}(frameluma.deviceapi.ModuleConfig.load(context,${groovy.json.JsonOutput.toJson(it["id"])}))" }
        val transports = modules.filter { it["kind"] == "transport" }.joinToString(",\n") { "new ${it["entry_class"]}(frameluma.deviceapi.ModuleConfig.load(context,${groovy.json.JsonOutput.toJson(it["id"])}))" }
        val lights = modules.filter { it["kind"] == "light" }.joinToString(",\n") {
            val config = "frameluma.deviceapi.ModuleConfig.load(context,${groovy.json.JsonOutput.toJson(it["id"])})"
            "new ${it["entry_class"]}($config, transport(transports, $config))"
        }
        file.writeText("""package frameluma.runtime;
            public final class GeneratedDevices {
              private GeneratedDevices() {}
              public static frameluma.deviceapi.DisplayModule[] displays(android.content.Context context) {
                return new frameluma.deviceapi.DisplayModule[] { $displays };
              }
              public static frameluma.deviceapi.LightModule[] lights(android.content.Context context) {
                frameluma.deviceapi.TransportModule[] transports = transports(context);
                return new frameluma.deviceapi.LightModule[] { $lights };
              }
              private static frameluma.deviceapi.TransportModule[] transports(android.content.Context context) {
                return new frameluma.deviceapi.TransportModule[] { $transports };
              }
              private static frameluma.deviceapi.TransportModule transport(frameluma.deviceapi.TransportModule[] modules, frameluma.deviceapi.ModuleConfig config) {
                String id = config.section("transport").string("provider");
                for (frameluma.deviceapi.TransportModule module : modules) if (id.equals(module.id())) return module;
                throw new IllegalArgumentException("Unknown transport provider: " + id);
              }
            }
        """.trimIndent())
        val assets = assetsDirectory.get().asFile
        require(assets.canonicalFile.parentFile==javaDirectory.get().asFile.canonicalFile.parentFile)
        assets.deleteRecursively()
        assets.mkdirs()
        val moduleAssets = assets.resolve("modules"); moduleAssets.mkdirs()
        moduleAssets.resolve("catalog.json").writeText(groovy.json.JsonOutput.toJson(modules))
        modules.forEach { module ->
            val directory=moduleAssets.resolve(module["id"].toString());directory.mkdirs()
            directory.resolve("config.json").writeText(groovy.json.JsonOutput.toJson(module))
        }
    }
}
val generateRegistry = tasks.register<GenerateDeviceRegistry>("generateRegistry") {
    descriptors.from(rootProject.fileTree("modules/displays") { include("*/config.json") },rootProject.fileTree("modules/lights") { include("*/config.json") },rootProject.fileTree("modules/transports") { include("*/config.json") })
    javaDirectory.set(layout.buildDirectory.dir("generated/registry/java"))
    assetsDirectory.set(layout.buildDirectory.dir("generated/registry/assets"))
}
androidComponents.onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(generateRegistry, GenerateDeviceRegistry::javaDirectory)
    variant.sources.assets?.addGeneratedSourceDirectory(generateRegistry, GenerateDeviceRegistry::assetsDirectory)
}

tasks.withType<JavaCompile>().configureEach { options.debugOptions.debugLevel = "source,lines" }
