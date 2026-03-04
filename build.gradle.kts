plugins {
    java
    application
    id("com.gradleup.shadow") version "9.3.0"
    id("org.beryx.runtime") version "2.0.1"
    id("com.github.ben-manes.versions") version "0.53.0"
    id("se.bjurr.gradle.update-versions") version "1.5.7"
}

repositories {
    mavenCentral()
}

group = "com.aezshma.melodiqa"
version = "1.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("com.aezshma.melodiqa.Melodiqa")
}

runtime {
    options.set(listOf("--strip-debug", "--compress", "0", "--no-header-files", "--no-man-pages"))
    imageDir.set(file(layout.buildDirectory.dir("image/${project.name}-${version}")))
    imageZip.set(file(layout.buildDirectory.dir("image/${project.name}-${version}.zip")))
    addModules(
        "java.se",
        "jdk.accessibility",
        "jdk.charsets",
        "jdk.crypto.cryptoki",
        "jdk.crypto.ec",
        "jdk.crypto.mscapi",
        "jdk.httpserver",
        "jdk.jsobject",
        "jdk.localedata",
        "jdk.net",
        "jdk.security.auth",
        "jdk.security.jgss",
        "jdk.unsupported",
        "jdk.unsupported.desktop",
        "jdk.xml.dom"
    )

    jpackage {
        imageName = "Melodiqa"
        imageOptions = listOf("--win-console")
        skipInstaller = false
        installerName = "Melodiqa"
        installerType = "msi"
        installerOptions = listOf("--win-per-user-install", "--win-dir-chooser", "--win-menu", "--win-shortcut")
    }
}

dependencies {
    implementation(libs.picocli)

    implementation(libs.logback.core)
    implementation(libs.logback.classic)
    implementation(platform(libs.slf4j))

    implementation(libs.jda)
    implementation(libs.typesafe.config)
    implementation(libs.guava)

    // DAVE Protocol - https://daveprotocol.com
    // Interface to use for libraries
    implementation(libs.jdave.api)
    // Compiled natives for libdave for the specified platform
    implementation(libs.jdave.linux.amd64)
    implementation(libs.jdave.linux.aarch64)
    implementation(libs.jdave.win.amd64)
    implementation(libs.jdave.darwin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("cleanStop") {
    description = "Creates the stop file to gracefully shut down the bot"
    group = "application"

    doLast {
        val stopFile = file("${System.getenv("APPDATA")}/Aezshma/Melodiqa/.stop")
        stopFile.parentFile.mkdirs()
        stopFile.createNewFile()
        println("Created stop file: ${stopFile.absolutePath}")
    }
}
