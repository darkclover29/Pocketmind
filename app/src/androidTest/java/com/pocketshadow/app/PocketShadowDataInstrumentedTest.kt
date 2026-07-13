package com.pocketshadow.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pocketshadow.app.data.db.PocketShadowDatabase
import com.pocketshadow.app.data.repository.ChatHistoryRepository
import com.pocketshadow.app.data.repository.SettingsRepository
import com.pocketshadow.app.ui.theme.ThemeMode
import com.pocketshadow.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PocketShadowDataInstrumentedTest {

    private lateinit var database: PocketShadowDatabase
    private lateinit var repository: ChatHistoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PocketShadowDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ChatHistoryRepository(database.chatDao(), database.inputHistoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun chatPersistence_roundTripsMessages() = runBlocking {
        val session = repository.createSession()

        repository.saveCompleteInteraction(session.sessionId, "Remember this", "Saved locally")

        val messages = repository.loadSessionMessages(session.sessionId)
        assertEquals(2, messages.size)
        assertEquals("Remember this", messages[0].content)
        assertEquals("Saved locally", messages[1].content)
    }

    @Test
    fun encryptedExportImport_restoresChatsAndRejectsWrongPassword() = runBlocking {
        val session = repository.createSession()
        repository.saveCompleteInteraction(session.sessionId, "Export me", "Imported safely")
        val archive = repository.exportEncryptedArchive("correct horse battery")

        repository.deleteSession(session.sessionId)
        assertEquals(0, database.chatDao().getAllSessionsOnce().size)

        assertEquals(1, repository.importEncryptedArchive(archive, "correct horse battery"))
        assertEquals(2, repository.loadSessionMessages(session.sessionId).size)

        var failed = false
        try {
            repository.importEncryptedArchive(archive, "wrong password")
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun settingsAndOnboarding_persistUserChoices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pocketshadow_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val settings = SettingsRepository(context)
        assertFalse(settings.hasCompletedOnboarding.value)
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setOnboardingCompleted()

        val reloaded = SettingsRepository(context)
        assertEquals(ThemeMode.LIGHT, reloaded.themeMode.value)
        assertTrue(reloaded.hasCompletedOnboarding.value)
        assertTrue(MainViewModel(reloaded).hasCompletedOnboarding.value)
    }

    @Test
    fun invalidModelHeader_isRejectedBeforeNativeLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        val repositoryClass = Class.forName("com.pocketshadow.app.data.repository.LlmRepository")
        val repository = repositoryClass
            .getConstructor(Context::class.java, SettingsRepository::class.java)
            .newInstance(context, settings)
        val validator = repositoryClass.getDeclaredMethod("validateModelFile", String::class.java)
            .apply { isAccessible = true }
        val invalid = File.createTempFile("pocketshadow", ".task", context.cacheDir)
        invalid.writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0, 0, 0, 0, 0, 0))

        val result = validator.invoke(repository, invalid.absolutePath) as String?
        assertTrue(result?.contains("MediaPipe") == true)
        invalid.delete()
    }

    @Test
    fun migration1To2_preservesExistingChatsAndAddsInputHistory() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PocketShadowDatabase::class.java
        )
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO chat_sessions(sessionId, title, createdAt) VALUES ('s1', 'Old chat', 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-test",
            2,
            true,
            PocketShadowDatabase.MIGRATION_1_2
        ).use { migrated ->
            migrated.query("SELECT title FROM chat_sessions WHERE sessionId = 's1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Old chat", cursor.getString(0))
            }
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'input_history'")
                .use { cursor -> assertTrue(cursor.moveToFirst()) }
        }
    }
}
