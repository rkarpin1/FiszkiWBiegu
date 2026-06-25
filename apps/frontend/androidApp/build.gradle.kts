import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifact
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        freeCompilerArgs.addAll("-opt-in=androidx.media3.common.util.UnstableApi")
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.multiplatform.settings.no.arg)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "pl.rkarpinski.fiszkiwbiegu"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.rkarpinski.fiszkiwbiegu"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 4
        versionName = "1.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Zmień nazwę wynikowego APK na FiszkiWBiegu.apk na poziomie artefaktu AGP
// (SingleArtifact.APK). Dzięki temu właściwą nazwę dostają wszyscy konsumenci:
// `./gradlew assembleRelease` ORAZ kreator "Generate Signed Bundle / APK"
// w Android Studio (który robi własny build z pominięciem zadania assemble).
@DisableCachingByDefault(because = "Tylko kopiuje/zmienia nazwę APK")
abstract class RenameApkTask : DefaultTask() {
    @get:InputFiles
    abstract val apkFolder: DirectoryProperty

    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<RenameApkTask>>

    @TaskAction
    fun taskAction() {
        transformationRequest.get().submit(this) { builtArtifact: BuiltArtifact ->
            val output = File(outFolder.get().asFile, "FiszkiWBiegu.apk")
            File(builtArtifact.outputFile).copyTo(output, overwrite = true)
            output
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val taskProvider = tasks.register<RenameApkTask>(
            "rename${variant.name.replaceFirstChar { it.uppercase() }}Apk",
        )
        val request = variant.artifacts.use(taskProvider)
            .wiredWithDirectories(RenameApkTask::apkFolder, RenameApkTask::outFolder)
            .toTransformMany(SingleArtifact.APK)
        taskProvider.configure { transformationRequest.set(request) }
    }
}