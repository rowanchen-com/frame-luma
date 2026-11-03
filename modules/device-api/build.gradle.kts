plugins { id("com.android.library") }
val identity = groovy.json.JsonSlurper().parse(rootProject.file("gradle/config.json")) as Map<*, *>
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "frameluma.deviceapi"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "OWNER_PACKAGE", groovy.json.JsonOutput.toJson(identity["application_id"]))
        buildConfigField("String", "OWNER_CERTIFICATE", groovy.json.JsonOutput.toJson(identity["certificate_sha256"]))
    }
}
dependencies { api(project(":core")) }
