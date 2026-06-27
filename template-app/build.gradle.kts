import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ── Placeholder PNG generation ────────────────────────────────────────────────
// Creates all slot images (icon_N, calendar_N_M, clock_N_layer) before aapt2
// touches the res/ folder. The actual artwork is replaced by ApkGenerator.
abstract class GeneratePlaceholdersTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val drawableDir: DirectoryProperty

    @get:OutputFile
    abstract val drawableXmlFile: RegularFileProperty

    @get:OutputFile
    abstract val sentinelFile: RegularFileProperty

    @TaskAction
    fun run() {
        val outDir = outputDir.get().asFile
        val drawDir = drawableDir.get().asFile
        val xmlFile = drawableXmlFile.get().asFile
        val sentFile = sentinelFile.get().asFile

        outDir.mkdirs()
        drawDir.mkdirs()
        xmlFile.parentFile.mkdirs()

        // Clean old placeholders so regeneration always produces the full set
        outDir.listFiles()?.forEach { it.delete() }
        drawDir.listFiles { _, name -> name.startsWith("clock_dynamic_") || name.startsWith("clock_") }?.forEach { it.delete() }

        val img = BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(210, 210, 210)
        g.fillRect(0, 0, 192, 192)
        g.dispose()
        val placeholderBytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(img, "PNG", output)
            output.toByteArray()
        }

        // 30000 static icon slots
        val totalStaticSlots = 30000
        for (i in 1..totalStaticSlots) {
            val f = outDir.resolve("icon_$i.png")
            f.parentFile.mkdirs()
            if (!f.exists()) f.writeBytes(placeholderBytes)
        }

        // 64×31 dynamic calendar slots
        val totalCalendarSlots = 64
        for (slot in 1..totalCalendarSlots) {
            for (day in 1..31) {
                val f = outDir.resolve("calendar_${slot}_$day.png")
                f.parentFile.mkdirs()
                if (!f.exists()) f.writeBytes(placeholderBytes)
            }
        }

        // 64×4 dynamic clock slots
        val totalClockSlots = 64
        for (slot in 1..totalClockSlots) {
            for (layer in listOf("bg", "hour", "minute", "second")) {
                val f = outDir.resolve("clock_${slot}_$layer.png")
                f.parentFile.mkdirs()
                if (!f.exists()) f.writeBytes(placeholderBytes)
            }
            // Generate clock_dynamic_X.xml
            val clockXml = drawDir.resolve("clock_dynamic_$slot.xml")
            if (!clockXml.exists()) {
                clockXml.writeText("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                        <item android:drawable="@drawable/clock_${slot}_bg" />
                        <item>
                            <rotate android:drawable="@drawable/clock_${slot}_hour" android:fromDegrees="0" android:toDegrees="360" android:pivotX="50%" android:pivotY="50%" />
                        </item>
                        <item>
                            <rotate android:drawable="@drawable/clock_${slot}_minute" android:fromDegrees="0" android:toDegrees="360" android:pivotX="50%" android:pivotY="50%" />
                        </item>
                        <item>
                            <rotate android:drawable="@drawable/clock_${slot}_second" android:fromDegrees="0" android:toDegrees="360" android:pivotX="50%" android:pivotY="50%" />
                        </item>
                    </layer-list>
                """.trimIndent())
            }
        }

        // Generate drawable.xml
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<resources>\n")
        sb.append("    <category title=\"All\" />\n")
        for (i in 1..totalStaticSlots) {
            sb.append("    <item drawable=\"icon_$i\" />\n")
        }
        for (slot in 1..totalCalendarSlots) {
            sb.append("    <item drawable=\"calendar_${slot}_1\" />\n")
        }
        for (slot in 1..totalClockSlots) {
            sb.append("    <item drawable=\"clock_dynamic_$slot\" />\n")
        }
        sb.append("</resources>")
        xmlFile.writeText(sb.toString())

        sentFile.writeText("generated")
    }
}

val generatePlaceholders by tasks.registering(GeneratePlaceholdersTask::class) {
    outputDir.set(layout.projectDirectory.dir("src/main/res/drawable-nodpi-v5"))
    drawableDir.set(layout.projectDirectory.dir("src/main/res/drawable"))
    drawableXmlFile.set(layout.projectDirectory.file("src/main/res/xml/drawable.xml"))
    sentinelFile.set(layout.projectDirectory.file("src/main/res/drawable-nodpi-v5/.generated"))
}

// ── Copy the pixel font from the main app ─────────────────────────────────────
val copyFont by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/main/res/font"))
    into("src/main/res/font")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

android {
    namespace = "com.kian.perficontemplate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kian.perficontemplate"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with debug key so ApkGenerator can strip+re-sign without a release keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    afterEvaluate {
        tasks.named("preBuild").configure {
            dependsOn(generatePlaceholders, copyFont)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
