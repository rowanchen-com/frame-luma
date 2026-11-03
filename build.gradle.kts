buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21") }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("com.android.library") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

fun Project.configureAdapter() {
    apply(plugin="com.android.library")
    val config=groovy.json.JsonSlurper().parse(file("config.json")) as Map<*,*>
    val consumerRules=file("consumer-rules.pro")
    extensions.configure<com.android.build.api.dsl.LibraryExtension> {
        namespace="frameluma.adapters."+config["id"].toString().replace('-', '_')
        if(consumerRules.isFile)defaultConfig { consumerProguardFiles(consumerRules) }
    }
    dependencies {
        add("api",project(":device-api"))
        (config["dependencies"] as? List<*>)?.forEach { dependency ->
            val notation=dependency.toString()
            add("implementation",if(notation.startsWith(":"))project(notation) else notation)
        }
    }
}

// Keep generated classes, Android intermediates and reports out of all source modules.
layout.buildDirectory.set(layout.projectDirectory.dir(".local/build/root"))
subprojects {
    layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".local/build/" + path.removePrefix(":").replace(':', '/')))
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            enableKotlin=false
            compileSdk=providers.gradleProperty("frameLuma.compileSdk").get().toInt()
            buildToolsVersion=providers.gradleProperty("frameLuma.buildTools").get()
            defaultConfig { minSdk=providers.gradleProperty("frameLuma.minSdk").get().toInt() }
            compileOptions {
                sourceCompatibility=JavaVersion.toVersion(providers.gradleProperty("frameLuma.javaVersion").get())
                targetCompatibility=JavaVersion.toVersion(providers.gradleProperty("frameLuma.javaVersion").get())
            }
            sourceSets.named("main") {
                java.directories.clear();java.directories.add(".")
                if(project.file("AndroidManifest.xml").isFile)manifest.srcFile("AndroidManifest.xml")
                res.directories.clear();res.directories.add("res")
            }
        }
    }
    if(projectDir.parentFile.name in listOf("displays","lights","transports") && projectDir.resolve("config.json").isFile)
        configureAdapter()
}
