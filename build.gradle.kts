plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.wd"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
//    version.set("2025.3")
//    type.set("IU") // Target IDE Platform
    intellij.updateSinceUntilBuild = false
    localPath.set("/Users/lww/Downloads/ideaJar/ideaIU-2025.3")
    plugins.set(
        listOf(
            /* Plugin Dependencies */
            "com.intellij.java",
            "org.jetbrains.kotlin",
            /* 注意：不在这里添加 com.intellij.database。
             * IC 版没有 Database 插件，且 DatabaseTableMetadataFetcher 使用纯反射访问，
             * 编译期无需依赖；运行时由 plugin.xml 的可选依赖声明控制。 */
        )
    )

}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.24")
    annotationProcessor("org.projectlombok:lombok:1.18.24")

    implementation("log4j:log4j:1.2.17")
    implementation("com.alibaba:fastjson:1.2.83")
    implementation("commons-io:commons-io:2.18.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    // pdf
    implementation("com.itextpdf:itextpdf:5.5.13")
    implementation("com.itextpdf:itext-asian:5.2.0")

    // Image processing optimization
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.10.1")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
