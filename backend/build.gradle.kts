import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

group = "com.hmgchallenge"
version = "0.0.1"

springBoot {
    mainClass.set("com.hmgchallenge.backend.BackendApplicationKt")
}

// 설치된 JDK 25로 컴파일하고 바이트코드는 LTS인 21로 낮춘다.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    // io.spring.dependency-management 대신 Gradle 네이티브 BOM 사용
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Official standard-data exports stay outside Git; only normalized facility fields are retained.
tasks.register<JavaExec>("importFacilities") {
    group = "application"
    description = "Normalize official rest-area and drowsiness-shelter JSON exports"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.hmgchallenge.backend.facility.FacilityImport")
    args = listOf("facilityRestFile", "facilityShelterFile", "facilityOutput").map { key ->
        providers.gradleProperty(key).getOrElse("")
    } + listOfNotNull(providers.gradleProperty("facilityFetchedAtEpochMillis").orNull)
}
