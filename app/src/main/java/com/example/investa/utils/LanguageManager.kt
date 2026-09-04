package com.example.investa.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.investa.R
import java.util.Locale

internal object LanguageManager {
    private const val PREFERENCES = "investa_preferences"
    private const val LANGUAGE_KEY = "language"
    private const val PENDING_LANGUAGE_TOAST_KEY = "pending_language_toast"

    enum class AppLanguage(val value: String, val labelRes: Int, val localeTag: String) {
        ENGLISH("en", R.string.language_english, "en"),
        INDONESIAN("id", R.string.language_indonesian, "id")
    }

    fun current(context: Context): AppLanguage {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val savedValue = preferences.getString(LANGUAGE_KEY, null)
        if (savedValue == null) {
            val deviceLanguage = context.resources.configuration.locales[0].language
            return if (deviceLanguage.equals("id", ignoreCase = true) ||
                deviceLanguage.equals("in", ignoreCase = true)
            ) {
                AppLanguage.INDONESIAN
            } else {
                AppLanguage.ENGLISH
            }
        }
        return AppLanguage.entries.firstOrNull { it.value == savedValue } ?: AppLanguage.ENGLISH
    }

    fun apply(context: Context) {
        val tags = current(context).localeTag
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tags) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        }
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language.value)
            .apply()
    }

    fun markLanguageChangedToast(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PENDING_LANGUAGE_TOAST_KEY, true)
            .apply()
    }

    fun consumeLanguageChangedToast(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val pending = preferences.getBoolean(PENDING_LANGUAGE_TOAST_KEY, false)
        if (pending) {
            preferences.edit().remove(PENDING_LANGUAGE_TOAST_KEY).apply()
        }
        return pending
    }

    fun label(context: Context, language: AppLanguage = current(context)): String =
        context.getString(language.labelRes)

    fun locale(context: Context): Locale = when (current(context)) {
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.INDONESIAN -> Locale("id")
    }
}
