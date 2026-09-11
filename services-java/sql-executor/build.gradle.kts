dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}

tasks.bootJar {
    mainClass.set("com.lpn.aibi.sqlexecutor.SqlExecutorApplication")
}
