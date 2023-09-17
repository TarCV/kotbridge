plugins {
    id("conventions")

    `java-library`
    `maven-publish`
    signing
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("kotbridge") {
            from(components["java"])

            artifactId = project.name

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name.set("kotbridge")

                licenses {
                    license {
                        name.set("Apache License")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        name.set("TarCV")
                        url.set("https://github.com/TarCV")
                    }
                }

                scm {
                    connection.set("scm:git@github.com:TarCV/kotbridge.git")
                    developerConnection.set("scm:git@github.com:TarCV/kotbridge.git")
                    url.set("https://github.com/TarCV/kotbridge")
                }
            }
        }
    }

    signing {
        useInMemoryPgpKeys(
            System.getenv("GPG_PRIVATE_KEY"),
            System.getenv("GPG_PRIVATE_PASSWORD")
        )

        sign(publishing.publications["kotbridge"])
    }

    repositories {
        maven {
            val repoId = System.getenv("REPOSITORY_ID")

            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deployByRepositoryId/$repoId/")

            credentials {
                username = System.getenv("SONATYPE_USERNAME")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}