package com.capstone.navitest.ui

import com.capstone.navitest.MainActivity
import com.capstone.navitest.navigation.NavigationManager
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue

class NavigationUI(
    private val activity: MainActivity,
    private val languageManager: LanguageManager
) {
    private lateinit var navigationManager: NavigationManager

    // 임시로 최소한의 구현 - 모든 필요한 메서드들 추가
    fun updateUIForNavigationStart() {
        // 임시 구현
    }

    fun updateUIForNavigationCancel() {
        // 임시 구현
    }

    fun showLanguageChangedToast() {
        // 임시 구현
    }

    fun setRecenterButtonVisibility(visible: Boolean) {
        // 임시 구현
    }

    fun updateManeuverView(maneuversResult: Expected<ManeuverError, List<Maneuver>>) {
        // 임시 구현
    }

    fun updateTripProgressView(tripProgress: TripProgressUpdateValue) {
        // 임시 구현
    }

    fun setNavigationManager(navManager: NavigationManager) {
        navigationManager = navManager
    }

    // ✅ 누락된 메서드들 추가
    fun setStartButtonEnabled(enabled: Boolean) {
        // 임시 구현 - 나중에 새로운 UI 구조에 맞게 수정 예정
    }

    @Deprecated("No longer needed - handled by MainActivity")
    fun setNavigationModeContainerVisibility(visible: Boolean) {
        // 임시 구현 - 새로운 UI에서는 MainActivity에서 처리
    }
}