package com.tasknext.app.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Android 13's app-language setting is authoritative after the one-time upgrade. */
object AppLocale {
    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = preferences(context)
        if (prefs.getBoolean("platform_locale_initialized", false)) return
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.isEmpty) {
            manager.applicationLocales = LocaleList.forLanguageTags(prefs.getString("language_code", "en") ?: "en")
        }
        prefs.edit().putBoolean("platform_locale_initialized", true).apply()
    }

    fun languageCode(context: Context): String {
        val code = if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) locales[0].language
            else if (preferences(context).getBoolean("platform_locale_initialized", false))
                Resources.getSystem().configuration.locales[0].language
            else preferences(context).getString("language_code", "en") ?: "en"
        } else preferences(context).getString("language_code", "en") ?: "en"
        return code.takeIf { it in setOf("en", "ka", "ru") } ?: "en"
    }

    fun setLanguage(context: Context, code: String) {
        require(code in setOf("en", "ka", "ru"))
        preferences(context).edit().putString("language_code", code)
            .putBoolean("platform_locale_initialized", true).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(code)
        }
    }

    fun localizedContext(context: Context): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageCode(context)))
        }
    )
}
