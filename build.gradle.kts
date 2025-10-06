plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    // Use your installed IntelliJ IDEA as the development/runtime IDE to guarantee compatibility.
    // Set IDEA_HOME to your IntelliJ Community/Ultimate installation folder (matching build 252.*).
    // Example Windows:  set IDEA_HOME="C:/Program Files/JetBrains/IntelliJ IDEA Community Edition 2025.2"
    localIde(setupDependencies = true).set(file(System.getenv("IDEA_HOME") ?: ""))

    // If you prefer to pin a specific IDE instead of localIde, switch to:
    // defaultDependencies {
    //     localPlugin() // or specify platform type/version once confirmed
    // }
}

tasks {
    patchPluginXml {
        sinceBuild.set("252")
        // No untilBuild to avoid artificially blocking newer IDEs
    }

    signPlugin {
        // Optional: configure if you plan to publish
        enabled = false
    }
    publishPlugin {
        enabled = false
    }
}
