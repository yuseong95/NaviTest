package com.capstone.navitest.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.capstone.navitest.map.MapInitializer
import com.capstone.navitest.map.MarkerManager
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.ui.NavigationUI
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
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
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
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

    private val navigationLocationProvider = NavigationLocationProvider()

    // 네비게이션 관련 변수들
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

    // 턴 바이 턴 안내를 위한 변수들
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi

    // 위치 관리자와 마커 관리자
    private lateinit var locationManager: LocationManager
    private lateinit var routeManager: RouteManager
    private lateinit var markerManager: MarkerManager

    // 네비게이션 상태
    private var isNavigating = false

    // MapboxNavigation 인스턴스는 MainActivity에서 전달받음
    private lateinit var mapboxNavigation: MapboxNavigation

    // 카메라 초기화 추적
    private var hasInitializedCamera = false

    init {
        try {
            Log.d("NavigationManager", "Initializing navigation components")

            // 위치 관리자 초기화
            locationManager = LocationManager(context, mapView)

            // 네비게이션 컴포넌트 초기화
            initializeNavigationComponents()

            // 마커 관리자 초기화
            markerManager = MarkerManager(
                context,
                mapInitializer.pointAnnotationManager,
                languageManager
            )

            // 위치 변경 리스너 설정
            locationManager.setLocationChangeListener(this)

            // 맵 클릭 리스너 설정
            setupMapClickListener()

            Log.d("NavigationManager", "Navigation manager initialization phase 1 completed")
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error initializing navigation components", e)
            throw IllegalStateException("Failed to initialize navigation components: ${e.message}")
        }
    }

    // MapboxNavigation 설정 메서드
    fun setMapboxNavigation(navigation: MapboxNavigation) {
        Log.d("NavigationManager", "Setting MapboxNavigation instance")

        if (!::mapboxNavigation.isInitialized) {
            mapboxNavigation = navigation

            // 경로 관리자 초기화 - MapboxNavigation이 설정된 후에 초기화
            routeManager = RouteManager(context, mapboxNavigation, languageManager)
            routeManager.setRouteChangeListener(this)

            Log.d("NavigationManager", "Navigation manager initialization completed")
        }
    }

    private fun initializeNavigationComponents() {
        Log.d("NavigationManager", "Initializing navigation components")

        // 뷰포트 데이터 소스 초기화
        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)

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

        // 경로 라인 API 및 뷰 초기화
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(context).build()
        )

        // 턴 바이 턴 안내 컴포넌트 초기화
        initializeTurnByTurnComponents()

        // 위치 컴포넌트 초기화
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = createDefault2DPuck()
            enabled = true
        }

        Log.d("NavigationManager", "Navigation components initialized")
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

    private fun setupMapClickListener() {
        try {
            // gesture 플러그인을 사용하여 맵 클릭 리스너 설정
            mapView.gestures.addOnMapClickListener { point ->
                // 네비게이션 중이 아닐 때만 목적지 설정 가능
                if (!isNavigating && ::mapboxNavigation.isInitialized) {
                    // 마커 추가 및 목적지 설정
                    val destination = markerManager.addMarker(point)
                    routeManager.setDestination(destination)

                    // 시작 버튼 활성화
                    navigationUI.setStartButtonEnabled(true)
                }
                true
            }

            Log.d("NavigationManager", "Map click listener set up")
        } catch (e: Exception) {
            Log.e("NavigationManager", "Failed to setup map click listener", e)
        }
    }

    // 관찰자 등록/해제 메서드 - MainActivity의 옵저버에서 호출됨
    @SuppressLint("MissingPermission")
    fun onNavigationAttached(mapboxNavigation: MapboxNavigation) {
        Log.d("NavigationManager", "Navigation attached")

        // MapboxNavigation 인스턴스 설정 - 코드 중복 제거를 위해 setMapboxNavigation() 호출
        setMapboxNavigation(mapboxNavigation)

        // 관찰자 등록 - setMapboxNavigation()에서 하지 않은 추가 작업들만 여기서 수행
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)

        // 트립 세션 시작 - 위치 권한이 필요함
        mapboxNavigation.startTripSession()
    }

    fun onNavigationDetached(mapboxNavigation: MapboxNavigation) {
        Log.d("NavigationManager", "Navigation detached")

        // 관찰자 해제
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
    }

    // 경로 관찰자
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            Log.d("NavigationManager", "Routes updated: ${routeUpdateResult.navigationRoutes.size} routes")

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

    // 위치 관찰자
    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {
            // Raw 위치는 사용하지 않음
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation

            // 현재 위치 포인트 생성
            val currentLocation = Point.fromLngLat(
                enhancedLocation.longitude,
                enhancedLocation.latitude
            )

            // 로그 추가
            Log.d("NavigationManager", "New location: ${enhancedLocation.longitude}, ${enhancedLocation.latitude}")

            // 위치 관리자에 위치 설정 (이 부분이 중요)
            locationManager.updateCurrentLocation(currentLocation)

            // 네비게이션 위치 제공자 업데이트
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            // 뷰포트 업데이트
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            // 첫 위치 수신 시 카메라 위치 설정 - 이중 안전장치
            if (!hasInitializedCamera) {
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(currentLocation)
                        .zoom(15.0)
                        .build()
                )
                hasInitializedCamera = true
                Log.d("NavigationManager", "Camera initialized to user location in NavigationManager")
            }
        }
    }

    // 경로 진행 관찰자
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        try {
            // 턴 안내 정보 업데이트
            val maneuvers = maneuverApi.getManeuvers(routeProgress)
            navigationUI.updateManeuverView(maneuvers)

            // 경로 진행 정보 업데이트
            val tripProgress = tripProgressApi.getTripProgress(routeProgress)
            navigationUI.updateTripProgressView(tripProgress)
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error processing route progress", e)
        }
    }

    // 네비게이션 시작
    fun startNavigation() {
        if (::routeManager.isInitialized && routeManager.hasValidRoute()) {
            Log.d("NavigationManager", "Starting navigation")

            isNavigating = true

            // 운전 시점으로 카메라 줌/피치 오버라이드
            viewportDataSource.followingZoomPropertyOverride(20.0) // 운전 시 가까운 줌
            viewportDataSource.followingPitchPropertyOverride(45.0) // 3D 시점
            viewportDataSource.evaluate()

            // UI 업데이트
            navigationUI.updateUIForNavigationStart()

            // 경로 재요청
            routeManager.requestRoute()

            Log.d("NavigationManager", "Navigation started")
        }
    }

    // 네비게이션 취소
    fun cancelNavigation() {
        Log.d("NavigationManager", "Canceling navigation")

        isNavigating = false

        // 카메라 오버라이드 해제
        viewportDataSource.followingZoomPropertyOverride(null)
        viewportDataSource.followingPitchPropertyOverride(null)
        viewportDataSource.evaluate()

        // UI 업데이트
        navigationUI.updateUIForNavigationCancel()

        // 경로 라인 명시적으로 지우기
        if (::routeLineApi.isInitialized) {
            lifecycleScope.launch {
                routeLineApi.clearRouteLine { value ->
                    mapView.mapboxMap.style?.let { style ->
                        routeLineView.renderClearRouteLineValue(style, value)
                    }
                }
            }
        }

        // 경로 및 마커 삭제
        if (::routeManager.isInitialized) {
            routeManager.clearRoutes()
        }

        if (::markerManager.isInitialized) {
            markerManager.clearMarkers()
        }

        Log.d("NavigationManager", "Navigation canceled")
    }

    // 카메라 재중앙화
    fun recenterCamera() {
        Log.d("NavigationManager", "Recentering camera")
        navigationCamera.requestNavigationCameraToFollowing()
    }

    // 언어 변경 시 처리
    fun updateLanguage(newLanguage: String) {
        Log.d("NavigationManager", "Updating language to: $newLanguage")

        // 맵 스타일 다시 적용
        mapInitializer.applyMapStyle(newLanguage)

        // 경로가 있다면 경로 재요청
        if (::routeManager.isInitialized && routeManager.hasValidRoute()) {
            routeManager.requestRoute()
        }
    }

    // RouteManager.OnRouteChangeListener 구현
    override fun onRouteReady(routes: List<NavigationRoute>) {
        Log.d("NavigationManager", "Routes ready: ${routes.size}")
        // 이미 routesObserver에서 처리하므로 여기서는 추가 작업 불필요
    }

    override fun onRouteError(message: String) {
        Log.e("NavigationManager", "Route error: $message")
        // 필요 시 추가 처리 가능
    }

    // LocationManager.OnLocationChangeListener 구현
    override fun onLocationChanged(location: Point) {
        try {
            // 현재 위치를 경로 관리자에 설정
            if (::routeManager.isInitialized) {
                routeManager.setOrigin(location)

                // 네비게이션 중이고 목적지가 설정된 경우 주기적으로 경로 업데이트
                if (isNavigating && routeManager.getDestination() != null) {
                    routeManager.requestRoute()
                }
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
            Log.d("NavigationManager", "Setting destination")

            if (::markerManager.isInitialized && ::routeManager.isInitialized) {
                markerManager.addMarker(point)
                routeManager.setDestination(point)
                navigationUI.setStartButtonEnabled(true)
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error setting destination", e)
        }
    }

    // 정리 메소드
    fun cleanup() {
        try {
            Log.d("NavigationManager", "Cleaning up navigation resources")
            // NavigationManager에서는 아무것도 정리하지 않음
            // MainActivity에서 MapboxNavigationApp.disable() 호출
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error during cleanup", e)
        }
    }
}