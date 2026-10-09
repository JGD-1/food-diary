package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodesTest {
    @Test fun `real EAN-13 codes are valid`() {
        assertTrue(Barcodes.isValid("5449000000996")) // Coca-Cola 330 ml
        assertTrue(Barcodes.isValid("8710398527554"))
    }

    @Test fun `one wrong digit is caught`() = assertFalse(Barcodes.isValid("5449000000997"))

    @Test fun `EAN-8 and UPC-A`() {
        assertTrue(Barcodes.isValid("96385074"))
        assertTrue(Barcodes.isValid("036000291452"))
    }

    @Test fun `UPC-E is expanded and checked`() {
        assertEquals("042100005264", Barcodes.upcEToUpcA("04252614"))
        assertTrue(Barcodes.isValid("04252614"))
    }

    @Test fun `not a shop barcode`() {
        assertFalse(Barcodes.isValid(""))
        assertFalse(Barcodes.isValid("12345"))
        assertFalse(Barcodes.isValid("54490000009a6"))
    }
}
