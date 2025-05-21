package com.capstone.navitest

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.capstone.navitest.map.MapInitializer
import com.capstone.navitest.map.OfflineTileManager
import com.capstone.navitest.navigation.NavigationManager
import com.capstone.navitest.search.SearchManager
import com.capstone.navitest.search.SearchUI
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.ui.NavigationUI
import com.capstone.navitest.utils.PermissionHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import java.io.File
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.capstone.navitest.search.SearchButtonViewModel

class MainActivity : ComponentActivity() {
    // 필요한 매니저 클래스들을 선언
    private lateinit var mapInitializer: MapInitializer
    private lateinit var navigationManager: NavigationManager
    private lateinit var offlineTileManager: OfflineTileManager
    private lateinit var navigationUI: NavigationUI
    private lateinit var languageManager: LanguageManager
    private lateinit var permissionHelper: PermissionHelper

    // 검색 관련 클래스 추가
    private lateinit var searchManager: SearchManager
    private lateinit var searchUI: SearchUI

    // 검색 버튼 참조
    private lateinit var searchFab: FloatingActionButton

    private lateinit var searchButtonViewModel: SearchButtonViewModel

    // MapboxNavigation 정의
    private val mapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                Log.d("MainActivity", "Navigation observer attached")

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

                        // 지도 클릭 리스너 설정
                        initializeMapClickListener()

                        // 검색 관련 초기화 - 이 시점에서 RouteManager 사용 가능
                        initializeSearchComponents()
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

        // ViewModel 초기화
        searchButtonViewModel = ViewModelProvider(this)[SearchButtonViewModel::class.java]

        // 먼저 searchFab 초기화
        searchFab = findViewById(R.id.searchFab)

        // ViewModel 상태 관찰 설정
        observeViewModelState()

        // Mapbox 액세스 토큰 설정
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

        // MapboxNavigationApp 설정
        setupMapboxNavigation()

        // 기본 매니저 초기화
        initializeManagers()

        // 검색 버튼 초기화
        initializeSearchButton()

        // 권한 확인 및 요청
        permissionHelper.checkLocationPermissions()

        // 검색 컴포넌트 초기화 시도
        if (::navigationManager.isInitialized) {
            initializeSearchComponents()
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            // 검색 버튼 가시성 상태 관찰
            searchButtonViewModel.isVisible.collectLatest { isVisible ->
                searchFab.visibility = if (isVisible) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            // 검색 UI 가시성 상태 관찰
            searchButtonViewModel.isSearchUIVisible.collectLatest { isVisible ->
                // searchContainer는 SearchUI 클래스에서 관리하도록 수정
                if (::searchUI.isInitialized) {
                    if (isVisible) searchUI.showSearchUI() else searchUI.hideSearchUI()
                }
            }
        }
    }

    private fun initializeSearchButton() {
        searchFab = findViewById(R.id.searchFab)
        searchFab.setOnClickListener {
            if (::searchUI.isInitialized) {
                Log.d("MainActivity", "searchUI is initialized, showing UI")
                searchUI.showSearchUI()
            } else {
                Log.e("MainActivity", "SearchUI not initialized, trying to initialize now")
                if (::navigationManager.isInitialized) {
                    initializeSearchComponents()
                    if (::searchUI.isInitialized) {
                        searchUI.showSearchUI()
                    } else {
                        Log.e("MainActivity", "Still could not initialize SearchUI")
                    }
                } else {
                    Log.e("MainActivity", "NavigationManager not initialized yet")
                }
            }
        }
    }

    private fun initializeSearchComponents() {
        try {
            // 검색 매니저 초기화
            searchManager = SearchManager(
                this,
                navigationManager.getRouteManager(),
                languageManager
            )

            // 검색 UI 초기화 - ViewModel 전달
            searchUI = SearchUI(
                this,
                searchManager,
                languageManager,
                navigationManager.getMarkerManager(),
                searchButtonViewModel  // ViewModel 전달
            )

            // 검색 버튼 클릭 이벤트 수정
            searchFab.setOnClickListener {
                searchButtonViewModel.openSearchUI()  // ViewModel 메소드 호출
            }

            Log.d("MainActivity", "Search components initialized")
        } catch (e: Exception) {
            Log.e("MainActivity", "Search components initialization error", e)
        }
    }

    // 내비게이션 상태 확인 메소드 추가
    fun isNavigationActive(): Boolean {
        return searchButtonViewModel.navigationActive.value
    }

    // 메소드 수정: 네비게이션 상태 설정
    fun setNavigationActive(active: Boolean) {
        searchButtonViewModel.setNavigationActive(active)
    }

    // 기존 메소드 수정: 검색 버튼 활성화 상태 설정
    fun setSearchButtonEnabled(enabled: Boolean) {
        if (::searchFab.isInitialized) {
            searchFab.isEnabled = enabled
            searchFab.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면이 포그라운드로 돌아올 때마다 네트워크 상태 확인
        if (::navigationManager.isInitialized) {
            navigationManager.checkNetworkStatus()
        }
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

            // 명시적인 카메라 위치 설정 추가
            val seoulLocation = Point.fromLngLat(126.978, 37.566) // 서울 시청 좌표(임시로 사용)
            mapInitializer.getMapView().mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(seoulLocation)
                    .zoom(15.0)
                    .build()
            )

            Log.d("MainActivity", "Initial camera position set")

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

        // 검색 매니저 정리
        if (::searchManager.isInitialized) {
            searchManager.cleanup()
        }

        // 기존 매니저 정리
        if (::navigationManager.isInitialized) {
            navigationManager.cleanup()
        }

        // MapboxNavigationApp 비활성화
        MapboxNavigationApp.disable()

        super.onDestroy()
    }
}