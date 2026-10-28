import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val policy = groovy.json.JsonSlurper().parse(rootProject.file("signing/policy.json")) as Map<*, *>
val unsigned = providers.gradleProperty("unsignedBuild").orNull == "true"
val keyPath = System.getenv("FRAME_LUMA_KEYSTORE") ?: local.getProperty("frameLuma.keystore")
val signingPassword = System.getenv("FRAME_LUMA_KEYSTORE_PASSWORD") ?: local.getProperty("frameLuma.keystorePassword")
val signingAlias = policy["key_alias"].toString()
android {
    namespace = "com.xiaomi.mitv.light"
    compileSdk = 34
    defaultConfig { applicationId = "com.xiaomi.mitv.light"; minSdk = 21; targetSdk = 34; versionCode = 120; versionName = "0.1.0-alpha.13" }
    signingConfigs {
        create("pinned") {
            if(keyPath != null)storeFile=rootProject.file(keyPath)
            storePassword=signingPassword;keyPassword=signingPassword;keyAlias=signingAlias
        }
    }
    buildTypes {
        getByName("debug") { signingConfig=null }
        getByName("release") { isMinifyEnabled=false;signingConfig=if(unsigned)null else signingConfigs.getByName("pinned") }
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_1_8;targetCompatibility=JavaVersion.VERSION_1_8 }
    kotlinOptions { jvmTarget="1.8" }
}
dependencies { implementation(project(":runtime")); testImplementation("junit:junit:4.13.2") }
val verifyPinnedSigning by tasks.registering {
    doLast {
        require(!keyPath.isNullOrBlank()&&!signingPassword.isNullOrBlank()) { "Set existing signing key in ignored local.properties or FRAME_LUMA_*; no key will be generated. Use -PunsignedBuild=true for an unsigned contributor build." }
        val keyFile=rootProject.file(keyPath!!);require(keyFile.isFile) { "Existing signing key is missing" }
        val ks=KeyStore.getInstance("JKS");keyFile.inputStream().use { ks.load(it,signingPassword!!.toCharArray()) }
        val cert=ks.getCertificate(signingAlias)?:error("Pinned key alias missing")
        val sha=MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(""){"%02x".format(it)}
        require(sha==policy["certificate_sha256"]) { "Signing certificate differs; restore the original key" }
    }
}
tasks.configureEach { if(!unsigned&&(name=="validateSigningRelease"||name=="packageRelease"))dependsOn(verifyPinnedSigning) }
