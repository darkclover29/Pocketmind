package com.pocketshadow.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.pocketshadow.app"

/**
 * Walks the critical user journey — cold start, first composition of the chat
 * screen, scrolling — while ART records every executed method. The recorded
 * list becomes baseline-prof.txt, which Play compiles ahead-of-time at install
 * so first launch skips the JIT warm-up.
 *
 * Run with:  gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        // Also emit a startup profile: these classes/methods get extra
        // dex-layout optimization for cold start.
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // Let the splash hand off and the chat screen (empty state) compose.
        // The LLM engine warm-up runs in the background — waiting here also
        // captures the model-scan and status-banner code paths.
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 10_000)
        device.waitForIdle()
        Thread.sleep(2_000)

        // Scroll the empty-state content up/down — compiles the scroll +
        // recomposition paths users hit in their first seconds.
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(2) {
            device.swipe(w / 2, (h * 0.7).toInt(), w / 2, (h * 0.3).toInt(), 15)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(w / 2, (h * 0.3).toInt(), w / 2, (h * 0.7).toInt(), 15)
            device.waitForIdle()
        }
    }
}
