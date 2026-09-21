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
//    version.set("2023.2.6")
//    type.set("IU") // Target IDE Platform
    intellij.updateSinceUntilBuild = false
//    localPath.set("/Users/lww/Downloads/ideaJar/ideaIU-2023.2.6")
//    localPath.set("E:\\ideac\\ideaIU-2023.2.6")
    localPath.set("/Applications/IntelliJ IDEA.app/Contents")
    plugins.set(
        listOf(
            /* Plugin Dependencies */
            "com.intellij.java",
            "org.jetbrains.kotlin",
            /* Database 插件（Ultimate 版独有） */
            "com.intellij.database",
        )
    )

}

dependencies {
    implementation("com.alibaba:fastjson:1.2.83")

    // pdf
    implementation("com.itextpdf:itextpdf:5.5.13")
    implementation("com.itextpdf:itext-asian:5.2.0")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
//        sinceBuild.set("223")
//        untilBuild.set("242.*")
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
