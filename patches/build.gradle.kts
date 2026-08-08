group = "app.template"

patches {
    // TODO: Update this section with your project details.
    about {
        name = "BlazeFTL Patches"
        description = "Universal Patches for removing ads,analytics,duplicate graphics"
        source = "git@github.com:BlazeFTL/ftl-patches.git"
        author = "BlazeFTL"
        contact = "https://github.com/BlazeFTL"
        website = "https://github.com/BlazeFTL/FTL-Patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
