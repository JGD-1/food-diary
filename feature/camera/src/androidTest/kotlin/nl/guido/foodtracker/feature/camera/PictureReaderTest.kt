package nl.guido.foodtracker.feature.camera

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nl.guido.foodtracker.feature.camera.read.PictureMemory
import nl.guido.foodtracker.feature.camera.vision.PictureReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The picture models load from the app's assets and read one of Guido's scale photos. */
@RunWith(AndroidJUnit4::class)
class PictureReaderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun modelsLoadAndTheSamePictureIsRecognisedAgain() {
        val photos = instrumentation.context.assets
        val name = photos.list("scale-aimed")!!.sorted().first()
        val bitmap = photos.open("scale-aimed/$name").use(BitmapFactory::decodeStream)
        PictureReader(instrumentation.targetContext).use { reader ->
            val picture = reader.read(bitmap)
            assertEquals(1000, picture.scores.size)
            assertEquals(1024, picture.vector.size)
            assertTrue("scores add up to about 1", picture.scores.sum() in 0.8f..1.2f)
            val turned = reader.read(bitmap, rotationDegrees = 90)
            val memory = PictureMemory().learn("scale", picture.vector)
            assertEquals(listOf("scale"), memory.nearest(reader.read(bitmap).vector))
            assertTrue(turned.vector.size == 1024)
        }
    }
}
