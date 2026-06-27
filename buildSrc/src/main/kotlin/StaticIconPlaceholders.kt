import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

abstract class GenerateStaticIconPlaceholdersTask : DefaultTask() {
    @get:Input
    abstract val startIndex: Property<Int>

    @get:Input
    abstract val endIndex: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }

        val first = startIndex.get()
        val last = endIndex.get()

        val img = BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(210, 210, 210)
        g.fillRect(0, 0, 192, 192)
        g.dispose()
        val placeholderBytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(img, "PNG", output)
            output.toByteArray()
        }

        for (i in first..last) {
            outDir.resolve("icon_$i.png").writeBytes(placeholderBytes)
        }
    }
}

fun Project.configureStaticIconPlaceholders(startIndex: Int, endIndex: Int) {
    val generateStaticIconPlaceholders = tasks.register<GenerateStaticIconPlaceholdersTask>("generateStaticIconPlaceholders") {
        this.startIndex.set(startIndex)
        this.endIndex.set(endIndex)
        outputDir.set(layout.projectDirectory.dir("src/main/res/drawable-nodpi-v5"))
    }

    tasks.named("preBuild").configure {
        dependsOn(generateStaticIconPlaceholders)
    }
}
