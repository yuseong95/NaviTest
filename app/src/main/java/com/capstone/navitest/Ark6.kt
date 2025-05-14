package com.capstone.navitest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMapsOptions
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import java.io.File

class Ark6 : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val navigationLocationProvider = NavigationLocationProvider()
    private lateinit var tileStore: TileStore
    private lateinit var pointAnnotationManager: PointAnnotationManager

    // Variables to store the origin and destination points
    private var currentOrigin: Point? = null
    private var currentDestination: Point? = null

    // 경로 요청 디바운싱을 위한 변수
    private var lastRouteRequestTime = 0L
    private val ROUTE_REQUEST_DEBOUNCE_TIME = 3000 // 3초

    // Flag to track if we've already centered the camera on user's location
    private var hasInitializedCamera = false

    // UI components
    private lateinit var startNavigationButton: Button
    private lateinit var cancelButton: Button
    private lateinit var buttonLayout: LinearLayout

    // Track if navigation is active
    private var isNavigating = false

    // Activity result launcher for location permissions
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    initializeMapComponents()
                }
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                    initializeMapComponents()
                }
                else -> {
                    Toast.makeText(
                        this,
                        "Location permissions denied. Please enable permissions in settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a root view
        val rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

        // Initialize TileStore
        val tilePath = File(filesDir, "mbx-offline").absolutePath
        tileStore = TileStore.create(tilePath)

        // Check if tile regions already exist before downloading
        checkTileRegionExists()

        // Create the MapView first
        val mapInitOptions = MapInitOptions(context = this)
        mapView = MapView(this, mapInitOptions)

        // Add mapView to root layout
        rootLayout.addView(mapView)

        // Initialize and set up UI components
        setupUIComponents(rootLayout)

        // 권한 확인 후 맵 초기화가 진행되도록 수정
        checkLocationPermissions()
    }

    // 새로운 메서드: 권한 확인 로직을 분리
    private fun checkLocationPermissions() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are already granted
            initializeMapComponents()
        } else {
            // Request location permissions
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun setupUIComponents(rootLayout: FrameLayout) {
        // Create button layout
        buttonLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.BOTTOM or Gravity.END
            params.bottomMargin = (16 * resources.displayMetrics.density).toInt()
            params.rightMargin = (16 * resources.displayMetrics.density).toInt()
            layoutParams = params
        }

        // Create start navigation button
        startNavigationButton = Button(this).apply {
            text = "Start Navigation"
            isEnabled = false // Initially disabled until destination is selected
            setOnClickListener {
                startNavigation()
            }
        }

        // Create cancel button
        cancelButton = Button(this).apply {
            text = "Cancel Navigation"
            visibility = View.GONE
            setOnClickListener {
                cancelNavigation()
            }
        }

        // Add buttons to layout
        buttonLayout.addView(startNavigationButton)
        buttonLayout.addView(cancelButton)

        // Add button layout to root
        rootLayout.addView(buttonLayout)
    }

    private fun checkTileRegionExists() {
        val regionName = "seoul-incheon-gyeonggi"

        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regions = expected.value!!
                val regionExists = regions.any { it.id == regionName }

                if (!regionExists) {
                    // Download tiles only if the region doesn't exist
                    downloadOfflineTiles()
                } else {
                    Log.d("OfflineTiles", "Region $regionName already exists")
                }
            } else {
                // Error checking regions, try downloading anyway
                downloadOfflineTiles()
            }
        }
    }

    private fun initializeMapComponents() {
        MapboxMapsOptions.tileStore = tileStore

        // Set up map style without setting camera position
        mapView.mapboxMap.loadStyleUri(Style.MAPBOX_STREETS)

        // Initialize location puck
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = LocationPuck2D()
            enabled = true
        }

        // Set viewportDataSource, which tells the navigationCamera where to look
        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)

        // Set padding for the navigation camera
        val pixelDensity = this.resources.displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )

        // Initialize a NavigationCamera
        navigationCamera = NavigationCamera(mapView.mapboxMap, mapView.camera, viewportDataSource)

        // Initialize route line API and view for drawing the route on the map
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(this).build())

        // Initialize annotation manager for destination marker
        pointAnnotationManager = mapView.annotations.createPointAnnotationManager()

        // Set up map click listener to set destination
        mapView.mapboxMap.addOnMapClickListener { point ->
            // Only allow setting destination if not actively navigating
            if (!isNavigating) {
                setDestination(point)
            }
            true
        }
    }

    // Set destination point and add a marker
    private fun setDestination(point: Point) {
        // Update current destination
        currentDestination = point

        // Clear existing annotations
        pointAnnotationManager.deleteAll()

        // Create a bitmap from a drawable resource
        val bitmap = BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_mylocation)

        // Create a destination marker
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(point)
            .withIconImage(bitmap)
            .withIconAnchor(IconAnchor.BOTTOM)
            .withIconSize(1.5)

        // Add the annotation to the map
        pointAnnotationManager.create(pointAnnotationOptions)

        // Enable start navigation button if origin is also available
        if (currentOrigin != null) {
            startNavigationButton.isEnabled = true
            // Preview the route immediately
            requestRoute()
        }

        Toast.makeText(this, "Destination set", Toast.LENGTH_SHORT).show()
    }

    // Start navigation mode
    private fun startNavigation() {
        if (currentOrigin != null && currentDestination != null) {
            isNavigating = true

            // 운전 시점으로 카메라 줌/피치 오버라이드
            viewportDataSource.followingZoomPropertyOverride(30.0) // 운전 시 가까운 줌
            viewportDataSource.followingPitchPropertyOverride(70.0) // 3D 시점
            viewportDataSource.evaluate()

            // Update UI
            startNavigationButton.visibility = View.GONE
            cancelButton.visibility = View.VISIBLE

            // Request routes with latest origin and destination
            requestRoute()
        }
    }

    // Cancel navigation mode
    private fun cancelNavigation() {
        isNavigating = false

        // 카메라 오버라이드 해제
        viewportDataSource.followingZoomPropertyOverride(null)
        viewportDataSource.followingPitchPropertyOverride(null)
        viewportDataSource.evaluate()

        // Update UI
        cancelButton.visibility = View.GONE
        startNavigationButton.visibility = View.VISIBLE

        // Clear routes
        mapboxNavigation.setNavigationRoutes(emptyList())

        // Clear the destination marker
        pointAnnotationManager.deleteAll()
        currentDestination = null

        // Disable start button until new destination is selected
        startNavigationButton.isEnabled = false
    }

    // Routes observer draws a route line and origin/destination circles on the map
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            // Generate route geometries asynchronously and render them
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                mapView.mapboxMap.style?.apply { routeLineView.renderRouteDrawData(this, value) }
            }

            // Update viewportSourceData to include the new route
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()

            // Set the navigationCamera to OVERVIEW or FOLLOWING based on state
            if (isNavigating) {
                navigationCamera.requestNavigationCameraToFollowing()
            } else {
                navigationCamera.requestNavigationCameraToOverview()
            }
        }
    }

    // Location observer updates the location puck and camera to follow the user's location
    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation

            // Update our current origin point with the latest location
            currentOrigin = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)

            // Update location puck's position on the map
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            // Only move the camera to the user's location the first time we get a location
            if (!hasInitializedCamera) {
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude))
                        .zoom(15.0)
                        .build()
                )

                // Mark camera as initialized
                hasInitializedCamera = true

                Log.d("Navigation", "Camera initialized to user location: ${enhancedLocation.longitude}, ${enhancedLocation.latitude}")
            }

            // Update viewportDataSource to trigger camera to follow the location during navigation
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            // If actively navigating, update the route with current position - 하지만 너무 자주 요청하지 않도록 제한
            if (isNavigating && currentDestination != null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastRouteRequestTime > ROUTE_REQUEST_DEBOUNCE_TIME) {
                    requestRoute()
                    lastRouteRequestTime = currentTime
                }
            }

            // Enable start button if we have both origin and destination
            if (currentDestination != null && !isNavigating) {
                startNavigationButton.isEnabled = true
            }
        }
    }

    // Define MapboxNavigation
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                // Register observers
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)

                // Start trip session to get real location updates
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                // Clean up observers
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
            }
        },
        onInitialize = this::initNavigation
    )

    private fun downloadOfflineTiles() {
        // 서울/인천/경기도 지역을 대략적으로 커버하는 사각형 영역 생성
        val seoulAreaBounds = listOf(
            Point.fromLngLat(126.5, 37.3),  // 서쪽 하단 (범위 확장)
            Point.fromLngLat(127.5, 37.3),  // 동쪽 하단 (범위 확장)
            Point.fromLngLat(127.5, 38.0),  // 동쪽 상단 (범위 확장)
            Point.fromLngLat(126.5, 38.0),  // 서쪽 상단 (범위 확장)
            Point.fromLngLat(126.5, 37.3)   // 시작점으로 닫기 (범위 확장)
        )

        // 위 좌표로 Polygon 생성
        val regionGeometry = Polygon.fromLngLats(listOf(seoulAreaBounds))

        // OfflineManager 생성
        val offlineManager = OfflineManager()

        // tilesetDescriptor 생성
        val tilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.MAPBOX_STREETS) // 기본 스트리트 스타일로 변경
                .minZoom(6)  // 넓은 영역을 위한 최소 줌
                .maxZoom(16) // 상세 내비게이션을 위한 최대 줌
                .build()
        )

        // 내비게이션을 위한 추가 타일셋 - 이것이 중요합니다
        val navigationTilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/navigation-day-v1")
                .minZoom(6)
                .maxZoom(16)
                .build()
        )

        // 3. TileRegionLoadOptions 생성 - 두 타일셋 모두 포함
        val tileRegionLoadOptions = TileRegionLoadOptions.Builder()
            .geometry(regionGeometry)
            .acceptExpired(true)
            .descriptors(listOf(tilesetDescriptor, navigationTilesetDescriptor))
            .build()

        // 타일 리전 이름
        val regionName = "seoul-incheon-gyeonggi"

        // 진행 상황 모니터링 콜백
        val progressCallback: (TileRegionLoadProgress) -> Unit = { progress ->
            val percentage = if (progress.requiredResourceCount > 0) {
                (progress.completedResourceCount.toFloat() / progress.requiredResourceCount) * 100
            } else {
                0f
            }

            // 다운로드 진행률을 로그로 출력 (실제 앱에서는 UI에 표시할 수 있음)
            Log.d("OfflineTiles", "Download progress: ${percentage.toInt()}%")
        }

        // 타일 리전 다운로드
        tileStore.loadTileRegion(
            regionName,
            tileRegionLoadOptions,
            progressCallback
        ) { expected ->
            if (expected.isValue) {
                // 다운로드 성공
                val tileRegion = expected.value!!
                Log.d("OfflineTiles", "Download completed successfully! Region: ${tileRegion.id}")

                // 성공 메시지 표시
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "오프라인 지도 다운로드 완료",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // 다운로드 실패
                val error = expected.error!!
                Log.e("OfflineTiles", "Download failed: ${error.message}")

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "오프라인 지도 다운로드 실패: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Request route between current origin and destination
    private fun requestRoute() {
        val origin = currentOrigin ?: return
        val destination = currentDestination ?: return

        // 로그로 경로 요청 정보 기록
        Log.d("Navigation", "Requesting route from: ${origin.longitude()},${origin.latitude()} to: ${destination.longitude()},${destination.latitude()}")

        // 이 시점에서 lastRouteRequestTime 업데이트
        lastRouteRequestTime = System.currentTimeMillis()

        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(listOf(origin, destination))
                .layersList(listOf(mapboxNavigation.getZLevel(), null))
                .build(),
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    Log.d("Navigation", "Route request canceled")
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("Navigation", "Route request failed: ${reasons.firstOrNull()?.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@Ark6,
                            "경로를 찾을 수 없습니다: ${reasons.firstOrNull()?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    Log.d("Navigation", "Routes ready: ${routes.size} routes, origin: $routerOrigin")
                    mapboxNavigation.setNavigationRoutes(routes)
                }
            }
        )
    }

    // On initialization of MapboxNavigation
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun initNavigation() {
        // 1. 먼저 tileStore 기반 RoutingTilesOptions 만들기
        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()

        // 2. 위 옵션을 포함한 NavigationOptions 만들기 - timeouts 증가 (밀리초 단위)
        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .navigatorPredictionMillis(3000) // 기본값 보다 큰 값으로 설정
            .build()

        // 3. MapboxNavigationApp 초기화
        MapboxNavigationApp.setup(navOptions)

        // Initialize location puck
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck()
            enabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up MapboxNavigation to prevent memory leaks
        MapboxNavigationApp.disable()
    }
}