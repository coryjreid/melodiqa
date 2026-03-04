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
    implementation("info.picocli:picocli:4.7.7")

    implementation("ch.qos.logback:logback-core:1.5.21")
    implementation("ch.qos.logback:logback-classic:1.5.21")
    implementation(platform("org.slf4j:slf4j-bom:2.0.17"))

    implementation("net.dv8tion:JDA:6.3.1")
    implementation("com.typesafe:config:1.4.5")
    implementation("com.google.guava:guava:33.5.0-jre")

    // DAVE Protocol - https://daveprotocol.com
    // Interface to use for libraries
    implementation("club.minnced:jdave-api:0.1.7")
    // Compiled natives for libdave for the specified platform
    implementation("club.minnced:jdave-native-linux-x86-64:0.1.7")
    implementation("club.minnced:jdave-native-linux-aarch64:0.1.7")
    implementation("club.minnced:jdave-native-win-x86-64:0.1.7")
    implementation("club.minnced:jdave-native-darwin:0.1.7")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
