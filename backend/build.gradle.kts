plugins {
	java
	groovy   // compiles src/test/groovy — Spock specs run on the JUnit Platform alongside the JUnit tests
	kotlin("jvm") version "2.2.20"            // mixed Java + Kotlin compilation in one module
	kotlin("plugin.spring") version "2.2.20"  // all-open: @Component/@Service Kotlin classes become open for proxying
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.tcgtracker"
version = "0.0.1-SNAPSHOT"

// Align the Spring Boot BOM's managed Kotlin stdlib/reflect version with the Kotlin plugin above.
extra["kotlin.version"] = "2.2.20"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(23)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// Push Micrometer metrics to InfluxDB (line protocol). Version managed by the Boot BOM.
	implementation("io.micrometer:micrometer-registry-influx")
	// Kotlin: Jackson (de)serializes the Kotlin event data classes idiomatically. Version BOM-managed.
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.springframework.boot:spring-boot-starter-data-cassandra")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-cassandra-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// Spock 2.4 (stable, 2025-12) on the Groovy 4.0 line. spock-spring wires specs into the
	// Spring TestContext so @WebMvcTest slices + @SpringBean work. Groovy is pinned explicitly so
	// the `groovy` plugin compiles against the same 4.0 line Spock expects.
	testImplementation("org.spockframework:spock-core:2.4-groovy-4.0")
	testImplementation("org.spockframework:spock-spring:2.4-groovy-4.0")
	testImplementation("org.apache.groovy:groovy:4.0.24")
	// Kotlin test (JUnit5 + kotlin.test assertions) — coexists with Spock + JUnit on the platform.
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.20")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Compile Kotlin against the same JDK 23 toolchain as Java (sets jvmTarget = 23).
kotlin {
	jvmToolchain(23)
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Only build the executable boot jar (skip the extra -plain.jar) so the
// Docker runtime stage can COPY a single, unambiguous artifact.
tasks.named("jar") {
	enabled = false
}
