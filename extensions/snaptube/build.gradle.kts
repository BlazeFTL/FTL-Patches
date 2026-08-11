import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly("androidx.preference:preference:1.2.1")
}

configure<ApplicationExtension> {
    defaultConfig {
        minSdk = 26
    }
}
