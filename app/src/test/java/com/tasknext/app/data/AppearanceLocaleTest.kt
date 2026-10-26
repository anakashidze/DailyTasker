package com.tasknext.app.data

import android.content.Context
import com.tasknext.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppearanceLocaleTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE)
    @Before fun setup() { prefs.edit().clear().commit() }

    @Test fun themeUpgradePreservesExplicitChoiceAndSystemCanOverrideIt() {
        assertEquals(ThemeMode.SYSTEM, SettingsStore(context).themeMode)
        prefs.edit().putBoolean("dark_theme", true).commit()
        assertEquals(ThemeMode.DARK, SettingsStore(context).themeMode)
        prefs.edit().putBoolean("dark_theme", false).commit()
        assertEquals(ThemeMode.LIGHT, SettingsStore(context).themeMode)
        SettingsStore(context).themeMode = ThemeMode.SYSTEM
        assertEquals(ThemeMode.SYSTEM, SettingsStore(context).themeMode)
    }

    @Test fun builtInLabelsFollowLanguageWithoutRenamingCustomGroups() {
        SettingsStore(context).languageCode = "ru"
        val russian = AppLocale.localizedContext(context)
        assertEquals("Работа", TaskGroups.displayName(russian, "Work", "work"))
        assertEquals("My project", TaskGroups.displayName(russian, "My project", "work"))
        assertEquals("Work", TaskGroups.displayName(russian, "Work", "star"))
        assertEquals("Work", TaskGroups.builtIns.last().name)
        SettingsStore(context).languageCode = "ka"
        assertEquals("სამუშაო", TaskGroups.displayName(AppLocale.localizedContext(context), "Work", "work"))
    }

    @Test fun russianStreakCountsUseOneFewAndManyForms() {
        SettingsStore(context).languageCode = "ru"
        val resources = AppLocale.localizedContext(context).resources
        mapOf(1 to "1 день", 2 to "2 дня", 5 to "5 дней", 21 to "21 день").forEach { (count, expected) ->
            assertEquals(expected, resources.getQuantityString(R.plurals.streak_days, count, count))
        }
    }
}
