plugins { `java-library` }
java { sourceCompatibility=JavaVersion.toVersion(providers.gradleProperty("frameLuma.javaVersion").get());targetCompatibility=sourceCompatibility }
sourceSets.main { java.setSrcDirs(listOf(".")) }
