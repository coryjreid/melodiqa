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

group = "com.coryjreid.melodiqa"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.coryjreid.melodiqa.Melodiqa")
}

runtime {
    options.set(listOf("--strip-debug", "--compress", "0", "--no-header-files", "--no-man-pages"))
    imageDir.set(file(layout.buildDirectory.dir("image/${project.name}-${version}")))
    imageZip.set(file(layout.buildDirectory.dir("image/${project.name}-${version}.zip")))

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

    implementation("net.dv8tion:JDA:6.1.3")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
