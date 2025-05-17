package com.capstone.navitest.map

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.capstone.navitest.MainActivity
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions

class OfflineTileManager(
    private val context: Context,
    private val tileStore: TileStore,
    private val languageManager: LanguageManager
) {
    private val REGION_NAME = "seoul-incheon-gyeonggi"

    init {
        // 타일 리전 존재 여부 확인
        checkTileRegionExists()
    }

    private fun checkTileRegionExists() {
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regions = expected.value!!
                val regionExists = regions.any { it.id == REGION_NAME }

                if (!regionExists) {
                    // 리전이 존재하지 않을 경우에만 다운로드
                    downloadOfflineTiles()
                } else {
                    Log.d("OfflineTiles", "Region $REGION_NAME already exists")
                }
            } else {
                // 리전 확인 중 오류 발생, 다운로드 시도
                downloadOfflineTiles()
            }
        }
    }

    fun downloadOfflineTiles() {
        // 서울/인천/경기도 지역을 대략적으로 커버하는 사각형 영역 생성
        val seoulAreaBounds = listOf(
            Point.fromLngLat(126.5, 37.3),  // 서쪽 하단
            Point.fromLngLat(127.5, 37.3),  // 동쪽 하단
            Point.fromLngLat(127.5, 38.0),  // 동쪽 상단
            Point.fromLngLat(126.5, 38.0),  // 서쪽 상단
            Point.fromLngLat(126.5, 37.3)   // 시작점으로 닫기
        )

        // 위 좌표로 Polygon 생성
        val regionGeometry = Polygon.fromLngLats(listOf(seoulAreaBounds))

        // OfflineManager 생성
        val offlineManager = OfflineManager()

        // tilesetDescriptor 생성
        val tilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.MAPBOX_STREETS)
                .minZoom(6)
                .maxZoom(16)
                .build()
        )

        // 내비게이션을 위한 추가 타일셋
        val navigationTilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/navigation-day-v1")
                .minZoom(6)
                .maxZoom(16)
                .build()
        )

        // TileRegionLoadOptions 생성
        val tileRegionLoadOptions = TileRegionLoadOptions.Builder()
            .geometry(regionGeometry)
            .acceptExpired(true)
            .descriptors(listOf(tilesetDescriptor, navigationTilesetDescriptor))
            .build()

        // 진행 상황 모니터링 콜백
        val progressCallback: (TileRegionLoadProgress) -> Unit = { progress ->
            val percentage = if (progress.requiredResourceCount > 0) {
                (progress.completedResourceCount.toFloat() / progress.requiredResourceCount) * 100
            } else {
                0f
            }

            Log.d("OfflineTiles", "Download progress: ${percentage.toInt()}%")
        }

        // 타일 리전 다운로드
        tileStore.loadTileRegion(
            REGION_NAME,
            tileRegionLoadOptions,
            progressCallback
        ) { expected ->
            if (expected.isValue) {
                // 다운로드 성공
                val tileRegion = expected.value!!
                Log.d("OfflineTiles", "Download completed successfully! Region: ${tileRegion.id}")

                // 성공 메시지 표시
                (context as? MainActivity)?.runOnUiThread {
                    val message = languageManager.getLocalizedString(
                        "오프라인 지도 다운로드 완료",
                        "Offline map download completed"
                    )

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } else {
                // 다운로드 실패
                val error = expected.error!!
                Log.e("OfflineTiles", "Download failed: ${error.message}")

                (context as? MainActivity)?.runOnUiThread {
                    val message = languageManager.getLocalizedString(
                        "오프라인 지도 다운로드 실패: ${error.message}",
                        "Offline map download failed: ${error.message}"
                    )

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}