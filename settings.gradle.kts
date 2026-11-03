pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "frame-luma"
include(":app", ":devices", ":transports")
project(":devices").projectDir = file("modules")
project(":transports").projectDir = file("modules/transports")
// Each adapter owns one config.json; source folders select the build projects.
listOf("core", "layouts", "device-api", "runtime").forEach { name ->
    include(":$name")
    project(":$name").projectDir = file("modules/" + name.replace(':', '/'))
}
val devices = file("modules")
devices.walkTopDown().filter { it.isFile && it.name == "config.json" && it.parentFile.parentFile.name in listOf("displays","lights") }.sortedBy { it.path }.forEach {
    val folder = it.parentFile
    val modulePath = ":devices:" + folder.relativeTo(devices).invariantSeparatorsPath.replace('/', ':')
    include(modulePath)
    project(modulePath).projectDir = folder
    val parts = folder.relativeTo(devices).invariantSeparatorsPath.split('/')
    for (depth in 1 until parts.size) {
        val parent = parts.take(depth)
        project(":devices:" + parent.joinToString(":" )).projectDir = devices.resolve(parent.joinToString("/"))
    }
}

file("modules/transports").listFiles()?.filter { it.isDirectory && (it.resolve("config.json").isFile || it.resolve("build.gradle.kts").isFile) }?.forEach { folder ->
    include(":transports:"+folder.name)
    project(":transports:"+folder.name).projectDir=folder
}
