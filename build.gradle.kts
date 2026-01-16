plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin")
}

group = "btc.renaud"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.typewritermc.com/beta/")
}

dependencies {
    implementation("com.typewritermc:QuestExtension:0.9.0")
    implementation("com.typewritermc:BasicExtension:0.9.0")
    implementation("com.typewritermc:EntityExtension:0.9.0")
    implementation("com.typewritermc:RoadNetworkExtension:0.9.0")
}

typewriter {
    namespace = "renaud"

    extension {
        name = "Compagnion"
        shortDescription = "Typewriter extension for Compagnion support."
        description =
            "This extension adds support for Compagnion in Typewriter like a npc can follow you and interact with the environment."
        engineVersion = file("../../version.txt").readText().trim()
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        dependencies {
            dependency("typewritermc", "Entity")
            paper()
        }
    }

}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}


