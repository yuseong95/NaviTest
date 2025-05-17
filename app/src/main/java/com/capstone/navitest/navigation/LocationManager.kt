package com.capstone.navitest.navigation

import android.content.Context
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

class LocationManager(
    private val context: Context,
    private val mapView: MapView
) {
    private val navigationLocationProvider = NavigationLocationProvider()

    // 현재 위치 (경도, 위도)
    private var currentLocation: Point? = null

    // 카메라가 초기화되었는지 추적하는 플래그
    private var hasInitializedCamera = false

    // 위치 관찰자 콜백
    val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {
            // Raw 위치 업데이트는 사용하지 않음
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation

            // 현재 위치 업데이트
            currentLocation = Point.fromLngLat(
                enhancedLocation.longitude,
                enhancedLocation.latitude
            )

            // 맵 위치 업데이트
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            // 처음 위치를 받았을 때 카메라 위치 설정
            if (!hasInitializedCamera) {
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(currentLocation)
                        .zoom(15.0)
                        .build()
                )

                hasInitializedCamera = true
            }

            // 위치 변경 리스너에게 알림
            locationChangeListener?.onLocationChanged(currentLocation!!)
        }
    }

    // 위치 변경 리스너 인터페이스
    interface OnLocationChangeListener {
        fun onLocationChanged(location: Point)
    }

    // 위치 변경 리스너
    private var locationChangeListener: OnLocationChangeListener? = null

    init {
        initializeLocationComponent()
    }

    private fun initializeLocationComponent() {
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck()
            enabled = true
        }
    }

    fun setLocationChangeListener(listener: OnLocationChangeListener) {
        locationChangeListener = listener
    }

    fun getCurrentLocation(): Point? = currentLocation

    fun getNavigationLocationProvider(): NavigationLocationProvider = navigationLocationProvider
}