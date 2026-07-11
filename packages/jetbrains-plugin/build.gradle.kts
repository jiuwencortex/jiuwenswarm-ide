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
        // Use IDEA Community for the dev sandbox — avoids the broken xi:include in
        // PyCharm Community 2024.1 on Apple Silicon. The plugin depends only on
        // com.intellij.modules.platform so it installs in PyCharm, IDEA, WebStorm, etc.
        intellijIdeaCommunity("2024.1")
        instrumentationTools()
    }

    // OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Kotlin coroutines (used by IntelliJ anyway, included in platform)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
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
        // description intentionally omitted here — the full HTML description lives in plugin.xml

        ideaVersion {
            sinceBuild = "231"   // 2023.1+
            untilBuild = provider { null }  // no upper limit
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
