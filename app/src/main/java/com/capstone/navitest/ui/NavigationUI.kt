package com.capstone.navitest.ui

import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.capstone.navitest.MainActivity
import com.capstone.navitest.R
import com.capstone.navitest.navigation.NavigationManager
import com.capstone.navitest.utils.Constants
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue

class NavigationUI(
    private val activity: MainActivity,
    private val languageManager: LanguageManager
) {
    private lateinit var navigationManager: NavigationManager

    // 기존 UI 컴포넌트
    private val startNavigationButton: Button = activity.findViewById(R.id.startNavigationButton)
    private val cancelButton: Button = activity.findViewById(R.id.cancelButton)
    private val recenterButton: Button = activity.findViewById(R.id.recenterButton)
    private val languageRadioGroup: RadioGroup = activity.findViewById(R.id.languageRadioGroup)
    private val maneuverView: MapboxManeuverView = activity.findViewById(R.id.maneuverView)
    private val tripProgressView: MapboxTripProgressView = activity.findViewById(R.id.tripProgressView)

    // 새로 추가된 내비게이션 모드 관련 UI 컴포넌트
    private val navigationModeRadioGroup: RadioGroup = activity.findViewById(R.id.navigationModeRadioGroup)
    private val radioButtonDriving: RadioButton = activity.findViewById(R.id.radioButtonDriving)
    private val radioButtonWalking: RadioButton = activity.findViewById(R.id.radioButtonWalking)
    private val radioButtonCycling: RadioButton = activity.findViewById(R.id.radioButtonCycling)

    init {
        setupUIComponents()
    }

    private fun setupUIComponents() {
        // 언어 관련 라디오 버튼 참조
        val radioButtonKo: RadioButton = activity.findViewById(R.id.radioButtonKo)
        val radioButtonEn: RadioButton = activity.findViewById(R.id.radioButtonEn)

        // 초기 언어 설정에 따라 라디오 버튼 상태 설정
        radioButtonKo.isChecked = languageManager.currentLanguage == Constants.LANG_KOREAN
        radioButtonEn.isChecked = languageManager.currentLanguage == Constants.LANG_ENGLISH

        // 초기 내비게이션 모드 설정
        setNavigationModeRadioButtons()

        // 버튼 클릭 리스너 설정
        setupButtonListeners()

        // 언어 변경 리스너 설정
        setupLanguageChangeListener()

        // 내비게이션 모드 변경 리스너 설정
        setupNavigationModeChangeListener()

        // 초기 UI 상태 설정
        updateUITexts()
        setInitialUIState()
    }

    private fun setNavigationModeRadioButtons() {
        when (languageManager.currentNavigationMode) {
            Constants.NAVIGATION_MODE_DRIVING -> radioButtonDriving.isChecked = true
            Constants.NAVIGATION_MODE_WALKING -> radioButtonWalking.isChecked = true
            Constants.NAVIGATION_MODE_CYCLING -> radioButtonCycling.isChecked = true
        }
    }

    private fun setupButtonListeners() {
        startNavigationButton.setOnClickListener {
            if (::navigationManager.isInitialized) {
                navigationManager.startNavigation()
                updateUIForNavigationStart()
            }
        }

        cancelButton.setOnClickListener {
            if (::navigationManager.isInitialized) {
                navigationManager.cancelNavigation()
                updateUIForNavigationCancel()
            }
        }

        recenterButton.setOnClickListener {
            if (::navigationManager.isInitialized) {
                navigationManager.recenterCamera()
            }
        }
    }

    private fun setupLanguageChangeListener() {
        languageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLanguage = if (checkedId == R.id.radioButtonKo)
                Constants.LANG_KOREAN else Constants.LANG_ENGLISH

            if (languageManager.changeLanguage(newLanguage)) {
                updateUITexts()
                updateNavigationModeTexts()
                navigationManager.updateLanguage(newLanguage)

                // 언어 변경 알림
                showLanguageChangedToast()
            }
        }
    }

    // 새로 추가된 내비게이션 모드 변경 리스너
    private fun setupNavigationModeChangeListener() {
        navigationModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioButtonDriving -> Constants.NAVIGATION_MODE_DRIVING
                R.id.radioButtonWalking -> Constants.NAVIGATION_MODE_WALKING
                R.id.radioButtonCycling -> Constants.NAVIGATION_MODE_CYCLING
                else -> Constants.NAVIGATION_MODE_DRIVING
            }

            if (languageManager.changeNavigationMode(newMode)) {
                // NavigationManager를 통해 RouteManager에 변경 사항 전달
                if (::navigationManager.isInitialized) {
                    navigationManager.getRouteManager().changeNavigationMode(newMode)
                }

                // 모드 변경 알림
                showNavigationModeChangedToast(newMode)
            }
        }
    }

    private fun updateUITexts() {
        startNavigationButton.text = languageManager.getLocalizedString(
            "내비게이션 시작",
            "Start Navigation"
        )
        cancelButton.text = languageManager.getLocalizedString(
            "내비게이션 취소",
            "Cancel Navigation"
        )
        recenterButton.text = languageManager.getLocalizedString(
            "위치로 돌아가기",
            "Return to Route"
        )
    }

    // 내비게이션 모드 텍스트 업데이트 (언어 변경 시)
    private fun updateNavigationModeTexts() {
        radioButtonDriving.text = if (languageManager.currentLanguage == Constants.LANG_KOREAN) {
            "🚗 자동차"
        } else {
            "🚗 Driving"
        }

        radioButtonWalking.text = if (languageManager.currentLanguage == Constants.LANG_KOREAN) {
            "🚶 도보"
        } else {
            "🚶 Walking"
        }

        radioButtonCycling.text = if (languageManager.currentLanguage == Constants.LANG_KOREAN) {
            "🚴 자전거"
        } else {
            "🚴 Cycling"
        }
    }

    private fun setInitialUIState() {
        startNavigationButton.isEnabled = false
        cancelButton.visibility = View.GONE
        recenterButton.visibility = View.GONE
        maneuverView.visibility = View.GONE
        tripProgressView.visibility = View.GONE
    }

    fun updateUIForNavigationStart() {
        startNavigationButton.visibility = View.GONE
        cancelButton.visibility = View.VISIBLE
        maneuverView.visibility = View.VISIBLE
        tripProgressView.visibility = View.VISIBLE

        // 내비게이션 중에는 모드 변경 비활성화
        navigationModeRadioGroup.isEnabled = false
        setNavigationModeRadioButtonsEnabled(false)

        // ✅ 추가: 내비게이션 모드 컨테이너 숨김
        setNavigationModeContainerVisibility(false)

        activity.setNavigationActive(true)
    }

    fun updateUIForNavigationCancel() {
        cancelButton.visibility = View.GONE
        startNavigationButton.visibility = View.VISIBLE
        recenterButton.visibility = View.GONE
        maneuverView.visibility = View.GONE
        tripProgressView.visibility = View.GONE
        startNavigationButton.isEnabled = false

        // 내비게이션 종료 시 모드 변경 다시 활성화
        navigationModeRadioGroup.isEnabled = true
        setNavigationModeRadioButtonsEnabled(true)

        // ✅ 추가: 내비게이션 모드 컨테이너 다시 표시
        setNavigationModeContainerVisibility(true)

        activity.setNavigationActive(false)
    }

    // 내비게이션 모드 라디오 버튼들의 활성화/비활성화
    private fun setNavigationModeRadioButtonsEnabled(enabled: Boolean) {
        radioButtonDriving.isEnabled = enabled
        radioButtonWalking.isEnabled = enabled
        radioButtonCycling.isEnabled = enabled
    }

    fun showLanguageChangedToast() {
        val message = languageManager.getLocalizedString(
            "언어가 한국어로 변경되었습니다.",
            "Language changed to English."
        )
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    // 내비게이션 모드 변경 알림 토스트
    private fun showNavigationModeChangedToast(mode: String) {
        val modeDisplayName = languageManager.getNavigationModeDisplayName(mode)
        val message = languageManager.getLocalizedString(
            "내비게이션 모드가 $modeDisplayName(으)로 변경되었습니다.",
            "Navigation mode changed to $modeDisplayName."
        )
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    fun setStartButtonEnabled(enabled: Boolean) {
        startNavigationButton.isEnabled = enabled
    }

    fun setRecenterButtonVisibility(visible: Boolean) {
        recenterButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // Expected 타입을 직접 받아 처리
    fun updateManeuverView(maneuversResult: Expected<ManeuverError, List<Maneuver>>) {
        maneuverView.renderManeuvers(maneuversResult)
    }

    // TripProgressUpdateValue 타입 사용
    fun updateTripProgressView(tripProgress: TripProgressUpdateValue) {
        tripProgressView.render(tripProgress)
    }

    fun setNavigationManager(navManager: NavigationManager) {
        navigationManager = navManager
        // NavigationManager가 설정된 후 언어 변경 사항을 즉시 반영
        updateNavigationModeTexts()
    }

    // 내비게이션 모드 컨테이너 표시/숨김 제어
    fun setNavigationModeContainerVisibility(visible: Boolean) {
        val navigationModeContainer = activity.findViewById<View>(R.id.navigationModeContainer)
        navigationModeContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }
}