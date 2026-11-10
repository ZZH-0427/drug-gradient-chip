package com.druggradientchip.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun mapsSupportedLanguageTags() {
        assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguage.fromLanguageTags(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-US"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-CN"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-Hans-CN"))
    }

    @Test
    fun exposesOnlyTheThreeProductModes() {
        assertEquals(
            listOf("", "en", "zh-CN"),
            AppLanguage.values().map { it.languageTag },
        )
    }
}
