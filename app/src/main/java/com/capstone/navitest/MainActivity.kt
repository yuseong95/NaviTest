package com.capstone.navitest

// UI 컴포넌트 import

// 프로젝트 내부 클래스들

// Mapbox 관련

//whisper

//lama

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.capstone.navitest.map.MapInitializer
import com.capstone.navitest.map.OfflineTileManager
import com.capstone.navitest.navigation.NavigationManager
import com.capstone.navitest.search.SearchButtonViewModel
import com.capstone.navitest.search.SearchManager
import com.capstone.navitest.search.SearchUI
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.ui.NavigationUI
import com.capstone.navitest.utils.PermissionHelper
import com.example.capstone_whisper.WhisperService
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
import com.quicinc.chatapp.GenieWrapper
import com.quicinc.chatapp.ModelInitializer
import com.quicinc.chatapp.StringCallback
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException


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

    //whisperService 생성
    private var whisperService: WhisperService? = null

    lateinit var searchButtonViewModel: SearchButtonViewModel

    // config path 설정
    private lateinit var configPath : String
    private lateinit var modelDir : String
    private lateinit var genieWrapper: GenieWrapper
    // MapboxNavigation delegate - 프로퍼티명 제거하여 "never used" 해결
    private val mapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                Log.d("MainActivity", "Navigation observer attached")

                if (!::navigationManager.isInitialized) {
                    try {
                        // NavigationManager 초기화 - 매개변수 수정
                        navigationManager = NavigationManager(
                            this@MainActivity,
                            lifecycleScope,
                            mapInitializer.getMapView(),
                            mapInitializer,
                            // tileStore 매개변수 제거됨
                            languageManager,
                            navigationUI
                        )

                        // UI와 네비게이션 매니저 연결
                        navigationUI.setNavigationManager(navigationManager)

                        // 지도 클릭 리스너 설정
                        initializeMapClickListener()

                        // 검색 관련 초기화
                        initializeSearchComponents()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "NavigationManager 초기화 오류", e)
                    }
                }

                // MapboxNavigation 인스턴스를 NavigationManager에 전달
                navigationManager.onNavigationAttached(mapboxNavigation)
                navigationManager.startNetworkMonitoring()
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

        // WhisperService 초기화 및 마이크 권한 요청
        setupWhisperService()


        // 모델 초기화 (정상 작동 버전) 라마 경로 찾기.
        try {
            configPath = ModelInitializer.initialize(this)
            Log.d("success","경로 초기화 성공")
        } catch (e: Exception) {
            Log.e("MainActivity", "모델 초기화 실패: ${e.message}")
        }
        modelDir = getExternalCacheDir()?.let {
            File(it, "models/llama3_2_3b").absolutePath
        } ?: throw IOException("External cache dir not found")
        genieWrapper = GenieWrapper(modelDir,configPath)

    }

    /*whisper-------------*/
    private fun setupWhisperService() {
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startWhisperService()
            } else {
                Toast.makeText(this, "🎤 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startWhisperService()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startWhisperService() {
        whisperService = WhisperService(this) { result ->
            runOnUiThread {
                // 텍스트 토스트로 보여주는 부분
                Toast.makeText(this, "📝 인식 결과: $result", Toast.LENGTH_LONG).show()
                Log.d("WhisperResult", result)

                if (::genieWrapper.isInitialized) {
                    genieWrapper.getResponseForPrompt(result, object : StringCallback {
                        override fun onNewString(response: String) {
                            runOnUiThread {
                                Log.d("LLaMA", "🦙 응답: $response")
                                Toast.makeText(this@MainActivity, "🦙 응답: $response", Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                } else {
                    Log.e("GenieWrapper", "❌ GenieWrapper not initialized!")
                }
            }
        }
        whisperService?.start()
    }
/*------------------------------------*/

    private fun observeViewModelState() {
        lifecycleScope.launch {
            // 검색 버튼 가시성 상태 관찰
            searchButtonViewModel.isSearchButtonVisible.collectLatest { isVisible ->
                Log.d("MainActivity", "Search button visibility changed: $isVisible")
                searchFab.visibility = if (isVisible) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            // 검색 UI 가시성 상태 관찰
            searchButtonViewModel.isSearchUIVisible.collectLatest { isVisible ->
                Log.d("MainActivity", "Search UI visibility changed: $isVisible")
                if (::searchUI.isInitialized) {
                    if (isVisible) {
                        searchUI.showSearchUI()
                    } else {
                        searchUI.hideSearchUI()
                    }
                }
            }
        }
    }

    private fun initializeSearchButton() {
        searchFab.setOnClickListener {
            Log.d("MainActivity", "Search FAB clicked")
            if (::searchUI.isInitialized) {
                searchButtonViewModel.openSearchUI()
            } else {
                Log.e("MainActivity", "SearchUI not initialized, trying to initialize now")
                if (::navigationManager.isInitialized) {
                    initializeSearchComponents()
                    if (::searchUI.isInitialized) {
                        searchButtonViewModel.openSearchUI()
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
            // SearchManager 초기화 - 매개변수 수정
            searchManager = SearchManager(this)

            // SearchUI 초기화 - 매개변수 수정
            searchUI = SearchUI(
                this,
                searchManager,
                languageManager,
                searchButtonViewModel
            )

            Log.d("MainActivity", "Search components initialized")
        } catch (e: Exception) {
            Log.e("MainActivity", "Search components initialization error", e)
        }
    }

    fun setDestinationFromSearch(point: Point) {
        Log.d("MainActivity", "Setting destination from search: ${point.longitude()}, ${point.latitude()}")

        if (::navigationManager.isInitialized) {
            navigationManager.setDestination(point)
            Log.d("MainActivity", "Destination set through NavigationManager")
        } else {
            Log.e("MainActivity", "NavigationManager not initialized, cannot set destination")

            val message = languageManager.getLocalizedString(
                "내비게이션이 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.",
                "Navigation is not ready yet. Please try again in a moment."
            )
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // isNavigationActive() 함수 제거 - 사용되지 않음
    // fun isNavigationActive(): Boolean {
    //     return searchButtonViewModel.navigationActive.value
    // }

    fun setNavigationActive(active: Boolean) {
        searchButtonViewModel.setNavigationActive(active)
    }

    fun setSearchButtonEnabled(enabled: Boolean) {
        if (::searchFab.isInitialized) {
            searchFab.isEnabled = enabled
            searchFab.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    override fun onResume() {
        super.onResume()
        if (::navigationManager.isInitialized) {
            navigationManager.checkNetworkStatus()
        }
    }

    private fun setupMapboxNavigation() {
        val tilePath = File(filesDir, "mbx-offline").absolutePath
        val tileStore = TileStore.create(tilePath)

        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()

        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .navigatorPredictionMillis(3000)
            .build()

        MapboxNavigationApp.setup(navOptions)
        Log.d("MainActivity", "MapboxNavigationApp setup completed")
    }

    private fun initializeManagers() {
        Log.d("MainActivity", "Initializing basic managers")

        languageManager = LanguageManager(this)
        permissionHelper = PermissionHelper(this, languageManager)
        mapInitializer = MapInitializer(this, languageManager)
        offlineTileManager = OfflineTileManager(
            this,
            mapInitializer.getTileStore(),
            languageManager
        )
        navigationUI = NavigationUI(this, languageManager)

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

            mapInitializer.initializeMap()

            val seoulLocation = Point.fromLngLat(126.978, 37.566)
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

        if (::searchManager.isInitialized) {
            searchManager.cleanup()
        }

        if (::navigationManager.isInitialized) {
            navigationManager.cleanup()
        }

        //whisperService 종료
        whisperService?.stop()

        MapboxNavigationApp.disable()
        super.onDestroy()
    }
}