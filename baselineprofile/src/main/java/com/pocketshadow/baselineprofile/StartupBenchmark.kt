package com.pocketshadow.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.pocketshadow.app"

/**
 * Before/after measurement for the baseline profile. Compare the
 * timeToInitialDisplay of the two tests:
 *
 *   startupCompilationNone            → cold start WITHOUT the profile (worst case)
 *   startupCompilationBaselineProfile → cold start WITH the profile applied
 *
 * Run with:  gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * (Physical device recommended for stable numbers; emulators warn but work.)
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfile() =
        startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName     = PACKAGE_NAME,
        metrics         = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode     = StartupMode.COLD,
        iterations      = 5
    ) {
        pressHome()
        startActivityAndWait()
    }
}
