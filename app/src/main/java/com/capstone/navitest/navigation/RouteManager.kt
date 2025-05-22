package com.capstone.navitest.navigation

import android.content.Context
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import com.capstone.navitest.ui.LanguageManager
import com.capstone.navitest.utils.Constants
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import android.net.ConnectivityManager

class RouteManager(
    private val context: Context,
    private val mapboxNavigation: MapboxNavigation,
    private val languageManager: LanguageManager
) {
    // 현재 출발지와 목적지
    private var currentOrigin: Point? = null
    private var currentDestination: Point? = null

    // 경로 요청 디바운싱을 위한 변수
    private var lastRouteRequestTime = 0L

    // 경로 정보 캐싱 지원을 위한 필드
    private var cachedRoutes: List<NavigationRoute>? = null

    // 경로 변경 리스너 인터페이스
    interface OnRouteChangeListener {
        fun onRouteReady(routes: List<NavigationRoute>)
        fun onRouteError(message: String)
    }

    // 경로 변경 리스너
    private var routeChangeListener: OnRouteChangeListener? = null

    fun setOrigin(origin: Point) {
        currentOrigin = origin

        // 출발지와 목적지가 모두 설정되었다면 경로 요청
        if (currentDestination != null) {
            requestRoute()
        }
    }

    fun setDestination(destination: Point) {
        Log.d("RouteManager", "Setting destination: ${destination.longitude()}, ${destination.latitude()}")

        currentDestination = destination

        // 현재 위치가 있는지 확인하고 경로 요청
        if (currentOrigin != null) {
            Log.d("RouteManager", "Origin available, requesting route")
            requestRoute()
        } else {
            Log.d("RouteManager", "Origin not available yet, waiting for location")
        }
    }

    fun setRouteChangeListener(listener: OnRouteChangeListener) {
        routeChangeListener = listener
    }

    fun requestRoute() {
        val origin = currentOrigin ?: return
        val destination = currentDestination ?: return

        // 1. 네트워크 연결 확인 - deprecated API 제거
        val isNetworkAvailable = isNetworkConnected()

        // 2. 오프라인 상태이고 캐시된 경로가 있으면 사용
        if (!isNetworkAvailable) {
            if (useCachedRoutesIfAvailable()) {
                val message = languageManager.getLocalizedString(
                    "오프라인 모드: 캐시된 경로 사용",
                    "Offline mode: Using cached route"
                )
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return
            } else {
                val message = languageManager.getLocalizedString(
                    "오프라인 모드: 새 경로를 계산할 수 없습니다. 인터넷 연결이 필요합니다.",
                    "Offline mode: Cannot calculate new route. Internet connection required."
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return
            }
        }

        // 3. 디바운싱 체크
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRouteRequestTime < Constants.ROUTE_REQUEST_DEBOUNCE_TIME) {
            Log.d("RouteManager", "Route request debounced")
            return
        }

        lastRouteRequestTime = currentTime

        // 4. 온라인 경로 요청
        performOnlineRouteRequest(origin, destination)
    }

    // 네트워크 연결 상태 확인 - deprecated API 제거
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // minSdk 33이므로 VERSION.SDK_INT 체크 불필요
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 온라인 경로 요청 수행
    private fun performOnlineRouteRequest(origin: Point, destination: Point) {
        Log.d("RouteManager", "Requesting route from: ${origin.longitude()},${origin.latitude()} to: ${destination.longitude()},${destination.latitude()}")

        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .coordinatesList(listOf(origin, destination))
            .layersList(listOf(mapboxNavigation.getZLevel(), null))
            .language(languageManager.currentLanguage)
            .steps(true)
            .build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    Log.d("RouteManager", "Route request canceled")
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("RouteManager", "Route request failed: ${reasons.firstOrNull()?.message}")

                    val message = languageManager.getLocalizedString(
                        "경로를 찾을 수 없습니다: ${reasons.firstOrNull()?.message}",
                        "Could not find route: ${reasons.firstOrNull()?.message}"
                    )

                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    routeChangeListener?.onRouteError(message)
                }

                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    Log.d("RouteManager", "Routes ready: ${routes.size} routes, origin: $routerOrigin")

                    // ✅ 성공적으로 받은 경로를 캐시에 저장
                    cacheRoutes(routes)

                    // 경로 설정
                    mapboxNavigation.setNavigationRoutes(routes)

                    // 리스너에게 경로 준비 알림
                    routeChangeListener?.onRouteReady(routes)
                }
            }
        )
    }

    // ✅ cacheRoutes 함수 - 이제 실제로 사용됨
    private fun cacheRoutes(routes: List<NavigationRoute>) {
        cachedRoutes = routes
        Log.d("RouteManager", "Routes cached: ${routes.size}")
    }

    // 캐시된 경로 사용
    private fun useCachedRoutesIfAvailable(): Boolean {
        cachedRoutes?.let { routes ->
            if (routes.isNotEmpty()) {
                Log.d("RouteManager", "Using cached routes")
                mapboxNavigation.setNavigationRoutes(routes)
                routeChangeListener?.onRouteReady(routes)
                return true
            }
        }
        return false
    }

    fun clearRoutes() {
        mapboxNavigation.setNavigationRoutes(emptyList())
        currentDestination = null
        // 경로 클리어 시 캐시도 클리어
        cachedRoutes = null
    }

    fun hasValidRoute(): Boolean {
        return currentOrigin != null && currentDestination != null
    }

    // ✅ getOrigin 함수 - 사용 여부 결정 필요
    // 옵션 1: 제거 (현재 사용하지 않음)
    // fun getOrigin(): Point? = currentOrigin

    // 옵션 2: 유지 (나중에 디버깅이나 상태 확인용으로 사용 가능)
    fun getOrigin(): Point? = currentOrigin

    fun getDestination(): Point? = currentDestination

    // 캐시된 경로가 있는지 확인하는 유틸리티 함수 추가
    fun hasCachedRoutes(): Boolean {
        return cachedRoutes?.isNotEmpty() == true
    }
}