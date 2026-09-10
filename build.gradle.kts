plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.3.20"
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.fancia.backend"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

fun RepositoryHandler.codeArtifactRepo(repoName: String) {
    maven {
        val baseUrl = System.getenv("ARTIFACT_REPO_URL") ?: project.findProperty("ARTIFACT_REPO_URL") as String?
        ?: error("ARTIFACT_REPO_URL must be provided via environment variable or project property")
        url = uri("$baseUrl/$repoName/")
        credentials {
            username = System.getenv("ARTIFACT_REPO_USER") ?: project.findProperty("ARTIFACT_REPO_USER") as String?
                    ?: error("ARTIFACT_REPO_USER must be provided")
            password =
                System.getenv("ARTIFACT_REPO_PASSWORD") ?: project.findProperty("ARTIFACT_REPO_PASSWORD") as String?
                        ?: error("ARTIFACT_REPO_PASSWORD must be provided")
        }
        content {
            includeGroupByRegex("com\\.fancia\\.backend(\\..+)?")
        }
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
    codeArtifactRepo("fancia-backend-shared-common")
    codeArtifactRepo("fancia-backend-shared-user")
    codeArtifactRepo("fancia-backend-shared-payment")
}
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.data:spring-data-commons")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.postgresql:postgresql")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("software.amazon.awssdk:sts:2.42.4")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager:4.0.0")
    implementation("com.amazonaws.secretsmanager:aws-secretsmanager-jdbc:2.0.4")
    implementation("com.fancia.backend.shared:common:0.0.1-SNAPSHOT")
    implementation("com.fancia.backend.shared:user:0.0.1-SNAPSHOT")
    implementation("com.fancia.backend.shared:payment:0.0.1-SNAPSHOT")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.1")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
    implementation("org.springdoc:springdoc-openapi-starter-common:3.0.1")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.kafka:spring-kafka:4.0.3")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-client-all:5.0.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.4")
    implementation("org.springframework.boot:spring-boot-starter-flyway:4.0.5")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    implementation("com.google.apis:google-api-services-androidpublisher:v3-rev20260528-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.30.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")
    implementation("com.stripe:stripe-java:33.1.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.7")
    testImplementation("io.kotest:kotest-assertions-core:6.1.7")
    testImplementation("io.kotest:kotest-extensions-spring:6.1.7")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.wiremock:wiremock:3.13.2")
    testImplementation("org.wiremock.integrations.testcontainers:wiremock-testcontainers-module:1.0-alpha-13")
    testImplementation("org.testcontainers:testcontainers-kafka:2.0.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    enabled = false
}

tasks.register<Zip>("lambdaZip") {
    group = "build"
    description = "AWS Lambda Zip package for Lambda Web Adapter (run.sh + boot JAR)"
    archiveFileName.set("${project.name}-lambda.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isZip64 = true

    dependsOn(tasks.bootJar)

    from(tasks.bootJar) {
        rename { "app.jar" }
    }
    from("src/lambda") {
        include("run.sh")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    environment(System.getenv())
}
