package com.hossa.redoble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas que corren en GitHub Actions antes de compilar el APK. */
class WeatherTest {
    @Test fun describeCodes() {
        assertEquals("Despejado", WeatherWorker.describe(0))
        assertEquals("Lluvia", WeatherWorker.describe(63))
        assertEquals("Tormenta", WeatherWorker.describe(95))
        assertEquals("Variable", WeatherWorker.describe(999))
    }

    @Test fun rockLinesNeverEmpty() {
        for (code in listOf(0, 1, 3, 45, 51, 61, 71, 80, 95, -1)) for (t in listOf(-5, 15, 30)) {
            assertTrue(WeatherWorker.rockLine(code, t, false).isNotBlank())
        }
        assertEquals("Sol: sube el volumen", WeatherWorker.rockLine(0, 30, false))
        assertEquals("Sol: sube el volumen", WeatherWorker.rockLine(0, 86, true))
    }
}
