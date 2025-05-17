package com.capstone.navitest.ui

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.capstone.navitest.MainActivity
import com.capstone.navitest.R
import com.capstone.navitest.navigation.NavigationManager
import com.capstone.navitest.utils.Constants
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
// 올바른 Expected 타입 import
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
// 올바른 Trip Progress 관련 타입
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue

class NavigationUI(
    private val activity: MainActivity,
    private val languageManager: LanguageManager
) {
    private lateinit var navigationManager: NavigationManager

    // UI 컴포넌트
    private val buttonLayout: LinearLayout = activity.findViewById(R.id.buttonLayout)
    private val startNavigationButton: Button = activity.findViewById(R.id.startNavigationButton)
    private val cancelButton: Button = activity.findViewById(R.id.cancelButton)
    private val recenterButton: Button = activity.findViewById(R.id.recenterButton)
    private val languageRadioGroup: RadioGroup = activity.findViewById(R.id.languageRadioGroup)
    private val maneuverView: MapboxManeuverView = activity.findViewById(R.id.maneuverView)
    private val tripProgressView: MapboxTripProgressView = activity.findViewById(R.id.tripProgressView)

    init {
        setupUIComponents()
    }

    private fun setupUIComponents() {
        // 라디오 버튼 참조
        val radioButtonKo: RadioButton = activity.findViewById(R.id.radioButtonKo)
        val radioButtonEn: RadioButton = activity.findViewById(R.id.radioButtonEn)

        // 초기 언어 설정에 따라 라디오 버튼 상태 설정
        radioButtonKo.isChecked = languageManager.currentLanguage == Constants.LANG_KOREAN
        radioButtonEn.isChecked = languageManager.currentLanguage == Constants.LANG_ENGLISH

        // 버튼 클릭 리스너 설정
        setupButtonListeners()

        // 언어 변경 리스너 설정
        setupLanguageChangeListener()

        // 초기 UI 상태 설정
        updateUITexts()
        setInitialUIState()
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
                navigationManager.updateLanguage(newLanguage)

                // 언어 변경 알림
                showLanguageChangedToast()
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
    }

    fun updateUIForNavigationCancel() {
        cancelButton.visibility = View.GONE
        startNavigationButton.visibility = View.VISIBLE
        recenterButton.visibility = View.GONE
        maneuverView.visibility = View.GONE
        tripProgressView.visibility = View.GONE
        startNavigationButton.isEnabled = false
    }

    fun showLanguageChangedToast() {
        val message = languageManager.getLocalizedString(
            "언어가 한국어로 변경되었습니다.",
            "Language changed to English."
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
    }
}