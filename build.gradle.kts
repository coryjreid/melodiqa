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
version = "1.1.0"

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
        // jdk.crypto.mscapi removed: Windows certificate store not used by Melodiqa
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

    targetPlatform("win-x64") {
        jdkHome = System.getProperty("java.home")  // Use the JDK running the build
    }

    targetPlatform("linux-x64") {
        jdkHome = findProperty("linuxJdkHome")?.toString()
            ?: error("linuxJdkHome must be set in ~/.gradle/gradle.properties to build the Linux distribution")
    }

    jpackage {
        targetPlatformName = "win-x64"   // Required when targetPlatform entries are declared
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

tasks.register<Zip>("runtimeZipWinX64") {
    group = "distribution"
    description = "Zips the win-x64 runtime image"
    dependsOn("runtime")
    from(layout.buildDirectory.dir("image/${project.name}-${version}/${project.name}-win-x64"))
    into("${project.name}-win-x64")
    archiveFileName.set("${project.name}-${version}-win-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("image"))
}

tasks.register<Zip>("runtimeZipLinuxX64") {
    group = "distribution"
    description = "Zips the linux-x64 runtime image"
    dependsOn("runtime")
    from(layout.buildDirectory.dir("image/${project.name}-${version}/${project.name}-linux-x64")) {
        into("${project.name}-linux-x64")
        exclude("bin/**")
    }
    from(layout.buildDirectory.dir("image/${project.name}-${version}/${project.name}-linux-x64/bin")) {
        into("${project.name}-linux-x64/bin")
        filePermissions { unix(0b111_101_101) }  // 0755 — executable bit stripped by Windows filesystem
    }
    archiveFileName.set("${project.name}-${version}-linux-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("image"))
}

tasks.register("cleanStop") {
    description = "Triggers a graceful shutdown by creating the stop file"
    group = "application"

    doLast {
        val os = System.getProperty("os.name").lowercase()
        val stopFile = if (os.contains("win")) {
            val appData = System.getenv("APPDATA") ?: error("APPDATA environment variable is not set")
            file("$appData/Aezshma/Melodiqa/.stop")
        } else {
            file("${System.getProperty("user.home")}/.local/share/Aezshma/Melodiqa/.stop")
        }
        stopFile.parentFile.mkdirs()
        stopFile.createNewFile()
        println("Created stop file: ${stopFile.absolutePath}")
    }
}
