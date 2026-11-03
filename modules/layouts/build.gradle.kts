plugins { `java-library` }
java { sourceCompatibility=JavaVersion.toVersion(providers.gradleProperty("frameLuma.javaVersion").get());targetCompatibility=JavaVersion.toVersion(providers.gradleProperty("frameLuma.javaVersion").get()) }
sourceSets.main { java.setSrcDirs(listOf(".")) }
dependencies { api(project(":core")) }
val generatedLayoutDir=layout.buildDirectory.dir("generated/layouts")
val generateLayouts=tasks.register("generateLayouts") {
    inputs.file("config.json");outputs.dir(generatedLayoutDir)
    doLast {
        val configuration=groovy.json.JsonSlurper().parse(file("config.json")) as Map<*,*>
        val entries=(configuration["profiles"] as List<*>).map{it as Map<*,*>}
        require(entries.map{it["id"]}.distinct().size==entries.size){"Duplicate layout profile"}
        require(entries.all{Regex("[a-z0-9]+(?:_[a-z0-9]+)*").matches(it["id"].toString())}){"Invalid layout profile id"}
        val defaultProfile=entries.singleOrNull{it["default"]==true}?:error("Declare exactly one default layout")
        fun quoted(value:Any?)=groovy.json.JsonOutput.toJson(value.toString())
        fun words(value:Any?):String {
            val rows=(value as Map<*,*>).map { (locale,text) ->
                val values=listOf(locale)+(if(text is List<*>)text else listOf(text))
                "new String[]{"+values.joinToString(","){quoted(it)}+"}"
            }
            return "new String[][]{"+rows.joinToString(",")+"}"
        }
        val constructors=entries.joinToString(","){
            "new LayoutCatalog.Configured("+quoted(it["id"])+",new ${it["entry_class"]}(),"+
            listOf("name","hint","left","right","labels").joinToString(","){key->words(it[key])}+")"
        }
        val file=generatedLayoutDir.get().file("frameluma/layouts/GeneratedLayouts.java").asFile;file.parentFile.mkdirs()
        file.writeText("package frameluma.layouts; final class GeneratedLayouts { static final String DEFAULT_ID=\"${defaultProfile["id"]}\"; static LayoutProfile[] create(){return new LayoutProfile[]{ $constructors };} }")
    }
}
sourceSets.main { java.srcDir(generatedLayoutDir) }
tasks.named("compileJava"){dependsOn(generateLayouts)}
