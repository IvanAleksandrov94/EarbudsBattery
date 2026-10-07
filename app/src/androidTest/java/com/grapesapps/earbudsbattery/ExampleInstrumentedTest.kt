package com.grapesapps.earbudsbattery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Example instrumented test, which runs on an Android device. */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.grapesapps.earbudsbattery.dev", appContext.packageName)
    }
}
