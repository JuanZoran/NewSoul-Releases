plugins { id("com.android.application") }
// The published AAR declares minCompileSdk=37. This demo only uses API-26+
// Android types, so extract its unchanged classes.jar for compile-only use
// with the installed SDK 35. The runtime implementation stays in LSPosed.
val xposedApi by configurations.creating { isTransitive = false }
val extractXposedApi by tasks.registering(Sync::class) {
    from({ zipTree(xposedApi.singleFile) }) { include("classes.jar") }
    into(layout.buildDirectory.dir("xposed-api"))
}
// Use the same verified service/interface jars as the SDK build. Their AARs
// declare API 37 metadata; this module uses the API 26+ classes only.
val prepareHomeDeps by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/prepare-home-deps.py")
    outputs.files(rootProject.file("build/deps/xposed-service.jar"),
        rootProject.file("build/deps/xposed-interface.jar"))
}
android {
    namespace = "dev.local.newsoul"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.juanzoran.newsoul"
        minSdk = 26
        targetSdk = 35
        versionCode = 333
        versionName = "1.50.0"
    }
    packaging { jniLibs.useLegacyPackaging = false }
    defaultConfig.manifestPlaceholders["moduleLabel"] = "New Soul"
    signingConfigs {
        create("release") {
            val keystore = providers.environmentVariable("NEWSOUL_SIGNING_KEYSTORE")
                .orElse("${System.getProperty("user.home")}/.local/share/newsoul/signing/new-soul-release.p12")
            val passwordFile = providers.environmentVariable("NEWSOUL_SIGNING_PASSWORD_FILE")
                .orElse("${System.getProperty("user.home")}/.config/newsoul/signing.pass")
            val secretFile = file(passwordFile.get())
            storeFile = file(keystore.get())
            // Keep Debug builds usable on machines without release credentials.
            storePassword = if (secretFile.isFile) secretFile.readText().trim() else ""
            keyAlias = providers.environmentVariable("NEWSOUL_SIGNING_ALIAS").orElse("newsoul-release").get()
            keyPassword = storePassword
            storeType = "PKCS12"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Both build paths stage the same unified source tree.
    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(rootProject.file("build/protection-gradle/public/java")))
        resources.setSrcDirs(listOf(rootProject.file("build/protection-gradle/public/resources")))
    }
}
dependencies {
    implementation(files(rootProject.file("build/deps/xposed-service.jar"),
        rootProject.file("build/deps/xposed-interface.jar")).builtBy(prepareHomeDeps))
    implementation("org.luckypray:dexkit:2.0.7")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    xposedApi("io.github.libxposed:api:102.0.0@aar")
    compileOnly(files(layout.buildDirectory.file("xposed-api/classes.jar"))
        .builtBy(extractXposedApi))
}

val checkDonationData by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/generate-donation-data.py", "--check")
}
tasks.named("preBuild") { dependsOn(checkDonationData) }
val prepareProtection by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/prepare-protected-build.py", "public", "build/protection-gradle/public")
}
tasks.named("preBuild") { dependsOn(prepareProtection) }
val verifyProtection by tasks.registering(Exec::class) {
    dependsOn("packageRelease")
    workingDir(rootProject.projectDir)
    commandLine("python3", "tests/check-protected-build.py", "build/protection-gradle/public",
        "app/build/outputs/apk/release/app-release.apk", "--mapping", "app/build/outputs/mapping/release/mapping.txt")
}
tasks.matching { it.name == "assembleRelease" }.configureEach { dependsOn(verifyProtection) }
val publicReleaseApk by tasks.registering(Copy::class) {
    dependsOn(verifyProtection)
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release.apk")
        rename { "new-soul-${android.defaultConfig.versionName}.apk" }
    }
    into(layout.buildDirectory.dir("outputs/releases/public"))
}
val publicReleaseZip by tasks.registering(Zip::class) {
    dependsOn(publicReleaseApk)
    from(layout.buildDirectory.file("outputs/releases/public/new-soul-${android.defaultConfig.versionName}.apk"))
    archiveFileName.set("new-soul-${android.defaultConfig.versionName}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/releases/public"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(publicReleaseZip) }
