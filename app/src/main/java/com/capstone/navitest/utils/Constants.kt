package com.capstone.navitest.utils

object Constants {
    // 언어 설정 관련 상수
    const val PREFS_NAME = "NaviSettings"
    const val PREF_LANG_KEY = "language"
    const val LANG_KOREAN = "ko"
    const val LANG_ENGLISH = "en"

    // 경로 요청 관련 상수
    const val ROUTE_REQUEST_DEBOUNCE_TIME = 3000L // 사용자 액션용
    const val AUTO_ROUTE_UPDATE_DEBOUNCE_TIME = 15000L // 자동 업데이트용 (15초)

    // 내비게이션 모드 관련 상수 추가
    const val PREF_NAVIGATION_MODE_KEY = "navigation_mode"

    // 내비게이션 프로필 상수
    const val NAVIGATION_MODE_DRIVING = "driving"
    const val NAVIGATION_MODE_WALKING = "walking"
    const val NAVIGATION_MODE_CYCLING = "cycling"

    // 기본 내비게이션 모드
    const val DEFAULT_NAVIGATION_MODE = NAVIGATION_MODE_DRIVING
}