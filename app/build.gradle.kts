import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Chiavi opzionali lette da local.properties (mai committato). Vedi local.properties.example.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val openRouterKey: String = localProps.getProperty("OPENROUTER_API_KEY", "").trim()

// Versione: unica fonte in gradle.properties (appVersion), incrementata da release.ps1.
// versionCode deve crescere a ogni release, altrimenti Android rifiuta l'aggiornamento.
val appVersion: String = providers.gradleProperty("appVersion").get()
val appVersionCode: Int = appVersion.split(".").mapNotNull { it.toIntOrNull() }.let { parts ->
    require(parts.size == 3 && parts[1] < 100 && parts[2] < 100) {
        "appVersion=$appVersion in gradle.properties: serve X.Y.Z con Y e Z minori di 100"
    }
    parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

// Firma: tutti gli APK, debug e release, di chiunque li compili, sono firmati con lo stesso keystore,
// indicato in keystore.properties (ignorato da git, modello in keystore.properties.example).
// Android accetta un aggiornamento solo con la firma dell'app installata, e l'accesso con Google
// riconosce l'app dallo SHA-1 registrato su Firebase: con un altro keystore non funzionerebbe nessuno dei due.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystoreFile: File? = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it) }

android {
    namespace = "com.example.animewatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.animewatch"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion

        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterKey\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"${localProps.getProperty("OPENROUTER_MODEL", "openai/gpt-4o-mini")}\"")
        buildConfigField("String", "UPDATE_REPO", "\"${providers.gradleProperty("appReleaseRepo").get()}\"")
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("shared") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}

// ---- Controlli prima della build ----

fun fingerprint(algorithm: String, bytes: ByteArray, separator: String = ""): String =
    MessageDigest.getInstance(algorithm).digest(bytes).joinToString(separator) { "%02X".format(it) }

val expectedSigningSha1: String = providers.gradleProperty("appSigningSha1").get()
val expectedOpenRouterSha256: String = providers.gradleProperty("openRouterKeySha256").getOrElse("").trim()
val googleServicesFile: File = file("google-services.json")
val runningInCi = System.getenv("CI") == "true"

// Ogni build (debug compresa) si ferma se il keystore non è quello delle versioni pubblicate: firmata con la
// chiave di debug della macchina l'app non potrebbe accedere con Google né aggiornarsi. Fa eccezione la CI,
// che compila senza segreti e non distribuisce nulla. La sincronizzazione di Android Studio non esegue preBuild.
val verifySigningKey by tasks.registering {
    group = "verification"
    description = "Controlla che keystore.properties indichi il keystore delle versioni pubblicate."
    doLast {
        if (keystoreFile == null) {
            if (runningInCi) {
                logger.warn("keystore.properties assente: in CI l'APK è firmato con la chiave di debug della macchina.")
                return@doLast
            }
            throw GradleException(
                "Manca keystore.properties nella radice del progetto: crealo da keystore.properties.example " +
                    "indicando il keystore condiviso di AnimeWatch (SHA-1 $expectedSigningSha1)."
            )
        }
        if (!keystoreFile.isFile) throw GradleException("Keystore non trovato: $keystoreFile (storeFile in keystore.properties).")
        val alias = keystoreProps.getProperty("keyAlias")
        val cert = try {
            KeyStore.getInstance(keystoreFile, keystoreProps.getProperty("storePassword").orEmpty().toCharArray())
                .getCertificate(alias)
        } catch (e: Exception) {
            throw GradleException("Impossibile leggere $keystoreFile (storePassword sbagliata?): ${e.message}")
        } ?: throw GradleException("Il keystore $keystoreFile non contiene la chiave '$alias' (keyAlias in keystore.properties).")
        val sha1 = fingerprint("SHA-1", cert.encoded, ":")
        if (!sha1.equals(expectedSigningSha1, ignoreCase = true)) {
            throw GradleException(
                "Il keystore $keystoreFile ha SHA-1 $sha1, atteso $expectedSigningSha1 (appSigningSha1 in gradle.properties): " +
                    "usa il keystore condiviso di AnimeWatch, non quello di debug di questo PC."
            )
        }
    }
}

// Una release si pubblica a tutti: deve avere il vero google-services.json e la chiave OpenRouter unica
// (ne è fissata solo l'impronta, la chiave resta in local.properties).
val verifyReleaseConfig by tasks.registering {
    group = "verification"
    description = "Controlla keystore, google-services.json e chiave OpenRouter prima di una release."
    dependsOn(verifySigningKey)
    doLast {
        if (keystoreFile == null) throw GradleException("Una release va firmata con il keystore condiviso: manca keystore.properties.")
        val services = googleServicesFile.takeIf { it.isFile }?.readText()
            ?: throw GradleException("Manca app/google-services.json: scaricalo dalla console Firebase (progetto di AnimeWatch).")
        if ("EXAMPLE" in services || Regex(""""oauth_client"\s*:\s*\[\s*]""").containsMatchIn(services)) {
            throw GradleException(
                "app/google-services.json è il segnaposto o non ha il client OAuth: scarica quello vero dalla console Firebase."
            )
        }
        if (openRouterKey.isEmpty()) {
            throw GradleException("Manca OPENROUTER_API_KEY in local.properties: le release includono la chiave OpenRouter condivisa.")
        }
        val keySha256 = fingerprint("SHA-256", openRouterKey.toByteArray()).lowercase()
        if (expectedOpenRouterSha256.isEmpty()) {
            throw GradleException(
                "openRouterKeySha256 è vuota in gradle.properties. Se la chiave in local.properties è quella giusta, " +
                    "fissala impostando openRouterKeySha256=$keySha256 (in un commit su dev)."
            )
        }
        if (keySha256 != expectedOpenRouterSha256.lowercase()) {
            throw GradleException(
                "La chiave OpenRouter in local.properties non è quella delle release (impronta $keySha256, " +
                    "attesa $expectedOpenRouterSha256): chiedi la chiave condivisa."
            )
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifySigningKey) }
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfig) }
