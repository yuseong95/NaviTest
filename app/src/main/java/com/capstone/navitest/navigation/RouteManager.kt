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
import android.os.Build


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

    // 경로 정보 캐싱 지원을 위한 필드 추가
    private var cachedRoutes: List<NavigationRoute>? = null

    // 경로 캐싱 처리
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

    // 경로 변경 리스너
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
            // 위치가 아직 설정되지 않았다면 LocationObserver에서 처리될 것임
        }
    }

    fun setRouteChangeListener(listener: OnRouteChangeListener) {
        routeChangeListener = listener
    }

    fun requestRoute() {
        val origin = currentOrigin ?: return
        val destination = currentDestination ?: return

        // 네트워크 연결 확인
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isNetworkAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo ?: return
            networkInfo.isConnected
        }

        // 오프라인 상태이고 캐시된 경로가 있으면 사용
        if (!isNetworkAvailable) {
            if (useCachedRoutesIfAvailable()) {
                // 캐시된 경로 사용 성공
                val message = languageManager.getLocalizedString(
                    "오프라인 모드: 캐시된 경로 사용",
                    "Offline mode: Using cached route"
                )
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return
            } else {
                // 캐시된 경로 없음
                val message = languageManager.getLocalizedString(
                    "오프라인 모드: 새 경로를 계산할 수 없습니다. 인터넷 연결이 필요합니다.",
                    "Offline mode: Cannot calculate new route. Internet connection required."
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return
            }
        }

        // 로그로 경로 요청 정보 기록
        Log.d("Navigation", "Requesting route from: ${origin.longitude()},${origin.latitude()} to: ${destination.longitude()},${destination.latitude()}")

        // 디바운싱 체크
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRouteRequestTime < Constants.ROUTE_REQUEST_DEBOUNCE_TIME) {
            Log.d("Navigation", "Route request debounced")
            return
        }

        // 마지막 요청 시간 업데이트
        lastRouteRequestTime = currentTime

        // 경로 요청 옵션 빌드
        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .coordinatesList(listOf(origin, destination))
            .layersList(listOf(mapboxNavigation.getZLevel(), null))
            .language(languageManager.currentLanguage) // 선택된 언어로 경로 안내 설정
            .steps(true) // 턴 바이 턴 안내를 위해 필요
            .build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    Log.d("Navigation", "Route request canceled")
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("Navigation", "Route request failed: ${reasons.firstOrNull()?.message}")

                    val message = languageManager.getLocalizedString(
                        "경로를 찾을 수 없습니다: ${reasons.firstOrNull()?.message}",
                        "Could not find route: ${reasons.firstOrNull()?.message}"
                    )

                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                    // 리스너에게 오류 알림
                    routeChangeListener?.onRouteError(message)
                }

                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    Log.d("Navigation", "Routes ready: ${routes.size} routes, origin: $routerOrigin")

                    // 경로 설정
                    mapboxNavigation.setNavigationRoutes(routes)

                    // 리스너에게 경로 준비 알림
                    routeChangeListener?.onRouteReady(routes)
                }
            }
        )
    }

    fun clearRoutes() {
        mapboxNavigation.setNavigationRoutes(emptyList())
        currentDestination = null
    }

    fun hasValidRoute(): Boolean {
        return currentOrigin != null && currentDestination != null
    }

    fun getOrigin(): Point? = currentOrigin

    fun getDestination(): Point? = currentDestination
}