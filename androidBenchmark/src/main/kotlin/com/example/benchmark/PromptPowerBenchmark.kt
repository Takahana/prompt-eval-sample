package com.example.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.PowerCategory
import androidx.benchmark.macro.PowerCategoryDisplayLevel
import androidx.benchmark.macro.PowerMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring ML Kit GenAI (Gemini Nano) energy consumption per prompt.
 *
 * Requirements:
 *  - Pixel 6+ device (ODPM support). Verified on Pixel 9.
 *  - Battery >= 20%. USB may remain connected; the benchmark simulates unplug via dumpsys.
 *  - Gemini Nano model already downloaded (launch PromptTest app once manually to trigger download).
 *
 * Run: ./gradlew :androidBenchmark:connectedBenchmarkAndroidTest
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PromptPowerBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun promptJ() = measurePrompt("prompt_J")

    @Test fun promptK() = measurePrompt("prompt_K")

    @Test fun promptL() = measurePrompt("prompt_L")

    private fun measurePrompt(promptId: String) {
        val categories = PowerCategory.values()
            .associateWith { PowerCategoryDisplayLevel.BREAKDOWN }

        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(PowerMetric(PowerMetric.Type.Energy(categories))),
            iterations = ITERATIONS,
            setupBlock = {
                killProcess()
            },
        ) {
            // Launch via shell so we can pass custom extras without startActivityAndWait's frame wait.
            device.executeShellCommand(
                "am start -n $TARGET_PACKAGE/.MainActivity " +
                    "--es action batch_run --es prompts $promptId",
            )

            // BatchRunScreen renders "BATCH_DONE N/M" text when runBatch finishes.
            val done = device.wait(
                Until.hasObject(By.textStartsWith(BATCH_DONE_MARKER)),
                BATCH_TIMEOUT_MS,
            )
            check(done) { "Batch run timed out after ${BATCH_TIMEOUT_MS}ms for $promptId" }
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.example.prompttest"
        private const val BATCH_DONE_MARKER = "BATCH_DONE"
        private const val BATCH_TIMEOUT_MS = 15 * 60 * 1000L
        private const val ITERATIONS = 2

        private var originalScreenTimeoutMs: String = "60000"

        @JvmStatic
        @BeforeClass
        fun setUpDevice() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            originalScreenTimeoutMs = device.executeShellCommand(
                "settings get system screen_off_timeout",
            ).trim().ifEmpty { "60000" }

            // Prevent screen from sleeping during long batch runs.
            device.executeShellCommand("settings put system screen_off_timeout 2147483647")
            device.executeShellCommand("svc power stayon true")

            // Simulate unplug for accurate ODPM readings (USB can remain attached for adb).
            device.executeShellCommand("dumpsys battery unplug")

            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
        }

        @JvmStatic
        @AfterClass
        fun tearDownDevice() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.executeShellCommand("settings put system screen_off_timeout $originalScreenTimeoutMs")
            device.executeShellCommand("svc power stayon false")
            device.executeShellCommand("dumpsys battery reset")
        }
    }
}
