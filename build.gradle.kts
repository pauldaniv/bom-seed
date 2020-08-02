import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import groovy.util.Node
import java.nio.file.Files
import java.nio.file.Paths.get

buildscript {
  dependencies {
    classpath("com.fasterxml.jackson.core:jackson-databind:2.11.0")
    classpath("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.0")
    classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.0")
  }
}

plugins {
  base
  `maven-publish`
}

group = "com.paul"
version = getBomVersionParts().joinToString(".")

val deployUsr: String = (project.findProperty("gpr.usr") ?: System.getenv("USERNAME") ?: "").toString()
val deployKey: String = (project.findProperty("gpr.key") ?: System.getenv("TOKEN")
?: System.getenv("GITHUB_TOKEN")).toString()
val repoKey: String = System.getenv("GITHUB_REPOSITORY") ?: "$deployUsr/${projectDir.name}"
val publishUrl: String = "https://maven.pkg.github.com/$repoKey"

publishing {
  repositories {
    maven {
      name = "GitHub"
      url = uri(publishUrl)
      credentials {
        username = deployUsr
        password = deployKey
      }
    }
  }
  publications {
    create<MavenPublication>("maven") {
      pom.withXml {
        val project = asNode()
        val srcDir = "$projectDir/versions"
        val versions = loadMap("$srcDir/versions.yml")
        val propertiesNode = Node(project, "properties")

        versions.forEach { propertiesNode.appendNode("${it.key}.version", it.value) }

        val dependencyManagement = Node(project, "dependencyManagement")
        val dependencies = Node(dependencyManagement, "dependencies")

        loadTree("$srcDir/dependencies.yml")?.forEach {
          val dependency = Node(dependencies, "dependency")
          dependency.appendNode("groupId", it.first)
          dependency.appendNode("artifactId", it.second)
          dependency.appendNode("version", when {
            versions.containsKey(it.third) -> "\${${it.third}.version}"
            else -> it.third
          })
        }
      }
    }
  }
}


tasks.register("bumpMajorVersion") {
  doLast{
    val version = getBomVersionParts()
    val newVersion = version[0].toInt() + 1
    saveVersion("$newVersion.0.0")
  }
}

tasks.register("bumpMinorVersion") {
  doLast {
    val version = getBomVersionParts()
    val newVersion = version[1].toInt() + 1
    saveVersion("${version[0]}.$newVersion.0")
  }
}

tasks.register("bumpBuildVersion") {
  doLast {
    val version = getBomVersionParts()
    val newVersion = version[2].toInt() + 1
    saveVersion("${version[0]}.${version[1]}.$newVersion")
  }
}

fun getBomVersionParts() = File("version.txt").bufferedReader().readLine().split(".")
fun saveVersion(version: String) = File("version.txt").writeText(version)

val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

fun loadMap(path: String): Map<String, String> = loadFile(path)

fun loadTree(path: String): List<Triple<String, String, String>>? = mapYml<Entry>(path)?.map { it.toEntry() }

fun <T : Any> mapYml(path: String): List<Entry>? {
  val yml: Map<String, T>? = loadFile(path)
  val children = mutableListOf<Entry>()
  fun <T : Any> map(yml: Map<String, T>?, parent: Entry? = null): List<Entry> {
    yml?.entries?.forEach {
      val entry = Entry(it.key, if (it.value is Map<*, *>) null else it.value.toString(), parent)
      @Suppress("UNCHECKED_CAST")
      when (it.value) {
        is Map<*, *> -> map(it.value as Map<String, T>, entry)
        else -> children.add(entry)
      }
    }
    return children
  }
  return map(yml)
}

fun <T> loadFile(path: String): Map<String, T> = Files.newBufferedReader(get(path)).use {
  mapper.readValue(it)
}

data class Entry(val key: String, val value: String?, val parent: Entry?) {
  fun toEntry(): Triple<String, String, String> {
    var tmpParent = parent
    val parts = mutableListOf<String>()
    while (tmpParent != null) {
      parts.add(0, tmpParent.key)
      tmpParent = tmpParent.parent
    }
    return Triple(parts.joinToString("."), this.key, value!!)
  }
}
