package com.capstone.navitest

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.capstone.navitest.map.MapInitializer
import com.capstone.navitest.map.OfflineTileManager
import com.capstone.navitest.navigation.LocationManager
import com.capstone.navitest.navigation.NavigationManager
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.ui.NavigationUI
import com.capstone.navitest.utils.PermissionHelper
import com.mapbox.common.MapboxOptions
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    // 필요한 매니저 클래스들을 선언
    private lateinit var mapInitializer: MapInitializer
    private lateinit var navigationManager: NavigationManager
    private lateinit var locationManager: LocationManager
    private lateinit var offlineTileManager: OfflineTileManager
    private lateinit var navigationUI: NavigationUI
    private lateinit var languageManager: LanguageManager
    private lateinit var permissionHelper: PermissionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Mapbox 액세스 토큰 설정
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

        // 각 매니저 초기화
        initializeManagers()

        // 권한 확인 및 요청
        permissionHelper.checkLocationPermissions()
    }

    private fun initializeManagers() {
        // 언어 매니저 초기화 (다른 매니저가 이를 참조할 수 있음)
        languageManager = LanguageManager(this)

        // 맵 초기화 매니저 생성
        mapInitializer = MapInitializer(this, languageManager)

        // 권한 도우미 초기화 - languageManager 추가
        permissionHelper = PermissionHelper(this, languageManager)
        permissionHelper.setPermissionCallback(object : PermissionHelper.PermissionCallback {
            override fun onPermissionGranted() {
                // 권한 허용 시 초기화 계속 진행
                initializeAfterPermissionGranted()
            }

            override fun onPermissionDenied() {
                // 권한 거부 시 별도 처리 없음 (Toast는 PermissionHelper에서 표시)
            }
        })

        // 오프라인 타일 매니저 초기화
        offlineTileManager = OfflineTileManager(
            this,
            mapInitializer.getTileStore(),
            languageManager
        )
    }

    fun initializeAfterPermissionGranted() {
        try {
            // 맵 초기화
            mapInitializer.initializeMap()

            // 위치 매니저 초기화
            locationManager = LocationManager(
                this,
                mapInitializer.getMapView()
            )

            // UI 매니저 생성
            navigationUI = NavigationUI(
                this,
                languageManager
            )

            try {
                // 네비게이션 매니저 초기화 (다른 매니저들에 의존)
                navigationManager = NavigationManager(
                    this,
                    lifecycleScope,
                    mapInitializer.getMapView(),
                    mapInitializer,
                    mapInitializer.getTileStore(),
                    languageManager,
                    navigationUI
                )

                // UI와 네비게이션 매니저 연결
                navigationUI.setNavigationManager(navigationManager)

            } catch (e: Exception) {
                // 네비게이션 초기화 실패 시 처리
                Log.e("MainActivity", "Error initializing components", e)

                // 기본 지도만 보여주기 위한 처리
                val message = "내비게이션 초기화 중 오류가 발생했습니다: ${e.message}\n기본 지도 모드로 실행됩니다."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // 심각한 오류 발생 시 처리
            Log.e("MainActivity", "Critical error initializing app", e)
            Toast.makeText(
                this,
                "앱 초기화 중 심각한 오류가 발생했습니다: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initializeNavigation() {
        // 맵 클릭 리스너 설정
        mapInitializer.setMapClickListener { point ->
            if (!navigationManager.isNavigating()) {
                navigationManager.setDestination(point)
                return@setMapClickListener true
            }
            false
        }
    }

    private fun showPermissionRequiredMessage() {
        val message = languageManager.getLocalizedString(
            "위치 권한이 필요합니다. 앱을 사용하려면 설정에서 권한을 허용해주세요.",
            "Location permission is required. Please enable it in settings to use the app."
        )
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 각 매니저의 정리 메소드 호출
        if (::navigationManager.isInitialized) {
            navigationManager.cleanup()
        }
    }
}