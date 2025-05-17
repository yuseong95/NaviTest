package com.capstone.navitest.map

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor

class MarkerManager(
    private val context: Context,
    private val pointAnnotationManager: PointAnnotationManager,
    private val languageManager: LanguageManager
) {

    // 마커 추가
    fun addMarker(point: Point): Point {
        // 기존 마커 삭제
        pointAnnotationManager.deleteAll()

        // 리소스에서 비트맵 생성
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            android.R.drawable.ic_menu_mylocation
        )

        // 마커 옵션 설정
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(point)
            .withIconImage(bitmap)
            .withIconAnchor(IconAnchor.BOTTOM)
            .withIconSize(1.5)

        // 맵에 마커 추가
        pointAnnotationManager.create(pointAnnotationOptions)

        // 마커 추가 메시지 표시
        val message = languageManager.getLocalizedString(
            "목적지 설정됨",
            "Destination set"
        )
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        return point
    }

    // 모든 마커 삭제
    fun clearMarkers() {
        pointAnnotationManager.deleteAll()
    }
}