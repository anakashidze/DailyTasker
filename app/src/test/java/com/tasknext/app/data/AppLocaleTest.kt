package com.tasknext.app.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.tasknext.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppLocaleTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test @Config(sdk = [28]) fun olderAndroidKeepsTheSavedAppLanguage() {
        AppLocale.setLanguage(context, "ru")
        assertEquals("ru", SettingsStore(context).languageCode)
        assertEquals("Отложить", AppLocale.localizedContext(context).getString(R.string.snooze_label))
    }

    @Test @Config(sdk = [33]) fun systemLanguageChangeOverridesOldPreference() {
        AppLocale.setLanguage(context, "en")
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ka")
        AppLocale.initialize(context)
        assertEquals("ka", SettingsStore(context).languageCode)
        assertEquals("გადადება", AppLocale.localizedContext(context).getString(R.string.snooze_label))
    }

    @Test @Config(sdk = [33]) fun existingSavedLanguageMigratesOnlyOnce() {
        context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE)
            .edit().putString("language_code", "ru").apply()
        AppLocale.initialize(context)
        assertEquals("ru", SettingsStore(context).languageCode)
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        AppLocale.initialize(context)
        assertEquals("en", SettingsStore(context).languageCode)
    }
}
