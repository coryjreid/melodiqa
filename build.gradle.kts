plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.coryjreid.melodiqa"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.coryjreid.melodiqa.Melodiqa")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")

    implementation("ch.qos.logback:logback-core:1.5.18")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation(platform("org.slf4j:slf4j-bom:2.0.17"))

    implementation("net.dv8tion:JDA:5.2.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("melodiqa")
    archiveClassifier.set("all")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.register<Copy>("prepareJvmDist") {
    group = "distribution"
    description = "Prepares a JVM-based distribution with run scripts"

    dependsOn("shadowJar")

    val distDir = layout.buildDirectory.dir("jvm-dist").get().asFile
    into(distDir)

    // Copy fat JAR
    from("build/libs/melodiqa-all.jar")

    // Copy additional resources like logback.xml
    from("src/main/resources") {
        include("logback.xml")
    }

    // Copy run scripts from the project root
    from(project.layout.projectDirectory) {
        include("run.bat", "run.sh")
    }
}

tasks.register<Zip>("packageJvmDist") {
    dependsOn("prepareJvmDist")
    group = "distribution"
    description = "Packages JVM distribution as a ZIP"

    from(layout.buildDirectory.dir("jvm-dist"))
    archiveFileName.set("melodiqa.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions").get().asFile)
}
