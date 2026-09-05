import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
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
