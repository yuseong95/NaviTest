package com.capstone.navitest.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.capstone.navitest.map.MapInitializer
import com.capstone.navitest.map.MarkerManager
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.ui.NavigationUI
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.common.TileStore
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeOfArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import kotlinx.coroutines.launch

class NavigationManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val mapView: MapView,
    private val mapInitializer: MapInitializer,
    private val tileStore: TileStore,
    private val languageManager: LanguageManager,
    private val navigationUI: NavigationUI
) : RouteManager.OnRouteChangeListener, LocationManager.OnLocationChangeListener {

    // 네비게이션 관련 변수들
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

    // 턴 바이 턴 안내를 위한 변수들
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi

    // 위치 관리자
    private lateinit var locationManager: LocationManager

    // 경로 관리자
    private lateinit var routeManager: RouteManager

    // 마커 관리자
    private lateinit var markerManager: MarkerManager

    // 네비게이션 상태
    private var isNavigating = false

    // 지연 초기화를 위한 private 백킹 필드
    private var _mapboxNavigation: MapboxNavigation? = null

    // 안전한 getter 제공
    private val mapboxNavigation: MapboxNavigation
        get() = _mapboxNavigation ?: throw IllegalStateException(
            "MapboxNavigation is not initialized. Call initializeNavigation() first."
        )

    init {
        try {
            // 기본 컴포넌트 초기화
            initializeNavigationComponents()

            // 네비게이션 초기화
            initializeNavigation()

            // 컴포넌트 초기화 후 경로 관리자 설정
            initializeRouteManager()

            // 맵 클릭 리스너 설정
            setupMapClickListener()
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error initializing navigation", e)
            throw IllegalStateException("Failed to initialize navigation: ${e.message}")
        }
    }

    private fun initializeNavigationComponents() {
        // 위치 관리자 초기화
        locationManager = LocationManager(context, mapView)
        locationManager.setLocationChangeListener(this)

        // 뷰포트 데이터 소스 초기화
        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)

        // 네비게이션 카메라 초기화
        initializeNavigationCamera()

        // 경로 라인 API 및 뷰 초기화
        initializeRouteLineComponents()

        // 마커 관리자 초기화
        markerManager = MarkerManager(
            context,
            mapInitializer.pointAnnotationManager,
            languageManager
        )

        // 턴 바이 턴 안내 컴포넌트 초기화
        initializeTurnByTurnComponents()
    }

    private fun initializeNavigationCamera() {
        // 패딩 설정
        val pixelDensity = context.resources.displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )

        // 네비게이션 카메라 초기화
        navigationCamera = NavigationCamera(
            mapView.mapboxMap,
            mapView.camera,
            viewportDataSource
        )

        // 제스처 핸들러 추가
        mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )

        // 카메라 상태 변경 관찰자 등록
        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            val showButton = when (navigationCameraState) {
                NavigationCameraState.TRANSITION_TO_FOLLOWING,
                NavigationCameraState.FOLLOWING -> false
                NavigationCameraState.TRANSITION_TO_OVERVIEW,
                NavigationCameraState.OVERVIEW,
                NavigationCameraState.IDLE -> true
            }
            navigationUI.setRecenterButtonVisibility(showButton)
        }
    }

    private fun initializeRouteLineComponents() {
        // 경로 라인 API 및 뷰 초기화
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(context).build()
        )
    }

    private fun initializeTurnByTurnComponents() {
        // 거리 포맷터 옵션
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(context).build()

        // 매뉴버 API 초기화
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)
        )

        // 트립 프로그레스 API 초기화
        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(context)
                .distanceRemainingFormatter(
                    DistanceRemainingFormatter(distanceFormatterOptions)
                )
                .timeRemainingFormatter(TimeRemainingFormatter(context))
                .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                .estimatedTimeOfArrivalFormatter(EstimatedTimeOfArrivalFormatter(context))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private fun initializeNavigation() {
        try {
            Log.d("NavigationManager", "MapboxNavigation 초기화 중...")

            // 1. 먼저 tileStore 기반 RoutingTilesOptions 만들기
            val routingTilesOptions = RoutingTilesOptions.Builder()
                .tileStore(tileStore)
                .build()

            // 2. 위 옵션을 포함한 NavigationOptions 만들기
            val navOptions = NavigationOptions.Builder(context)
                .routingTilesOptions(routingTilesOptions)
                .navigatorPredictionMillis(3000) // 기본값 보다 큰 값으로 설정
                .build()

            // 3. 명시적으로 MapboxNavigationApp을 설정
            if (!MapboxNavigationApp.isSetup()) {
                Log.d("NavigationManager", "MapboxNavigationApp 설정 중...")
                MapboxNavigationApp.setup(navOptions)
            }

            // 잠시 기다려서 초기화가 완료될 시간을 줌
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Log.w("NavigationManager", "초기화 대기 중 인터럽트됨", e)
            }

            // 내비게이션 인스턴스 가져오기 (최대 3번 시도)
            var navigation: MapboxNavigation? = null
            var attempt = 0

            while (navigation == null && attempt < 3) {
                navigation = MapboxNavigationApp.current()
                if (navigation == null) {
                    attempt++
                    Log.d("NavigationManager", "Navigation 인스턴스 시도 $attempt/3 실패, 다시 시도...")
                    try {
                        Thread.sleep(200) // 더 긴 대기 시간
                    } catch (e: InterruptedException) {
                        Log.w("NavigationManager", "재시도 대기 중 인터럽트됨", e)
                    }
                }
            }

            if (navigation != null) {
                _mapboxNavigation = navigation
                Log.d("NavigationManager", "MapboxNavigation이 성공적으로 초기화되었습니다")
            } else {
                throw IllegalStateException("MapboxNavigationApp.current()가 null을 반환했습니다")
            }

        } catch (e: Exception) {
            Log.e("NavigationManager", "MapboxNavigation 초기화 실패", e)
            throw IllegalStateException("MapboxNavigation 초기화 실패: ${e.message}")
        }
    }

    // 초기화 이후 별도로 호출하여 경로 관리자 및 관찰자 설정
    @SuppressLint("MissingPermission")
    private fun initializeRouteManager() {
        try {
            Log.d("NavigationManager", "Initializing RouteManager...")

            // 경로 관리자 초기화
            routeManager = RouteManager(context, mapboxNavigation, languageManager)
            routeManager.setRouteChangeListener(this)

            // 관찰자 등록
            registerObservers(mapboxNavigation)

            // 트립 세션 시작 - 권한 체크 추가
            if (PermissionsManager.areLocationPermissionsGranted(context)) {
                mapboxNavigation.startTripSession()
            } else {
                Log.w("NavigationManager", "Location permissions not granted, trip session not started")
            }

            Log.d("NavigationManager", "RouteManager initialized successfully")
        } catch (e: Exception) {
            Log.e("NavigationManager", "Failed to initialize RouteManager", e)
            throw IllegalStateException("Failed to initialize RouteManager: ${e.message}")
        }
    }

    private fun setupMapClickListener() {
        try {
            // gesture 플러그인을 사용하여 맵 클릭 리스너 설정
            mapView.gestures.addOnMapClickListener { point ->
                // 네비게이션 중이 아닐 때만 목적지 설정 가능
                if (!isNavigating) {
                    // 마커 추가 및 목적지 설정
                    val destination = markerManager.addMarker(point)
                    routeManager.setDestination(destination)

                    // 시작 버튼 활성화
                    navigationUI.setStartButtonEnabled(true)
                }
                true
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Failed to setup map click listener", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerObservers(mapboxNavigation: MapboxNavigation) {
        try {
            // 경로 관찰자 등록
            mapboxNavigation.registerRoutesObserver(routesObserver)

            // 위치 관찰자 등록
            mapboxNavigation.registerLocationObserver(locationManager.locationObserver)

            // 경로 진행 관찰자 등록
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        } catch (e: Exception) {
            Log.e("NavigationManager", "Failed to register observers", e)
            throw IllegalStateException("Failed to register navigation observers: ${e.message}")
        }
    }

    // 경로 관찰자
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            try {
                // 경로 라인 생성 및 렌더링
                routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                    mapView.mapboxMap.style?.apply {
                        routeLineView.renderRouteDrawData(this, value)
                    }
                }

                // 뷰포트 데이터 소스 경로 변경 알림
                viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
                viewportDataSource.evaluate()

                // 네비게이션 상태에 따라 카메라 위치 요청
                if (isNavigating) {
                    navigationCamera.requestNavigationCameraToFollowing()
                } else {
                    navigationCamera.requestNavigationCameraToOverview()
                }
            } catch (e: Exception) {
                Log.e("NavigationManager", "Error processing route update", e)
            }
        }
    }

    // 경로 진행 관찰자
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        try {
            // 턴 안내 정보 업데이트 - Expected 객체를 직접 전달
            val maneuversResult = maneuverApi.getManeuvers(routeProgress)
            navigationUI.updateManeuverView(maneuversResult)

            // 경로 진행 정보 업데이트
            val tripProgress = tripProgressApi.getTripProgress(routeProgress)
            navigationUI.updateTripProgressView(tripProgress)
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error processing route progress", e)
        }
    }

    // 네비게이션 시작
    fun startNavigation() {
        if (routeManager.hasValidRoute()) {
            isNavigating = true

            // 운전 시점으로 카메라 줌/피치 오버라이드
            viewportDataSource.followingZoomPropertyOverride(20.0) // 운전 시 가까운 줌
            viewportDataSource.followingPitchPropertyOverride(45.0) // 3D 시점
            viewportDataSource.evaluate()

            // UI 업데이트
            navigationUI.updateUIForNavigationStart()

            // 경로 재요청
            routeManager.requestRoute()
        }
    }

    // 네비게이션 취소
    fun cancelNavigation() {
        isNavigating = false

        // 카메라 오버라이드 해제
        viewportDataSource.followingZoomPropertyOverride(null)
        viewportDataSource.followingPitchPropertyOverride(null)
        viewportDataSource.evaluate()

        // UI 업데이트
        navigationUI.updateUIForNavigationCancel()

        // 경로 라인 명시적으로 지우기
        lifecycleScope.launch {
            routeLineApi.clearRouteLine { value ->
                mapView.mapboxMap.style?.let { style ->
                    routeLineView.renderClearRouteLineValue(style, value)
                }
            }
        }

        // 경로 및 마커 삭제
        routeManager.clearRoutes()
        markerManager.clearMarkers()
    }

    // 카메라 재중앙화
    fun recenterCamera() {
        navigationCamera.requestNavigationCameraToFollowing()
    }

    // 언어 변경 시 처리
    fun updateLanguage(newLanguage: String) {
        // 맵 스타일 다시 적용
        mapInitializer.applyMapStyle(newLanguage)

        // 경로가 있다면 경로 재요청
        if (routeManager.hasValidRoute()) {
            routeManager.requestRoute()
        }
    }

    // RouteManager.OnRouteChangeListener 구현
    override fun onRouteReady(routes: List<NavigationRoute>) {
        // 필요한 경우 추가 처리
    }

    override fun onRouteError(message: String) {
        // 필요한 경우 추가 처리
    }

    // LocationManager.OnLocationChangeListener 구현
    override fun onLocationChanged(location: Point) {
        try {
            // 현재 위치를 경로 관리자에 설정
            routeManager.setOrigin(location)

            // Location 객체 생성 - timestamp() 사용
            val enhancedLocation = Location.Builder()
                .longitude(location.longitude())
                .latitude(location.latitude())
                .timestamp(System.currentTimeMillis()) // time() -> timestamp()로 변경
                .build()

            // 뷰포트 데이터 소스 위치 변경 알림
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            // 네비게이션 중이고 목적지가 설정된 경우 주기적으로 경로 업데이트
            if (isNavigating && routeManager.getDestination() != null) {
                routeManager.requestRoute()
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error handling location change", e)
        }
    }


    // 네비게이션 상태 확인 메서드
    fun isNavigating(): Boolean {
        return isNavigating
    }

    // 목적지 설정 메서드
    fun setDestination(point: Point) {
        try {
            markerManager.addMarker(point)
            routeManager.setDestination(point)
            navigationUI.setStartButtonEnabled(true)
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error setting destination", e)
        }
    }

    // 정리 메소드
    fun cleanup() {
        try {
            // MapboxNavigationApp 비활성화
            MapboxNavigationApp.disable()
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error during cleanup", e)
        }
    }
}