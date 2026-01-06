plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("co.uzzu.dotenv.gradle") version "2.0.0"
    `maven-publish`
}

group = "gg.aquatic.holograms"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.nekroplex.com/releases")
}

dependencies {
    paperweight.paperDevBundle("1.21.10-R0.1-SNAPSHOT")
    implementation("gg.aquatic.execute:Execute:1.0.2")
    implementation("gg.aquatic.items:Items:1.0.0")
    implementation("gg.aquatic.packetutils:PacketUtils:1.0.0")
    implementation("gg.aquatic.eventutils:EventUtils:1.0.0")
    implementation("gg.aquatic.replace:Replace:1.0.0")
}


kotlin {
    jvmToolchain(21)
}

val maven_username = if (env.isPresent("MAVEN_USERNAME")) env.fetch("MAVEN_USERNAME") else ""
val maven_password = if (env.isPresent("MAVEN_PASSWORD")) env.fetch("MAVEN_PASSWORD") else ""

publishing {
    repositories {
        maven {
            name = "aquaticRepository"
            url = uri("https://repo.nekroplex.com/releases")

            credentials {
                username = maven_username
                password = maven_password
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "gg.aquatic.holograms"
            artifactId = "Holograms"
            version = "${project.version}"
            from(components["java"])
        }
    }
}