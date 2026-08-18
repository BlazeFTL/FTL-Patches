import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(libs.morphe.extensions.library)
}

configure<ApplicationExtension> {
    defaultConfig {
        minSdk = 26
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = true
            proguardFiles("../proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles("../proguard-rules.pro")
        }
    }

}
