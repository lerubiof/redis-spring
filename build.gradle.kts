plugins {
    java
    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.flywaydb.flyway") version "10.8.1"
    kotlin("jvm")
}

group = "com.luisrubio"
version = "0.0.1-SNAPSHOT"

java {
}

repositories {
    mavenCentral()
}



dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("redis.clients:jedis:5.1.0")
    implementation("org.springframework.data:spring-data-redis:3.2.2")
    implementation("org.flywaydb:flyway-core:10.8.1")
    implementation("org.flywaydb:flyway-mysql:10.8.1")
    implementation(kotlin("stdlib-jdk8"))


}

tasks.withType<Test> {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

flyway {
    url = "jdbc:mysql://mysql/pruebas"
    user = "root"
    password = "1234567890"
    locations = arrayOf("classpath:db/")
}