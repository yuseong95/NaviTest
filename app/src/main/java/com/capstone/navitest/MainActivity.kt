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
        // 먼저 언어와 권한 관리자 초기화
        languageManager = LanguageManager(this)
        permissionHelper = PermissionHelper(this, languageManager)

        // 권한이 필요하지 않은 맵 컴포넌트 초기화
        mapInitializer = MapInitializer(this, languageManager)
        offlineTileManager = OfflineTileManager(
            this,
            mapInitializer.getTileStore(),
            languageManager
        )

        // 내비게이션 의존성 없이 먼저 UI 설정
        navigationUI = NavigationUI(this, languageManager)

        // 권한 콜백 설정
        permissionHelper.setPermissionCallback(object : PermissionHelper.PermissionCallback {
            override fun onPermissionGranted() {
                initializeAfterPermissionGranted()
            }

            override fun onPermissionDenied() {
                showPermissionRequiredMessage()
            }
        })
    }

    private fun initializeAfterPermissionGranted() {
        try {
            // 1. 맵 초기화
            mapInitializer.initializeMap()

            // 2. 위치 매니저 초기화
            locationManager = LocationManager(
                this,
                mapInitializer.getMapView()
            )

            // 3. 네비게이션 매니저 초기화 - 마지막에 초기화
            navigationManager = NavigationManager(
                this,
                lifecycleScope,
                mapInitializer.getMapView(),
                mapInitializer,
                mapInitializer.getTileStore(),
                languageManager,
                navigationUI
            )

            // 4. 위치 매니저에 위치 변경 리스너 설정 (registerLocationObserver 대신)
            locationManager.setLocationChangeListener(navigationManager)

            // 5. UI와 네비게이션 매니저 연결
            navigationUI.setNavigationManager(navigationManager)

            // 6. 맵 클릭 리스너 설정
            initializeMapClickListener()

        } catch (e: Exception) {
            Log.e("MainActivity", "컴포넌트 초기화 오류", e)
            Toast.makeText(
                this,
                "내비게이션 초기화 중 오류가 발생했습니다: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initializeMapClickListener() {
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