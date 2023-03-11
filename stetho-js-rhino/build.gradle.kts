plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = 14
        consumerProguardFiles("proguard-consumer.pro")
    }

    publishing {
        multipleVariants {
            allVariants()
        }
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = project.properties["GROUP"] as String
                artifactId = project.properties["POM_ARTIFACT_ID"] as String
                version = project.properties["VERSION_NAME"] as String

                afterEvaluate {
                    from(components["release"])
                }
            }
        }
        repositories {
            mavenLocal()
        }
    }
}

dependencies {
    implementation(project(":stetho"))
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.mozilla:rhino:1.7.6")
    implementation("androidx.annotation:annotation:1.6.0")

    testImplementation("junit:junit:4.13.2")
}

// apply from: rootProject.file("release.gradle")
