package com.capstone.navitest.ui

import android.content.Context
import android.content.SharedPreferences
import com.capstone.navitest.utils.Constants

class LanguageManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // 현재 선택된 언어
    var currentLanguage: String = Constants.LANG_KOREAN
        private set

    init {
        // 저장된 언어 설정 로드
        loadSavedLanguage()
    }

    private fun loadSavedLanguage() {
        currentLanguage = prefs.getString(Constants.PREF_LANG_KEY, Constants.LANG_KOREAN)
            ?: Constants.LANG_KOREAN
    }

    fun changeLanguage(newLanguage: String): Boolean {
        if (currentLanguage == newLanguage) return false

        currentLanguage = newLanguage

        // 설정 저장
        prefs.edit().putString(Constants.PREF_LANG_KEY, currentLanguage).apply()

        return true
    }

    // 현재 언어에 맞는 문자열 반환 유틸리티 메소드들
    fun getLocalizedString(koreanText: String, englishText: String): String {
        return if (currentLanguage == Constants.LANG_KOREAN) koreanText else englishText
    }
}