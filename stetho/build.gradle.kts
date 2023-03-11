import java.util.*

plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = 14
        consumerProguardFiles("proguard-consumer.pro")
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
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
    implementation("commons-cli:commons-cli:1.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("androidx.annotation:annotation:1.6.0")

    // Optional: reflection is used to test whether Fragment (and the transient AndroidX Core) are actually present.
    implementation("androidx.appcompat:appcompat:1.6.1") // optional

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.2.1") {
        exclude(module = "commons-logging")
        exclude(module = "httpclient")
    }
    testImplementation("org.powermock:powermock-api-mockito:1.6.6")
    testImplementation("org.powermock:powermock-module-junit4:1.6.6")

    // https://stackoverflow.com/a/75298544
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0") {
            because("kotlin-stdlib-jdk7 is now a part of kotlin-stdlib")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0") {
            because("kotlin-stdlib-jdk8 is now a part of kotlin-stdlib")
        }
    }
}

// apply from: rootProject.file("release.gradle")

android.libraryVariants.forEach { variant ->
    val name = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    // Ugly kludge to rename license files in the bundled commons-cli
    // dependency so that they do not appear to describe Stetho"s license.
    task<Copy>("tidyCommonsCli$name") {
        from(
            variant.javaCompileProvider.get().classpath.filter {
                it.getName() == "commons-cli-1.2.jar"
            }.map {
                zipTree(it)
            }
        )
        into("build/commons-cli-tidy-$name")
        rename("LICENSE", "commons-cli-LICENSE")
        rename("NOTICE", "commons-cli-NOTICE")
    }

    task<Copy>("metainf${name}") {
        from(rootProject.file("LICENSE"))
        into("build/metainf-${name}/META-INF")
    }

    task<Jar>("fatjar${name}") {
        dependsOn("jar${name}", "tidyCommonsCli${name}", "metainf${name}")
        // classifier = "fatjar"
        from(variant.javaCompileProvider.get().destinationDirectory)
        from("build/commons-cli-tidy-${name}")
        from("build/metainf-${name}")
        exclude("android/support/**/*")
    }

    task<Jar>("jar${name}") {
        dependsOn(variant.javaCompileProvider.get())
        from(variant.javaCompileProvider.get().destinationDirectory)
    }

    println(variant.javaCompileProvider.get())
}
