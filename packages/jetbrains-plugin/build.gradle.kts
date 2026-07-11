plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.jiuwenswarm"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        instrumentationTools()
    }

    // OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.jiuwenswarm.ide-plugin"
        name = "JiuwenSwarm"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "231"
            untilBuild = provider { null }
        }

        changeNotes = """
            <b>0.1.0</b> — Initial release (Phase 1)
            <ul>
              <li>Chat panel with streaming responses</li>
              <li>Session management (create / switch)</li>
              <li>Tool call display</li>
              <li>Connection status widget</li>
            </ul>
        """.trimIndent()
    }

    signing {
        // Configure for Marketplace publishing; leave empty for local dev
    }

    publishing {
        // token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    }
}

tasks {
    wrapper {
        gradleVersion = "8.7"
    }
}

tasks.named("instrumentCode") {
    enabled = false
}