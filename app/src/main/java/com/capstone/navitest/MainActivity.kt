package com.capstone.navitest

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.capstone.navitest.map.MapInitializer
import com.capstone.navitest.map.OfflineTileManager
import com.capstone.navitest.navigation.NavigationManager
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.ui.NavigationUI
import com.capstone.navitest.utils.PermissionHelper
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import java.io.File

class MainActivity : ComponentActivity() {
    // 필요한 매니저 클래스들을 선언
    private lateinit var mapInitializer: MapInitializer
    private lateinit var navigationManager: NavigationManager
    private lateinit var offlineTileManager: OfflineTileManager
    private lateinit var navigationUI: NavigationUI
    private lateinit var languageManager: LanguageManager
    private lateinit var permissionHelper: PermissionHelper

    // MapboxNavigation 정의 - Ark7에서 사용하던 방식으로 정의
    private val mapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                Log.d("MainActivity", "Navigation observer attached")

                // NavigationManager가 아직 초기화되지 않았거나 MapboxNavigation이 설정되지 않았다면
                if (!::navigationManager.isInitialized) {
                    try {
                        // NavigationManager 초기화
                        navigationManager = NavigationManager(
                            this@MainActivity,
                            lifecycleScope,
                            mapInitializer.getMapView(),
                            mapInitializer,
                            mapInitializer.getTileStore(),
                            languageManager,
                            navigationUI
                        )

                        // UI와 네비게이션 매니저 연결
                        navigationUI.setNavigationManager(navigationManager)

                        // 맵 클릭 리스너 설정
                        initializeMapClickListener()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "NavigationManager 초기화 오류", e)
                    }
                }

                // MapboxNavigation 인스턴스를 NavigationManager에 전달
                navigationManager.onNavigationAttached(mapboxNavigation)
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                Log.d("MainActivity", "Navigation observer detached")
                if (::navigationManager.isInitialized) {
                    navigationManager.onNavigationDetached(mapboxNavigation)
                }
            }
        },
        onInitialize = {
            setupMapboxNavigation()
        }
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Mapbox 액세스 토큰 설정
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

        // 먼저 MapboxNavigationApp 설정
        setupMapboxNavigation()

        // 각 매니저 초기화
        initializeManagers()

        // 권한 확인 및 요청
        permissionHelper.checkLocationPermissions()
    }

    private fun setupMapboxNavigation() {
        // TileStore 초기화 (필요한 경우)
        val tilePath = File(filesDir, "mbx-offline").absolutePath
        val tileStore = TileStore.create(tilePath)

        // RoutingTilesOptions 생성
        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()

        // NavigationOptions 생성
        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .navigatorPredictionMillis(3000)
            .build()

        // MapboxNavigationApp 초기화
        MapboxNavigationApp.setup(navOptions)

        Log.d("MainActivity", "MapboxNavigationApp setup completed")
    }

    private fun initializeManagers() {
        Log.d("MainActivity", "Initializing basic managers")

        // 먼저 언어와 권한 관리자 초기화
        languageManager = LanguageManager(this)

        // 권한 관리자 초기화
        permissionHelper = PermissionHelper(this, languageManager)

        // 맵 초기화
        mapInitializer = MapInitializer(this, languageManager)

        // 오프라인 타일 관리자 초기화
        offlineTileManager = OfflineTileManager(
            this,
            mapInitializer.getTileStore(),
            languageManager
        )

        // UI 관리자 초기화
        navigationUI = NavigationUI(this, languageManager)

        // 권한 콜백 설정
        permissionHelper.setPermissionCallback(object : PermissionHelper.PermissionCallback {
            override fun onPermissionGranted() {
                Log.d("MainActivity", "Location permissions granted")
                initializeAfterPermissionGranted()
            }

            override fun onPermissionDenied() {
                Log.d("MainActivity", "Location permissions denied")
                showPermissionRequiredMessage()
            }
        })

        Log.d("MainActivity", "Basic managers initialized")
    }

    private fun initializeAfterPermissionGranted() {
        try {
            Log.d("MainActivity", "Initializing after permission granted")

            // 맵 초기화
            mapInitializer.initializeMap()

            // 여기서는 NavigationManager를 초기화하지 않습니다.
            // NavigationManager는 MapboxNavigationObserver의 onAttached 콜백에서 초기화됩니다.

            Log.d("MainActivity", "Initialization after permission completed")
        } catch (e: Exception) {
            Log.e("MainActivity", "컴포넌트 초기화 오류", e)
            Toast.makeText(
                this,
                languageManager.getLocalizedString(
                    "내비게이션 초기화 중 오류가 발생했습니다: ${e.message}",
                    "Error during navigation initialization: ${e.message}"
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initializeMapClickListener() {
        Log.d("MainActivity", "Setting up map click listener")

        mapInitializer.setMapClickListener { point ->
            if (::navigationManager.isInitialized && !navigationManager.isNavigating()) {
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
        Log.d("MainActivity", "onDestroy called")

        // 각 매니저의 정리 메소드 호출
        if (::navigationManager.isInitialized) {
            navigationManager.cleanup()
        }

        // MapboxNavigationApp 비활성화
        MapboxNavigationApp.disable()

        super.onDestroy()
    }
}