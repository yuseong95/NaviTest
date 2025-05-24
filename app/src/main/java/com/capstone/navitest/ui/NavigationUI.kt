package com.capstone.navitest.ui

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.capstone.navitest.MainActivity
import com.capstone.navitest.R
import com.capstone.navitest.navigation.NavigationManager
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView

class NavigationUI(
    private val activity: MainActivity,
    private val languageManager: LanguageManager
) {
    private lateinit var navigationManager: NavigationManager

    // UI 컴포넌트들 - null 안전성을 위해 lazy 초기화
    private val mainActionButton: Button by lazy { activity.findViewById(R.id.mainActionButton) }
    private val recenterButton: com.google.android.material.floatingactionbutton.FloatingActionButton by lazy {
        activity.findViewById(R.id.recenterButton)
    }
    private val navigationGuidanceContainer: LinearLayout by lazy {
        activity.findViewById(R.id.navigationGuidanceContainer)
    }
    private val maneuverView: MapboxManeuverView by lazy { activity.findViewById(R.id.maneuverView) }
    private val tripProgressView: MapboxTripProgressView by lazy { activity.findViewById(R.id.tripProgressView) }
    private val bottomNavigationContainer: RelativeLayout by lazy {
        activity.findViewById(R.id.bottomNavigationContainer)
    }

    init {
        // 초기화는 setNavigationManager에서 수행
        Log.d("NavigationUI", "NavigationUI created")
    }

    private fun setupInitialState() {
        try {
            // 초기 상태 설정
            mainActionButton.visibility = View.GONE
            recenterButton.visibility = View.GONE
            navigationGuidanceContainer.visibility = View.GONE

            // 초기 텍스트 설정
            updateUITexts()

            Log.d("NavigationUI", "Initial state setup completed")
        } catch (e: Exception) {
            Log.e("NavigationUI", "Error setting up initial state", e)
        }
    }

    private fun setupButtonListeners() {
        try {
            // 메인 액션 버튼 리스너는 MainActivity에서 처리

            // 재중앙화 버튼 리스너
            recenterButton.setOnClickListener {
                if (::navigationManager.isInitialized) {
                    navigationManager.recenterCamera()
                } else {
                    Log.w("NavigationUI", "NavigationManager not initialized when recenter button clicked")
                }
            }

            Log.d("NavigationUI", "Button listeners setup completed")
        } catch (e: Exception) {
            Log.e("NavigationUI", "Error setting up button listeners", e)
        }
    }

    fun updateUIForNavigationStart() {
        activity.runOnUiThread {
            try {
                // 메인 액션 버튼을 "취소"로 변경
                mainActionButton.text = languageManager.getLocalizedString(
                    "내비게이션 취소",
                    "Cancel Navigation"
                )
                mainActionButton.visibility = View.VISIBLE

                // 내비게이션 안내 UI 표시
                navigationGuidanceContainer.visibility = View.VISIBLE
                maneuverView.visibility = View.VISIBLE
                tripProgressView.visibility = View.VISIBLE

                // 하단 내비게이션 바 숨기기 (내비게이션 중에는 모드 변경 불가)
                bottomNavigationContainer.visibility = View.GONE

                // 재중앙화 버튼은 카메라 상태에 따라 NavigationManager에서 제어

                Log.d("NavigationUI", "Navigation start UI updated")
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error updating UI for navigation start", e)
            }
        }
    }

    fun updateUIForNavigationCancel() {
        activity.runOnUiThread {
            try {
                // 메인 액션 버튼을 "시작"으로 변경하고 숨기기
                mainActionButton.text = languageManager.getLocalizedString(
                    "내비게이션 시작",
                    "Start Navigation"
                )
                mainActionButton.visibility = View.GONE

                // 내비게이션 안내 UI 숨기기
                navigationGuidanceContainer.visibility = View.GONE
                maneuverView.visibility = View.GONE
                tripProgressView.visibility = View.GONE

                // 재중앙화 버튼 숨기기
                recenterButton.visibility = View.GONE

                // 하단 내비게이션 바 다시 표시
                bottomNavigationContainer.visibility = View.VISIBLE

                Log.d("NavigationUI", "Navigation cancel UI updated")
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error updating UI for navigation cancel", e)
            }
        }
    }

    fun setStartButtonEnabled(enabled: Boolean) {
        activity.runOnUiThread {
            try {
                if (enabled) {
                    mainActionButton.visibility = View.VISIBLE
                    mainActionButton.isEnabled = true
                    mainActionButton.alpha = 1.0f
                } else {
                    // 내비게이션 중이 아니라면 버튼 숨기기
                    if (::navigationManager.isInitialized && !navigationManager.isNavigating()) {
                        mainActionButton.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error setting start button enabled state", e)
            }
        }
    }

    fun setRecenterButtonVisibility(visible: Boolean) {
        activity.runOnUiThread {
            try {
                recenterButton.visibility = if (visible) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error setting recenter button visibility", e)
            }
        }
    }

    fun updateManeuverView(maneuversResult: Expected<ManeuverError, List<Maneuver>>) {
        activity.runOnUiThread {
            try {
                maneuverView.renderManeuvers(maneuversResult)
            } catch (e: Exception) {
                android.util.Log.e("NavigationUI", "Error updating maneuver view", e)
            }
        }
    }

    fun updateTripProgressView(tripProgress: TripProgressUpdateValue) {
        activity.runOnUiThread {
            try {
                tripProgressView.render(tripProgress)
            } catch (e: Exception) {
                android.util.Log.e("NavigationUI", "Error updating trip progress view", e)
            }
        }
    }

    fun showLanguageChangedToast() {
        val message = languageManager.getLocalizedString(
            "언어가 한국어로 변경되었습니다.",
            "Language changed to English."
        )
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    fun setNavigationManager(navManager: NavigationManager) {
        navigationManager = navManager

        // NavigationManager가 설정된 후에 UI 초기화 수행
        setupInitialState()
        setupButtonListeners()
        updateUITexts()

        Log.d("NavigationUI", "NavigationManager set and UI initialized")
    }

    private fun updateUITexts() {
        try {
            // 현재 상태에 따라 버튼 텍스트 업데이트
            if (::navigationManager.isInitialized && navigationManager.isNavigating()) {
                mainActionButton.text = languageManager.getLocalizedString(
                    "내비게이션 취소",
                    "Cancel Navigation"
                )
            } else {
                mainActionButton.text = languageManager.getLocalizedString(
                    "내비게이션 시작",
                    "Start Navigation"
                )
            }
        } catch (e: Exception) {
            Log.e("NavigationUI", "Error updating UI texts", e)
        }
    }

    // 언어 변경 시 UI 텍스트 업데이트
    fun updateLanguage() {
        updateUITexts()
    }

    @Deprecated("No longer needed - handled by MainActivity")
    fun setNavigationModeContainerVisibility(visible: Boolean) {
        // 새로운 UI에서는 MainActivity에서 처리하므로 더 이상 필요 없음
    }
}