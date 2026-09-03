plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "io.github.barayo"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.barayo:testpulse-annotations:1.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    create("functionalTest") {
        java.srcDir("src/functionalTest/java")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

val functionalTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}

dependencies {
    functionalTestImplementation(gradleTestKit())
    functionalTestImplementation(platform("org.junit:junit-bom:5.10.3"))
    functionalTestImplementation("org.junit.jupiter:junit-jupiter")
    functionalTestImplementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val functionalTest by tasks.registering(Test::class) {
    description = "Runs Gradle TestKit integration tests against real, isolated builds."
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    useJUnitPlatform()
    dependsOn(tasks.named("pluginUnderTestMetadata"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTest)
}

gradlePlugin {
    website.set("https://github.com/Barayo/testpulse-gradle-plugin")
    vcsUrl.set("https://github.com/Barayo/testpulse-gradle-plugin")
    plugins {
        create("testpulse") {
            id = "io.github.barayo.testpulse"
            implementationClass = "io.github.barayo.testpulse.gradle.TestPulsePlugin"
            displayName = "TestPulse import plugin"
            description = "Reports Gradle test results into TestPulse via the execution-import API."
            tags.set(listOf("testpulse", "testing", "junit", "test-management"))
        }
    }
    testSourceSets(sourceSets["test"], sourceSets["functionalTest"])
}
