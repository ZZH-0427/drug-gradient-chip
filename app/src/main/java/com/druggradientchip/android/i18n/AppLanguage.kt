package com.druggradientchip.android.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val languageTag: String) {
    FOLLOW_SYSTEM(""),
    ENGLISH("en"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ;

    companion object {
        fun fromLanguageTags(tags: String): AppLanguage = when {
            tags.startsWith("zh", ignoreCase = true) -> SIMPLIFIED_CHINESE
            tags.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> FOLLOW_SYSTEM
        }
    }
}

object AppLanguageController {
    fun current(): AppLanguage = AppLanguage.fromLanguageTags(
        AppCompatDelegate.getApplicationLocales().toLanguageTags(),
    )

    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.languageTag),
        )
    }

    fun localizedContext(context: Context): Context {
        val languageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (languageTags.isBlank()) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(languageTags))
        return context.createConfigurationContext(configuration)
    }
}
