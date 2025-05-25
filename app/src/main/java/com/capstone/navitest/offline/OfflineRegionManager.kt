package com.capstone.navitest.offline

import android.content.Context
import android.util.Log
import com.capstone.navitest.R
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

class OfflineRegionManager(
    private val context: Context,
    private val languageManager: LanguageManager
) {
    private val tileStore: TileStore
    private val offlineManager: OfflineManager
    private var downloadProgressCallback: DownloadProgressCallback? = null
    private var isDownloading = false
    private var currentDownloadRegionName: String? = null

    // SharedPreferences for metadata storage
    private val prefs = context.getSharedPreferences("offline_regions_meta", Context.MODE_PRIVATE)

    // 진행상황 콜백 인터페이스
    interface DownloadProgressCallback {
        fun onDownloadStarted(regionName: String)
        fun onDownloadProgress(progress: Float)
        fun onDownloadCompleted(regionName: String)
        fun onDownloadError(regionName: String, error: String)
    }

    init {
        val tilePath = File(context.filesDir, "mbx-offline").absolutePath
        tileStore = TileStore.create(tilePath)
        offlineManager = OfflineManager()

        Log.d("OfflineRegionManager", "TileStore path: $tilePath")
    }

    fun setDownloadProgressCallback(callback: DownloadProgressCallback) {
        downloadProgressCallback = callback
    }

    /**
     * 지역 다운로드 (기존 지역 삭제 후 새 지역 다운로드)
     */
    suspend fun downloadRegion(
        regionName: String,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isDownloading) {
                Log.w("OfflineRegionManager", "Download already in progress")
                return@withContext false
            }

            isDownloading = true
            currentDownloadRegionName = regionName

            Log.d("OfflineRegionManager", "Starting download for region: $regionName")

            // 진행상황 콜백 호출
            downloadProgressCallback?.onDownloadStarted(regionName)

            // 1. 기존 모든 지역 삭제
            deleteAllRegions()

            // 2. 새 지역 다운로드
            val success = downloadRegionInternal(regionName, minLon, maxLon, minLat, maxLat)

            isDownloading = false
            currentDownloadRegionName = null

            if (success) {
                downloadProgressCallback?.onDownloadCompleted(regionName)
            } else {
                downloadProgressCallback?.onDownloadError(regionName, "Download failed")
            }

            return@withContext success

        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error downloading region: $regionName", e)
            isDownloading = false
            currentDownloadRegionName = null
            downloadProgressCallback?.onDownloadError(regionName, e.message ?: "Unknown error")
            return@withContext false
        }
    }

    /**
     * 실제 지역 다운로드 수행
     */
    private suspend fun downloadRegionInternal(
        regionName: String,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            // 지역 영역 정의
            val regionBounds = listOf(
                Point.fromLngLat(minLon, minLat),
                Point.fromLngLat(maxLon, minLat),
                Point.fromLngLat(maxLon, maxLat),
                Point.fromLngLat(minLon, maxLat),
                Point.fromLngLat(minLon, minLat)
            )

            val regionGeometry = Polygon.fromLngLats(listOf(regionBounds))

            // 타일셋 디스크립터 생성 (기본 스타일)
            val tilesetDescriptor = offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(Style.MAPBOX_STREETS)
                    .minZoom(6)
                    .maxZoom(16)
                    .build()
            )

            // 내비게이션용 타일셋 디스크립터
            val navigationTilesetDescriptor = offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI("mapbox://styles/mapbox/navigation-day-v1")
                    .minZoom(6)
                    .maxZoom(16)
                    .build()
            )

            // 로드 옵션 설정
            val loadOptions = TileRegionLoadOptions.Builder()
                .geometry(regionGeometry)
                .descriptors(listOf(tilesetDescriptor, navigationTilesetDescriptor))
                .acceptExpired(true)
                .build()

            // 진행상황 콜백
            val progressCallback: (TileRegionLoadProgress) -> Unit = { progress ->
                val percentage = if (progress.requiredResourceCount > 0) {
                    progress.completedResourceCount.toFloat() / progress.requiredResourceCount
                } else {
                    0f
                }

                Log.d("OfflineRegionManager", "Download progress: ${(percentage * 100).toInt()}%")
                downloadProgressCallback?.onDownloadProgress(percentage)
            }

            // 다운로드 시작
            Log.d("OfflineRegionManager", "Starting tile region download: $regionName")

            tileStore.loadTileRegion(
                regionName,
                loadOptions,
                progressCallback
            ) { expected ->
                if (expected.isValue) {
                    val tileRegion = expected.value!!
                    Log.d("OfflineRegionManager", "Download completed successfully: ${tileRegion.id}")

                    // 다운로드 메타데이터 저장
                    saveRegionMetadata(regionName)

                    continuation.resume(true)
                } else {
                    val error = expected.error!!
                    Log.e("OfflineRegionManager", "Download failed: ${error.message}")
                    continuation.resume(false)
                }
            }

            // 취소 처리
            continuation.invokeOnCancellation {
                Log.d("OfflineRegionManager", "Download cancelled: $regionName")
                // TODO: 실제 다운로드 취소 구현
            }

        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error in downloadRegionInternal", e)
            continuation.resume(false)
        }
    }

    /**
     * 모든 기존 지역 삭제
     */
    private suspend fun deleteAllRegions(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            tileStore.getAllTileRegions { expected ->
                if (expected.isValue) {
                    val regions = expected.value!!
                    if (regions.isEmpty()) {
                        Log.d("OfflineRegionManager", "No existing regions to delete")
                        clearAllRegionMetadata() // 메타데이터도 정리
                        continuation.resume(true)
                        return@getAllTileRegions
                    }

                    Log.d("OfflineRegionManager", "Deleting ${regions.size} existing regions")
                    var deletedCount = 0
                    val totalCount = regions.size

                    regions.forEach { region ->
                        tileStore.removeTileRegion(region.id) { deleteExpected ->
                            deletedCount++
                            if (deleteExpected.isError) {
                                Log.w("OfflineRegionManager", "Failed to delete region: ${region.id}")
                            } else {
                                Log.d("OfflineRegionManager", "Successfully deleted region: ${region.id}")
                            }

                            if (deletedCount == totalCount) {
                                Log.d("OfflineRegionManager", "All existing regions deleted")
                                clearAllRegionMetadata() // 모든 메타데이터 정리
                                continuation.resume(true)
                            }
                        }
                    }
                } else {
                    Log.e("OfflineRegionManager", "Failed to get existing regions")
                    continuation.resume(false)
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error deleting all regions", e)
            continuation.resume(false)
        }
    }

    /**
     * 다운로드된 지역 목록 가져오기
     */
    suspend fun getDownloadedRegions(): List<DownloadedRegion> = suspendCancellableCoroutine { continuation ->
        try {
            tileStore.getAllTileRegions { expected ->
                if (expected.isValue) {
                    val regions = expected.value!!
                    val downloadedRegions = regions.map { region ->
                        val metadata = getRegionMetadata(region.id)
                        DownloadedRegion(
                            id = region.id,
                            name = region.id,
                            downloadDate = metadata.downloadDate,
                            size = metadata.estimatedSize
                        )
                    }

                    Log.d("OfflineRegionManager", "Found ${downloadedRegions.size} downloaded regions")
                    continuation.resume(downloadedRegions)
                } else {
                    Log.e("OfflineRegionManager", "Failed to get downloaded regions")
                    continuation.resume(emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error getting downloaded regions", e)
            continuation.resume(emptyList())
        }
    }

    /**
     * 지역 메타데이터 저장
     */
    private fun saveRegionMetadata(regionName: String) {
        val currentTime = System.currentTimeMillis()
        val estimatedSize = getEstimatedRegionSizeByName(regionName)

        prefs.edit().apply {
            putLong("${regionName}_download_time", currentTime)
            putLong("${regionName}_estimated_size", estimatedSize)
            apply()
        }

        Log.d("OfflineRegionManager", "Saved metadata for region: $regionName")
    }

    /**
     * 지역 메타데이터 조회
     */
    private fun getRegionMetadata(regionName: String): RegionMetadata {
        val downloadTime = prefs.getLong("${regionName}_download_time", System.currentTimeMillis())
        val estimatedSize = prefs.getLong("${regionName}_estimated_size", getEstimatedRegionSizeByName(regionName))

        return RegionMetadata(
            downloadDate = formatDate(Date(downloadTime)),
            estimatedSize = formatSize(estimatedSize)
        )
    }

    /**
     * 지역 메타데이터 삭제
     */
    private fun removeRegionMetadata(regionName: String) {
        prefs.edit().apply {
            remove("${regionName}_download_time")
            remove("${regionName}_estimated_size")
            apply()
        }

        Log.d("OfflineRegionManager", "Removed metadata for region: $regionName")
    }

    /**
     * 모든 지역 메타데이터 삭제
     */
    private fun clearAllRegionMetadata() {
        prefs.edit().clear().apply()
        Log.d("OfflineRegionManager", "Cleared all region metadata")
    }

    /**
     * 특정 지역 삭제
     */
    suspend fun deleteRegion(regionId: String): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            Log.d("OfflineRegionManager", "Deleting region: $regionId")

            tileStore.removeTileRegion(regionId) { expected ->
                if (expected.isValue) {
                    Log.d("OfflineRegionManager", "Successfully deleted region: $regionId")

                    // 메타데이터도 함께 삭제
                    removeRegionMetadata(regionId)

                    continuation.resume(true)
                } else {
                    Log.e("OfflineRegionManager", "Failed to delete region: $regionId")
                    continuation.resume(false)
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error deleting region: $regionId", e)
            continuation.resume(false)
        }
    }

    /**
     * 현재 다운로드 취소
     */
    fun cancelCurrentDownload() {
        if (isDownloading && currentDownloadRegionName != null) {
            Log.d("OfflineRegionManager", "Cancelling download: $currentDownloadRegionName")
            isDownloading = false

            // TODO: 실제 Mapbox 다운로드 취소 API 호출
            // 현재 Mapbox SDK에서는 직접적인 취소 API가 제한적일 수 있음

            downloadProgressCallback?.onDownloadError(
                currentDownloadRegionName!!,
                languageManager.getLocalizedString("다운로드가 취소되었습니다", "Download cancelled")
            )

            currentDownloadRegionName = null
        }
    }

    /**
     * 다운로드 상태 확인
     */
    fun isDownloading(): Boolean = isDownloading

    /**
     * 현재 다운로드 중인 지역명 반환
     */
    fun getCurrentDownloadRegion(): String? = currentDownloadRegionName

    /**
     * 날짜 포맷팅
     */
    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(date)
    }

    /**
     * 파일 크기 포맷팅
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 지역 크기 추정 (실제 API에서 크기 정보를 제공하지 않으므로 추정값 사용)
     */
    private fun getEstimatedRegionSizeByName(regionName: String): Long {
        // 지역별 추정 크기 (MB 단위를 바이트로 변환)
        val estimatedSizeMB = when {
            regionName.contains("seoul", ignoreCase = true) || regionName.contains("서울", ignoreCase = true) -> 150L
            regionName.contains("busan", ignoreCase = true) || regionName.contains("부산", ignoreCase = true) -> 120L
            regionName.contains("gyeonggi", ignoreCase = true) || regionName.contains("경기", ignoreCase = true) -> 300L
            regionName.contains("gangwon", ignoreCase = true) || regionName.contains("강원", ignoreCase = true) -> 250L
            regionName.contains("incheon", ignoreCase = true) || regionName.contains("인천", ignoreCase = true) -> 130L
            regionName.contains("daegu", ignoreCase = true) || regionName.contains("대구", ignoreCase = true) -> 110L
            regionName.contains("gwangju", ignoreCase = true) || regionName.contains("광주", ignoreCase = true) -> 100L
            regionName.contains("daejeon", ignoreCase = true) || regionName.contains("대전", ignoreCase = true) -> 105L
            regionName.contains("ulsan", ignoreCase = true) || regionName.contains("울산", ignoreCase = true) -> 95L
            regionName.contains("jeju", ignoreCase = true) || regionName.contains("제주", ignoreCase = true) -> 80L
            regionName.contains("chung", ignoreCase = true) || regionName.contains("충청", ignoreCase = true) -> 200L
            regionName.contains("jeon", ignoreCase = true) || regionName.contains("전라", ignoreCase = true) -> 220L
            regionName.contains("gyeongsang", ignoreCase = true) || regionName.contains("경상", ignoreCase = true) -> 240L
            regionName.length > 15 -> 200L // 긴 이름 (해외 도시 등)
            regionName.length > 10 -> 150L // 중간 길이 이름
            else -> 100L // 기타 지역
        }
        return estimatedSizeMB * 1024 * 1024 // MB를 바이트로 변환
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        Log.d("OfflineRegionManager", "Cleaning up OfflineRegionManager")
        downloadProgressCallback = null
        isDownloading = false
        currentDownloadRegionName = null
    }
}

/**
 * 지역 메타데이터를 담는 데이터 클래스
 */
data class RegionMetadata(
    val downloadDate: String,
    val estimatedSize: String
)