package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleDisplayParserTest {
    private fun line(text: String, height: Float = 100f, top: Float = 0f) =
        TextLine(text, 0f, top, text.length * height * 0.6f, top + height)

    private fun parse(vararg lines: TextLine) = ScaleDisplayParser.parse(lines.toList())

    @Test fun `plain grams`() = assertEquals(ScaleParse.Grams(245.0), parse(line("245 g")))

    @Test fun `unit stuck to the number`() = assertEquals(ScaleParse.Grams(245.0), parse(line("245g")))

    @Test fun `no unit means grams`() = assertEquals(ScaleParse.Grams(87.0), parse(line("87")))

    @Test fun `unit shown separately and small`() =
        assertEquals(ScaleParse.Grams(312.0), parse(line("312", 120f), line("g", 30f, 200f)))

    @Test fun `tallest number wins over brand and model text`() =
        assertEquals(
            ScaleParse.Grams(1250.0),
            parse(line("SALTER 1066", 20f, 0f), line("1250", 120f, 50f), line("TARE ON/OFF", 15f, 200f)),
        )

    @Test fun `logo read as a digit next to the brand name is ignored`() =
        // Seen on Guido's scale: the "B" logo before "KitchenBrothers" read as a 3, in bigger letters.
        assertEquals(ScaleParse.Grams(130.0), parse(line("3 KitchenBrothers", 60f, 200f), line("130", 50f, 0f)))

    @Test fun `seven segment lookalikes are fixed`() {
        assertEquals(ScaleParse.Grams(150.0), parse(line("l5O g")))
        assertEquals(ScaleParse.Grams(258.0), parse(line("2SB")))
    }

    @Test fun `zero read as the letter O`() = assertEquals(ScaleParse.Grams(0.0), parse(line("O g")))

    @Test fun `words are not numbers`() = assertEquals(ScaleParse.Unreadable, parse(line("ON OFF TARE")))

    @Test fun `decimal grams with comma or dot`() {
        assertEquals(ScaleParse.Grams(12.5), parse(line("12,5 g")))
        assertEquals(ScaleParse.Grams(12.5), parse(line("12.5g")))
    }

    @Test fun `kilograms are turned into grams`() {
        assertEquals(ScaleParse.Grams(1250.0), parse(line("1.25 kg")))
        assertEquals(ScaleParse.Grams(1250.0), parse(line("1.250")))
    }

    @Test fun `millilitres count as grams`() = assertEquals(ScaleParse.Grams(330.0), parse(line("330 ml")))

    @Test fun `ounces ask for grams`() {
        assertEquals(ScaleParse.OtherUnit, parse(line("8.6 oz")))
        assertEquals(ScaleParse.OtherUnit, parse(line("1 lb")))
    }

    @Test fun `below zero while taring is ignored`() = assertEquals(ScaleParse.Unreadable, parse(line("-12 g")))

    @Test fun `impossible weights are ignored`() = assertEquals(ScaleParse.Unreadable, parse(line("99999")))

    @Test fun `nothing seen`() = assertEquals(ScaleParse.Unreadable, parse())
}

class StabilizerTest {
    @Test fun `needs the same value several frames in a row`() {
        val s = Stabilizer<Double>(3)
        assertEquals(null, s.offer(245.0))
        assertEquals(null, s.offer(245.0))
        assertEquals(245.0, s.offer(245.0))
    }

    @Test fun `a different value starts again`() {
        val s = Stabilizer<Double>(2)
        s.offer(245.0)
        assertEquals(null, s.offer(246.0))
        assertEquals(246.0, s.offer(246.0))
    }

    @Test fun `unreadable frames are skipped, not counted`() {
        val s = Stabilizer<Double>(2)
        s.offer(245.0)
        assertEquals(null, s.offer(null))
        assertEquals(245.0, s.offer(245.0))
    }
}
