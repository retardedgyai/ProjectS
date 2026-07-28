plugins {
    java
}

group = "io.github.gyai"
version = "0.1.0"

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named("build") {
    doLast {
        if (!project.hasProperty("skipAutoStart")) {
            val script = layout.projectDirectory
                .file("scripts/deploy-and-start-server.ps1").asFile.absolutePath
            val result = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-File", script
            ).inheritIO().start().waitFor()
            if (result != 0) {
                throw GradleException("Plugin deployment or server launch failed.")
            }
        }
    }
}
