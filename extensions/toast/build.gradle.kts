import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
}

configure<ApplicationExtension> {
    defaultConfig {
        minSdk = 26
    }
}
