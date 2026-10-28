pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "frame-luma"
include(":core", ":device-api", ":runtime", ":app")

// A model is a self-contained library. Discovery is build-time, never arbitrary on-device code loading.
file("devices").walkTopDown().filter { it.isFile && it.name == "module.json" }.sortedBy { it.path }.forEach {
    val folder = it.parentFile
    require(folder.resolve("build.gradle.kts").isFile) { "Missing model build: $folder" }
    val modulePath = ":devices:" + folder.relativeTo(file("devices")).invariantSeparatorsPath.replace('/', ':')
    include(modulePath)
    project(modulePath).projectDir = folder
}
