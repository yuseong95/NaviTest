package com.capstone.navitest

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// 프로젝트 내부 클래스들
import com.capstone.navitest.map.MapInitializer
import com.capstone.navitest.map.OfflineTileManager
import com.capstone.navitest.navigation.NavigationManager
import com.capstone.navitest.search.SearchManager
import com.capstone.navitest.search.SearchUI
import com.capstone.navitest.search.SearchButtonViewModel
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.ui.NavigationUI
import com.capstone.navitest.utils.PermissionHelper
import com.capstone.navitest.utils.Constants

// Mapbox 관련
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
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

    // 검색 관련 클래스
    private lateinit var searchManager: SearchManager
    private lateinit var searchUI: SearchUI

    // 새로운 UI 컴포넌트들
    private lateinit var searchContainer: RelativeLayout
    private lateinit var searchOverlay: LinearLayout
    private lateinit var bottomNavigationContainer: RelativeLayout
    private lateinit var navigationModePanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var mainActionButton: Button

    // 네비게이션 탭들
    private lateinit var navHome: LinearLayout
    private lateinit var navNavigation: LinearLayout
    private lateinit var navSettings: LinearLayout

    // 현재 선택된 탭
    private var currentTab = NavigationTab.HOME

    enum class NavigationTab {
        HOME, NAVIGATION, SETTINGS
    }

    lateinit var searchButtonViewModel: SearchButtonViewModel

    // MapboxNavigation delegate
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

        // UI 컴포넌트 초기화
        initializeUIComponents()

        // 1단계: Mapbox 액세스 토큰 설정
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

        // 2단계: TileStore 글로벌 설정
        val globalTileStore = MapInitializer.setupGlobalTileStore(this)
        Log.d("MainActivity", "Global TileStore configured before MapboxNavigationApp setup")

        // 3단계: MapboxNavigationApp 설정
        setupMapboxNavigation()

        // 4단계: 기본 매니저 초기화 (여기서 languageManager 초기화됨)
        initializeManagers()

        // 5단계: ViewModel 상태 관찰 설정 (languageManager 초기화 후로 이동)
        observeViewModelState()

        // 6단계: 새로운 UI 이벤트 설정
        setupNewUIEvents()

        // 권한 확인 및 요청
        permissionHelper.checkLocationPermissions()

        // 검색 컴포넌트 초기화 시도
        if (::navigationManager.isInitialized) {
            initializeSearchComponents()
        }
    }

    private fun initializeUIComponents() {
        // 새로운 UI 컴포넌트들 참조
        searchContainer = findViewById(R.id.searchContainer)
        searchOverlay = findViewById(R.id.searchOverlay)
        bottomNavigationContainer = findViewById(R.id.bottomNavigationContainer)
        navigationModePanel = findViewById(R.id.navigationModePanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        mainActionButton = findViewById(R.id.mainActionButton)

        // 네비게이션 탭들
        navHome = findViewById(R.id.navHome)
        navNavigation = findViewById(R.id.navNavigation)
        navSettings = findViewById(R.id.navSettings)

        // 초기 상태 설정
        mainActionButton.visibility = View.GONE
        searchOverlay.visibility = View.GONE
        navigationModePanel.visibility = View.GONE
        settingsPanel.visibility = View.GONE

        // 기본 탭 선택
        selectTab(NavigationTab.HOME)
    }

    private fun setupNewUIEvents() {
        // 검색 컨테이너 클릭 이벤트
        searchContainer.setOnClickListener {
            Log.d("MainActivity", "Search container clicked")
            if (::searchUI.isInitialized) {
                searchButtonViewModel.openSearchUI()
            }
        }

        // 네비게이션 탭 클릭 이벤트들
        navHome.setOnClickListener {
            selectTab(NavigationTab.HOME)
        }

        navNavigation.setOnClickListener {
            selectTab(NavigationTab.NAVIGATION)
        }

        navSettings.setOnClickListener {
            selectTab(NavigationTab.SETTINGS)
        }

        // 메인 액션 버튼 클릭 이벤트
        mainActionButton.setOnClickListener {
            if (::navigationManager.isInitialized) {
                if (navigationManager.isNavigating()) {
                    navigationManager.cancelNavigation()
                } else {
                    navigationManager.startNavigation()
                }
            }
        }

        // 내비게이션 모드 패널 옵션들
        setupNavigationModeOptions()

        // 설정 패널 옵션들
        setupSettingsOptions()
    }

    private fun setupNavigationModeOptions() {
        val drivingOption = findViewById<RelativeLayout>(R.id.drivingModeOption)
        val walkingOption = findViewById<RelativeLayout>(R.id.walkingModeOption)
        val cyclingOption = findViewById<RelativeLayout>(R.id.cyclingModeOption)

        drivingOption.setOnClickListener {
            changeNavigationMode(Constants.NAVIGATION_MODE_DRIVING)
        }

        walkingOption.setOnClickListener {
            changeNavigationMode(Constants.NAVIGATION_MODE_WALKING)
        }

        cyclingOption.setOnClickListener {
            changeNavigationMode(Constants.NAVIGATION_MODE_CYCLING)
        }
    }

    private fun setupSettingsOptions() {
        val koreanOption = findViewById<RelativeLayout>(R.id.koreanLanguageOption)
        val englishOption = findViewById<RelativeLayout>(R.id.englishLanguageOption)
        val offlineMapOption = findViewById<RelativeLayout>(R.id.offlineMapOption)
        val appInfoOption = findViewById<RelativeLayout>(R.id.appInfoOption)

        koreanOption.setOnClickListener {
            changeLanguage(Constants.LANG_KOREAN)
        }

        englishOption.setOnClickListener {
            changeLanguage(Constants.LANG_ENGLISH)
        }

        offlineMapOption.setOnClickListener {
            // 오프라인 지도 관리 화면으로 이동 (추후 구현)
            Toast.makeText(this, "오프라인 지도 관리 기능은 추후 구현 예정입니다", Toast.LENGTH_SHORT).show()
        }

        appInfoOption.setOnClickListener {
            // 앱 정보 화면으로 이동 (추후 구현)
            Toast.makeText(this, "앱 정보 화면은 추후 구현 예정입니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeNavigationMode(mode: String) {
        if (::languageManager.isInitialized && languageManager.changeNavigationMode(mode)) {
            updateNavigationModeUI(mode)

            if (::navigationManager.isInitialized) {
                navigationManager.getRouteManager().changeNavigationMode(mode)
            }

            val modeDisplayName = languageManager.getNavigationModeDisplayName(mode)
            Toast.makeText(this, "내비게이션 모드: $modeDisplayName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeLanguage(language: String) {
        if (::languageManager.isInitialized && languageManager.changeLanguage(language)) {
            updateLanguageUI(language)

            if (::navigationManager.isInitialized) {
                navigationManager.updateLanguage(language)
            }

            val message = if (language == Constants.LANG_KOREAN) {
                "언어가 한국어로 변경되었습니다"
            } else {
                "Language changed to English"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNavigationModeUI(mode: String) {
        val drivingCheck = findViewById<View>(R.id.drivingCheck)
        val walkingCheck = findViewById<View>(R.id.walkingCheck)
        val cyclingCheck = findViewById<View>(R.id.cyclingCheck)

        // 모든 체크 숨기기
        drivingCheck.visibility = View.GONE
        walkingCheck.visibility = View.GONE
        cyclingCheck.visibility = View.GONE

        // 선택된 모드만 체크 표시
        when (mode) {
            Constants.NAVIGATION_MODE_DRIVING -> drivingCheck.visibility = View.VISIBLE
            Constants.NAVIGATION_MODE_WALKING -> walkingCheck.visibility = View.VISIBLE
            Constants.NAVIGATION_MODE_CYCLING -> cyclingCheck.visibility = View.VISIBLE
        }
    }

    private fun updateLanguageUI(language: String) {
        val koreanCheck = findViewById<View>(R.id.koreanCheck)
        val englishCheck = findViewById<View>(R.id.englishCheck)

        if (language == Constants.LANG_KOREAN) {
            koreanCheck.visibility = View.VISIBLE
            englishCheck.visibility = View.GONE
        } else {
            koreanCheck.visibility = View.GONE
            englishCheck.visibility = View.VISIBLE
        }

        // UI 텍스트들 업데이트
        updateUITexts()
    }

    private fun updateUITexts() {
        if (!::languageManager.isInitialized) return

        val searchHint = findViewById<TextView>(R.id.searchHint)
        searchHint.text = languageManager.getLocalizedString("목적지 검색", "Search destination")

        mainActionButton.text = if (::navigationManager.isInitialized && navigationManager.isNavigating()) {
            languageManager.getLocalizedString("내비게이션 취소", "Cancel Navigation")
        } else {
            languageManager.getLocalizedString("내비게이션 시작", "Start Navigation")
        }
    }

    private fun selectTab(tab: NavigationTab) {
        // 이전 패널들 숨기기
        hideAllPanels()

        // 모든 탭 비활성화
        updateTabAppearance(navHome, false)
        updateTabAppearance(navNavigation, false)
        updateTabAppearance(navSettings, false)

        // 선택된 탭 활성화 및 해당 패널 표시
        currentTab = tab
        when (tab) {
            NavigationTab.HOME -> {
                updateTabAppearance(navHome, true)
                // 홈 탭에서는 추가 패널이 없음
            }
            NavigationTab.NAVIGATION -> {
                updateTabAppearance(navNavigation, true)
                showPanel(navigationModePanel)
            }
            NavigationTab.SETTINGS -> {
                updateTabAppearance(navSettings, true)
                showPanel(settingsPanel)
            }
        }
    }

    private fun updateTabAppearance(tabView: LinearLayout, isActive: Boolean) {
        val iconView = tabView.getChildAt(0) as android.widget.ImageView
        val textView = tabView.getChildAt(1) as TextView

        if (isActive) {
            iconView.setColorFilter(getColor(R.color.nav_icon_active))
            textView.setTextColor(getColor(R.color.nav_text_active))
        } else {
            iconView.setColorFilter(getColor(R.color.nav_icon_inactive))
            textView.setTextColor(getColor(R.color.nav_text_inactive))
        }
    }

    private fun hideAllPanels() {
        hidePanel(navigationModePanel)
        hidePanel(settingsPanel)
    }

    private fun showPanel(panel: LinearLayout) {
        panel.visibility = View.VISIBLE

        // 슬라이드 업 애니메이션
        val animator = ObjectAnimator.ofFloat(panel, "translationY", 400f, 0f)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    private fun hidePanel(panel: LinearLayout) {
        if (panel.visibility == View.VISIBLE) {
            // 슬라이드 다운 애니메이션
            val animator = ObjectAnimator.ofFloat(panel, "translationY", 0f, 400f)
            animator.duration = 300
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                if (animation.animatedFraction == 1.0f) {
                    panel.visibility = View.GONE
                }
            }
            animator.start()
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            // 검색 UI 가시성 상태 관찰
            searchButtonViewModel.isSearchUIVisible.collectLatest { isVisible ->
                Log.d("MainActivity", "Search UI visibility changed: $isVisible")
                if (::searchUI.isInitialized) {
                    if (isVisible) {
                        searchUI.showSearchUI()
                        searchOverlay.visibility = View.VISIBLE
                    } else {
                        searchUI.hideSearchUI()
                        searchOverlay.visibility = View.GONE
                    }
                }
            }
        }

        lifecycleScope.launch {
            // 내비게이션 상태 관찰
            searchButtonViewModel.navigationActive.collectLatest { isActive ->
                Log.d("MainActivity", "Navigation active state changed: $isActive")
                updateNavigationUI(isActive)
            }
        }
    }

    private fun updateNavigationUI(isNavigating: Boolean) {
        if (!::languageManager.isInitialized) {
            Log.w("MainActivity", "updateNavigationUI called before languageManager initialization")
            return
        }

        if (isNavigating) {
            // 내비게이션 시작시
            mainActionButton.text = languageManager.getLocalizedString("내비게이션 취소", "Cancel Navigation")
            mainActionButton.visibility = View.VISIBLE

            // 하단 네비게이션 바 숨기기 (내비게이션 중에는 모드 변경 불가)
            bottomNavigationContainer.visibility = View.GONE

            // 모든 패널 숨기기
            hideAllPanels()
        } else {
            // 내비게이션 종료시
            mainActionButton.text = languageManager.getLocalizedString("내비게이션 시작", "Start Navigation")

            // 하단 네비게이션 바 다시 표시
            bottomNavigationContainer.visibility = View.VISIBLE

            // 목적지가 설정되어 있으면 시작 버튼 표시
            if (::navigationManager.isInitialized &&
                navigationManager.getRouteManager().hasValidRoute()) {
                mainActionButton.visibility = View.VISIBLE
            } else {
                mainActionButton.visibility = View.GONE
            }
        }
    }

    // 기존 메서드들...
    private fun initializeSearchComponents() {
        try {
            searchManager = SearchManager(this)
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
            mainActionButton.visibility = View.VISIBLE
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

    fun setNavigationActive(active: Boolean) {
        searchButtonViewModel.setNavigationActive(active)
    }

    override fun onResume() {
        super.onResume()
        if (::navigationManager.isInitialized) {
            navigationManager.checkNetworkStatus()
        }
    }

    private fun setupMapboxNavigation() {
        val tilePath = File(filesDir, "mbx-offline").absolutePath
        val tileStore = com.mapbox.common.TileStore.create(tilePath)

        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()

        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .navigatorPredictionMillis(3000)
            .build()

        MapboxNavigationApp.setup(navOptions)
        Log.d("MainActivity", "MapboxNavigationApp setup completed with pre-configured TileStore")
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
                mainActionButton.visibility = View.VISIBLE
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

    fun setSearchButtonEnabled(enabled: Boolean) {
        // 새로운 UI에서는 검색 컨테이너의 활성화/비활성화를 제어
        if (::searchContainer.isInitialized) {
            searchContainer.isEnabled = enabled
            searchContainer.alpha = if (enabled) 1.0f else 0.5f

            // 검색 힌트 텍스트도 업데이트
            val searchHint = findViewById<TextView>(R.id.searchHint)
            if (enabled) {
                searchHint.text = languageManager.getLocalizedString("목적지 검색", "Search destination")
                searchHint.setTextColor(getColor(R.color.hint_text_color))
            } else {
                searchHint.text = languageManager.getLocalizedString("오프라인 모드", "Offline mode")
                searchHint.setTextColor(getColor(R.color.offline_status))
            }
        }
    }

    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy called")

        if (::searchManager.isInitialized) {
            searchManager.cleanup()
        }

        if (::navigationManager.isInitialized) {
            navigationManager.cleanup()
        }

        MapboxNavigationApp.disable()
        super.onDestroy()
    }
}