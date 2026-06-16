plugins {
    kotlin("jvm") version "2.3.20"
    id("com.typewritermc.module-plugin") version "2.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.typewritermc.com/beta/")
    maven("https://maven.typewritermc.com/external/")
    maven("https://jitpack.io")
    mavenLocal()
}

group = "btcrenaud"
version = "0.0.7"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.typewritermc:engine-paper")
    implementation("com.typewritermc:QuestExtension:0.9.0")
    implementation("com.typewritermc:BasicExtension:0.9.0")
    implementation("com.typewritermc:EntityExtension:0.9.0")
    implementation("com.typewritermc:RoadNetworkExtension:0.9.0")
}

typewriter {
    namespace = "btcrenaud"
    extension {
        name = "Companion"
        shortDescription = "Typewriter extension for Companion support."
        description = "This extension adds support for Companions in Typewriter — NPCs that follow the player and interact with the environment."
        engineVersion = "0.9.0-beta-174"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            dependency("typewritermc", "Entity")
        }
        paper()

        dependencies {
            dependency("typewritermc", "Quest")
            dependency("typewritermc", "Basic")
            dependency("typewritermc", "Entity")
            dependency("typewritermc", "RoadNetwork")
        }
    }
}

    

kotlin {
    jvmToolchain(25)
    
}
