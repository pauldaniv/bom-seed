import java.io.StringReader
import java.util.*

plugins {
    `maven-publish`
}

group = "com.fym"
version = getTagOrDefault("1.0.0-SNAPSHOT")


publishing {
    repositories {
        maven {
            name = "GitHub"
            url = uri("https://maven.pkg.github.com/" + System.getenv("GITHUB_REPOSITORY"))
            credentials {
                username = ""
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        maven(MavenPublication) {
            pom.withXml {
                var xml = asNode()

                xml.children().last() + {
                    var mkp = delegate

                    var versionPropertiesFile: File = File("$projectDir/src/main/versions/_versions.properties")
                    var versionProperties: Properties = Properties()
                    versionProperties.load(StringReader(versionPropertiesFile.toString()))

                    mkp.properties {
                        for (dep in versionProperties.entrySet()) {
                            mkp."${dep.key}"(dep.value)
                        }
                    }

                    mkp.dependencyManagement {
                        mkp.dependencies {
                            for (propertyFile: File in File("$projectDir/src/main/versions").listFiles()) {

                                if (propertyFile.name == '_versions.properties') {
                                    continue
                                }

                                val groupIdFromFile: String = propertyFile.name - '.properties'

                                val versions: = Properties()
                                versions.load(StringReader(propertyFile.toString()))

                                for (pair in versions.entrySet()) {
                                    mkp.dependency {
                                        val finalVersion = pair . value ==~ /\d.*/ ? pair.value : "\${$pair.value}"
                                        mkp.groupId groupIdFromFile
                                                mkp.artifactId pair . key
                                                mkp.version finalVersion
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getTagOrDefault(defaultValue: String): String {
    val ref: String = System.getenv("GITHUB_REF") ?: return defaultValue
    if (ref.startsWith("refs/tags/")) {
        return ref.substring("refs/tags/".length)
    }
    return defaultValue
}
