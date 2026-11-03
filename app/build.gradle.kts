import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins { id("com.android.application");id("org.jetbrains.kotlin.plugin.compose") }
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val policy = groovy.json.JsonSlurper().parse(rootProject.file("gradle/config.json")) as Map<*, *>
val unsigned = providers.gradleProperty("unsignedBuild").orNull == "true"
val keyPath = System.getenv("FRAME_LUMA_KEYSTORE") ?: local.getProperty("frameLuma.keystore")
val signingPassword = System.getenv("FRAME_LUMA_KEYSTORE_PASSWORD") ?: local.getProperty("frameLuma.keystorePassword")
val signingAlias = policy["key_alias"].toString()
extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "frameluma.app"
    compileSdk = providers.gradleProperty("frameLuma.compileSdk").get().toInt()
    buildToolsVersion = providers.gradleProperty("frameLuma.buildTools").get()
    defaultConfig {
        applicationId = policy["application_id"].toString(); minSdk = providers.gradleProperty("frameLuma.minSdk").get().toInt(); targetSdk = providers.gradleProperty("frameLuma.targetSdk").get().toInt(); versionCode = 1; versionName = "0.0.1"
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(versionName.orEmpty())) { "Use a numeric version such as 0.0.1" }
        ndk { abiFilters.addAll(listOf("armeabi-v7a","arm64-v8a")) }
    }
    sourceSets.named("main") {
        java.directories.clear(); java.directories.add("src")
        kotlin.directories.clear(); kotlin.directories.add("src")
    }
    buildFeatures { compose=true }
    splits { abi { isEnable = false } }
    signingConfigs {
        create("pinned") {
            if(keyPath != null)storeFile=rootProject.file(keyPath)
            storePassword=signingPassword;keyPassword=signingPassword;keyAlias=signingAlias
        }
        getByName("debug") {
            storeFile=if(keyPath!=null)rootProject.file(keyPath)else null
            storePassword=signingPassword;keyPassword=signingPassword;keyAlias=signingAlias
        }
    }
    buildTypes {
        getByName("debug") { signingConfig=null }
        getByName("release") {
            isMinifyEnabled=true
            isShrinkResources=true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
            signingConfig=if(unsigned)null else signingConfigs.getByName("pinned")
        }
    }
    compileOptions { sourceCompatibility=JavaVersion.toVersion(providers.gradleProperty("frameLuma.javaVersion").get());targetCompatibility=JavaVersion.toVersion(providers.gradleProperty("frameLuma.javaVersion").get()) }
}
dependencies {
    implementation(project(":runtime"))
    val composeBom=platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.tv:tv-material:1.1.0")
}

abstract class GenerateLicenseAssets : DefaultTask() {
    @get:InputFile abstract val projectLicense: RegularFileProperty
    @get:InputFile abstract val thirdPartyLicenses: RegularFileProperty
    @get:InputFiles abstract val runtimeArchives: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val folder = outputDirectory.dir("licenses").get().asFile
        folder.mkdirs()
        projectLicense.get().asFile.copyTo(folder.resolve("Apache-2.0.txt"), overwrite = true)
        val combined = StringBuilder(thirdPartyLicenses.get().asFile.readText())
        val extracted=linkedMapOf<String,Pair<String,MutableList<String>>>()
        for (artifact in runtimeArchives.files.sortedBy { it.name }) {
            if (artifact.extension !in listOf("jar", "aar")) continue
            ZipFile(artifact).use { archive ->
                val entries = archive.entries().asSequence().filter {
                    !it.isDirectory && Regex("(?i)(?:META-INF/)?(?:LICENSE|NOTICE)(?:[._-][^/]*)?").matches(it.name)
                }.sortedBy { it.name }.toList()
                for (entry in entries) {
                    val contents = archive.getInputStream(entry).bufferedReader().use { it.readText() }.replace("\r\n","\n").trim()
                    val digest=contents
                    extracted.getOrPut(digest){contents to mutableListOf()}.second.add("${artifact.name} / ${entry.name}")
                }
            }
        }
        for((contents,sources)in extracted.values){
            combined.append("\n\n"+"=".repeat(72)+"\n"+sources.distinct().joinToString("\n")+"\n"+"=".repeat(72)+"\n\n"+contents)
        }
        folder.resolve("THIRD_PARTY_LICENSES.txt").writeText(combined.toString())
    }
}
androidComponents.onVariants { variant ->
    val generator = tasks.register<GenerateLicenseAssets>("generate${variant.name.replaceFirstChar { it.uppercase() }}LicenseAssets") {
        projectLicense.set(rootProject.layout.projectDirectory.file("LICENSE"))
        thirdPartyLicenses.set(rootProject.layout.projectDirectory.file("NOTICE"))
        runtimeArchives.from(configurations.named("${variant.name}RuntimeClasspath").map { configuration ->
            configuration.incoming.artifactView { componentFilter { it is ModuleComponentIdentifier } }.files
        })
        outputDirectory.set(layout.buildDirectory.dir("generated/license-assets/${variant.name}"))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(generator, GenerateLicenseAssets::outputDirectory)
}

tasks.register("exportReleaseApk") {
    group = "distribution"
    description = "Export the installable artifact and public build metadata to .local/dist/"
    dependsOn("assembleRelease")
    val apkFolder = layout.buildDirectory.dir("outputs/apk/release")
    val outputName = if(unsigned)"frame-luma-unsigned.apk" else "frame-luma.apk"
    val output = rootProject.layout.projectDirectory.file(".local/dist/$outputName")
    val metadata = rootProject.layout.projectDirectory.file(".local/dist/" + if(unsigned)"unsigned-build-info.json" else "build-info.json")
    inputs.dir(apkFolder)
    val sourceCommit = providers.environmentVariable("GITHUB_SHA").orElse("local")
    inputs.property("sourceCommit", sourceCommit)
    outputs.file(output)
    outputs.file(metadata)
    doLast {
        val folder = apkFolder.get().asFile
        val built = groovy.json.JsonSlurper().parse(folder.resolve("output-metadata.json")) as Map<*, *>
        val elements = built["elements"] as List<*>
        require(elements.size == 1) { "One universal APK is required" }
        val element = elements.single() as Map<*, *>
        require((element["filters"] as List<*>).isEmpty()) { "ABI/density split artifacts are not supported" }
        val filename = element["outputFile"].toString()
        require(File(filename).name == filename && filename.endsWith(".apk")) { "Unexpected APK filename" }
        val source = folder.resolve(filename)
        require(source.isFile) { "Assembled APK is missing" }
        output.asFile.parentFile.mkdirs()
        source.copyTo(output.asFile, overwrite = true)
        val info = linkedMapOf(
            "application_id" to built["applicationId"],
            "version_code" to element["versionCode"],
            "version_name" to element["versionName"],
            "min_sdk" to providers.gradleProperty("frameLuma.minSdk").get().toInt(),
            "target_sdk" to providers.gradleProperty("frameLuma.targetSdk").get().toInt(),
            "apk_mode" to "universal",
            "source_commit" to sourceCommit.get(),
            "signed" to !unsigned,
            "certificate_sha256" to if(unsigned)null else policy["certificate_sha256"],
            "apk_file" to outputName
        )
        metadata.asFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(info)) + "\n")
        logger.lifecycle("Exported ${output.asFile}")
    }
}
