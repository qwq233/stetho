buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
    }
    extra.apply {
        set("compileSdkVersion", 33)
        set("targetSdkVersion", 33)
        set("aaa", 11)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}
