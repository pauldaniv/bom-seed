import groovy.util.Node
import java.io.StringReader
import java.util.*

plugins {
    `maven-publish`
}

group = "com.paul"
version = getTagOrDefault("1.0.0-SNAPSHOT")

val deployUsr: String = (project.findProperty("gpr.usr") ?: System.getenv("USERNAME") ?: "").toString()
val deployKey: String = (project.findProperty("gpr.key") ?: System.getenv("TOKEN") ?: System.getenv("GITHUB_TOKEN")).toString()
val repoKey: String = System.getenv("GITHUB_REPOSITORY") ?: "$deployUsr/${projectDir.name}"
val publishUrl: String = "https://maven.pkg.github.com/$repoKey"

publishing {
    repositories {
        mavenLocal()
//        maven {
//            name = "GitHub"
//            url = uri(publishUrl)
//            credentials {
//                username = deployUsr
//                password = deployKey
//            }
//        }
    }
    publications {
        create<MavenPublication>("maven") {
            pom.withXml {
                val project = asNode()
                val srcDir = "$projectDir/versions"

                val versionProperties = Properties()
                versionProperties.load(StringReader(file("$srcDir/versions.properties").readText()))

                val propertiesNode = Node(project, "properties")
                versionProperties.entries.forEach { propertiesNode.appendNode(it.key, it.value) }

                val dependencyManagement = Node(project, "dependencyManagement")
                val dependencies = Node(dependencyManagement, "dependencies")

                file("$srcDir/dependencies").listFiles()?.forEach {
                    val groupIdFromFile = it.name.removeSuffix(".prooperties")
                    val dependencyVersions = Properties()
                    dependencyVersions.load(StringReader(it.readText()))
                    dependencyVersions.entries.forEach { v ->
                        val dependency = Node(dependencies, "dependency")
                        dependency.appendNode("groupId", groupIdFromFile)
                        dependency.appendNode("artifactId", v.key)
                        dependency.appendNode("version",
                                if (versionProperties.containsKey(v.value)) v.value else "\${$v.value}")
                    }
                }
            }
        }
    }
}


fun getTagOrDefault(defaultValue: String): String {
    val ref: String = System.getenv("GITHUB_REF") ?: return defaultValue
    return if (ref.startsWith("refs/tags/"))
        ref.substring("refs/tags/".length)
    else
        defaultValue
}
