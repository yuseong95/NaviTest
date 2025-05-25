package com.capstone.navitest.offline

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.capstone.navitest.R
import com.capstone.navitest.search.SearchManager
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.common.IsoCountryCode
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import kotlinx.coroutines.launch

class OfflineMapActivity : ComponentActivity() {

    private lateinit var languageManager: LanguageManager
    private lateinit var offlineRegionManager: OfflineRegionManager
    private lateinit var searchManager: SearchManager

    // UI 컴포넌트들
    private lateinit var backButton: ImageButton
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var koreanRegionsContainer: LinearLayout
    private lateinit var worldMapContainer: LinearLayout
    private lateinit var downloadedRegionsRecyclerView: RecyclerView
    private lateinit var downloadProgressContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var cancelDownloadButton: Button

    // 탭 관련
    private lateinit var tabKoreanRegions: LinearLayout
    private lateinit var tabWorldMap: LinearLayout
    private lateinit var tabDownloaded: LinearLayout

    // 어댑터
    private lateinit var downloadedRegionsAdapter: DownloadedRegionsAdapter

    // 현재 선택된 탭
    private var currentTab = OfflineTab.KOREAN_REGIONS

    enum class OfflineTab {
        KOREAN_REGIONS, WORLD_MAP, DOWNLOADED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_map)

        // Mapbox 토큰 설정
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

        // 매니저 초기화
        languageManager = LanguageManager(this)
        offlineRegionManager = OfflineRegionManager(this, languageManager)
        searchManager = SearchManager(this)

        // UI 초기화
        initializeUI()
        setupEventListeners()
        setupRecyclerView()

        // 기본 탭 선택
        selectTab(OfflineTab.KOREAN_REGIONS)

        // 다운로드된 지역 목록 로드
        loadDownloadedRegions()
    }

    private fun initializeUI() {
        // 상단 바
        backButton = findViewById(R.id.backButton)

        // 검색 관련
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)

        // 탭
        tabKoreanRegions = findViewById(R.id.tabKoreanRegions)
        tabWorldMap = findViewById(R.id.tabWorldMap)
        tabDownloaded = findViewById(R.id.tabDownloaded)

        // 컨텐츠 컨테이너
        koreanRegionsContainer = findViewById(R.id.koreanRegionsContainer)
        worldMapContainer = findViewById(R.id.worldMapContainer)
        downloadedRegionsRecyclerView = findViewById(R.id.downloadedRegionsRecyclerView)

        // 다운로드 진행상황
        downloadProgressContainer = findViewById(R.id.downloadProgressContainer)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        cancelDownloadButton = findViewById(R.id.cancelDownloadButton)

        // 초기 상태 설정
        downloadProgressContainer.visibility = View.GONE
        updateLanguageTexts()
        setupKoreanRegions()
    }

    private fun setupEventListeners() {
        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 검색 버튼
        searchButton.setOnClickListener {
            performSearch()
        }

        // 엔터키로 검색
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // 탭 클릭 이벤트
        tabKoreanRegions.setOnClickListener { selectTab(OfflineTab.KOREAN_REGIONS) }
        tabWorldMap.setOnClickListener { selectTab(OfflineTab.WORLD_MAP) }
        tabDownloaded.setOnClickListener { selectTab(OfflineTab.DOWNLOADED) }

        // 다운로드 취소 버튼
        cancelDownloadButton.setOnClickListener {
            offlineRegionManager.cancelCurrentDownload()
            hideDownloadProgress()
        }

        // 다운로드 진행상황 콜백 설정
        offlineRegionManager.setDownloadProgressCallback(object : OfflineRegionManager.DownloadProgressCallback {
            override fun onDownloadStarted(regionName: String) {
                runOnUiThread {
                    showDownloadProgress(regionName)
                }
            }

            override fun onDownloadProgress(progress: Float) {
                runOnUiThread {
                    updateDownloadProgress(progress)
                }
            }

            override fun onDownloadCompleted(regionName: String) {
                runOnUiThread {
                    hideDownloadProgress()
                    loadDownloadedRegions()
                    showToast(languageManager.getLocalizedString(
                        "$regionName 다운로드 완료",
                        "$regionName download completed"
                    ))
                }
            }

            override fun onDownloadError(regionName: String, error: String) {
                runOnUiThread {
                    hideDownloadProgress()
                    showToast(languageManager.getLocalizedString(
                        "$regionName 다운로드 실패: $error",
                        "$regionName download failed: $error"
                    ))
                }
            }
        })
    }

    private fun setupRecyclerView() {
        downloadedRegionsAdapter = DownloadedRegionsAdapter(
            emptyList(),
            languageManager
        ) { region ->
            // 지역 삭제 확인 다이얼로그
            showDeleteConfirmDialog(region)
        }

        downloadedRegionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OfflineMapActivity)
            adapter = downloadedRegionsAdapter
        }
    }

    private fun setupKoreanRegions() {
        val koreanRegions = listOf(
            KoreanRegion("서울특별시", "Seoul", 126.978, 37.566, 126.8, 127.2, 37.4, 37.7, "수도권 중심지"),
            KoreanRegion("부산광역시", "Busan", 129.075, 35.180, 128.9, 129.3, 35.0, 35.4, "남부 항구도시"),
            KoreanRegion("대구광역시", "Daegu", 128.601, 35.871, 128.4, 128.8, 35.7, 36.0, "영남권 중심도시"),
            KoreanRegion("인천광역시", "Incheon", 126.705, 37.456, 126.4, 127.0, 37.2, 37.7, "수도권 관문도시"),
            KoreanRegion("광주광역시", "Gwangju", 126.852, 35.160, 126.7, 127.0, 35.0, 35.3, "호남권 중심도시"),
            KoreanRegion("대전광역시", "Daejeon", 127.385, 36.351, 127.2, 127.6, 36.2, 36.5, "충청권 과학도시"),
            KoreanRegion("울산광역시", "Ulsan", 129.311, 35.538, 129.1, 129.5, 35.3, 35.7, "동남권 산업도시"),
            KoreanRegion("경기도", "Gyeonggi", 127.5, 37.4, 126.5, 128.5, 36.8, 38.2, "수도권 광역지역"),
            KoreanRegion("강원도", "Gangwon", 128.2, 37.8, 127.0, 129.5, 37.0, 38.6, "동북부 산악지역"),
            KoreanRegion("충청북도", "Chungbuk", 127.7, 36.8, 127.2, 128.8, 36.3, 37.3, "중부내륙 지역"),
            KoreanRegion("충청남도", "Chungnam", 127.0, 36.5, 126.3, 127.8, 36.0, 37.0, "서해안 지역"),
            KoreanRegion("전라북도", "Jeonbuk", 127.1, 35.7, 126.4, 127.9, 35.2, 36.3, "호남 북부지역"),
            KoreanRegion("전라남도", "Jeonnam", 126.9, 34.8, 125.4, 127.8, 34.0, 35.8, "호남 남부지역"),
            KoreanRegion("경상북도", "Gyeongbuk", 128.9, 36.4, 128.0, 130.0, 35.4, 37.5, "영남 북부지역"),
            KoreanRegion("경상남도", "Gyeongnam", 128.2, 35.4, 127.4, 129.2, 34.6, 36.0, "영남 남부지역"),
            KoreanRegion("제주특별자치도", "Jeju", 126.5, 33.4, 126.1, 126.9, 33.1, 33.6, "남쪽 화산섬")
        )

        koreanRegionsContainer.removeAllViews()

        koreanRegions.forEach { region ->
            val regionCard = createRegionCard(region)
            koreanRegionsContainer.addView(regionCard)
        }
    }

    private fun createRegionCard(region: KoreanRegion): View {
        val cardView = layoutInflater.inflate(R.layout.item_korean_region, null)
        val regionName = cardView.findViewById<TextView>(R.id.regionName)
        val regionDescription = cardView.findViewById<TextView>(R.id.regionDescription)
        val downloadButton = cardView.findViewById<Button>(R.id.downloadButton)

        regionName.text = if (languageManager.currentLanguage == "ko") region.koreanName else region.englishName

        // 설명 텍스트 설정 및 표시
        regionDescription.text = region.description
        regionDescription.visibility = View.VISIBLE

        downloadButton.text = languageManager.getLocalizedString("다운로드", "Download")

        downloadButton.setOnClickListener {
            if (isNetworkAvailable()) {
                downloadRegion(region)
            } else {
                showToast(languageManager.getLocalizedString(
                    "오프라인 지도 다운로드는 인터넷 연결이 필요합니다",
                    "Internet connection required for offline map download"
                ))
            }
        }

        return cardView
    }

    private fun performSearch() {
        val query = searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            showToast(languageManager.getLocalizedString(
                "검색어를 입력해주세요",
                "Please enter a search term"
            ))
            return
        }

        if (!isNetworkAvailable()) {
            showToast(languageManager.getLocalizedString(
                "검색 기능은 인터넷 연결이 필요합니다",
                "Search requires internet connection"
            ))
            return
        }

        // 검색 수행
        val searchOptions = SearchOptions.Builder()
            .languages(listOf(
                if (languageManager.currentLanguage == "ko") IsoLanguageCode.KOREAN else IsoLanguageCode.ENGLISH
            ))
            .limit(5)
            .build()

        searchManager.geocodingEngine.search(
            query = query,
            options = searchOptions,
            callback = object : SearchSuggestionsCallback {
                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    if (suggestions.isNotEmpty()) {
                        // 첫 번째 결과 선택
                        searchManager.geocodingEngine.select(
                            suggestion = suggestions.first(),
                            callback = object : SearchSelectionCallback {
                                override fun onResult(
                                    suggestion: SearchSuggestion,
                                    result: SearchResult,
                                    responseInfo: ResponseInfo
                                ) {
                                    handleSearchResult(result)
                                }

                                override fun onResults(
                                    suggestion: SearchSuggestion,
                                    results: List<SearchResult>,
                                    responseInfo: ResponseInfo
                                ) {
                                    if (results.isNotEmpty()) {
                                        handleSearchResult(results.first())
                                    }
                                }

                                override fun onSuggestions(
                                    suggestions: List<SearchSuggestion>,
                                    responseInfo: ResponseInfo
                                ) {
                                    // 추가 suggestions 처리 (필요시)
                                }

                                override fun onError(e: Exception) {
                                    runOnUiThread {
                                        showToast(languageManager.getLocalizedString(
                                            "검색 중 오류가 발생했습니다: ${e.message}",
                                            "Search error: ${e.message}"
                                        ))
                                    }
                                }
                            }
                        )
                    } else {
                        runOnUiThread {
                            showToast(languageManager.getLocalizedString(
                                "검색 결과가 없습니다",
                                "No search results found"
                            ))
                        }
                    }
                }

                override fun onError(e: Exception) {
                    runOnUiThread {
                        showToast(languageManager.getLocalizedString(
                            "검색 중 오류가 발생했습니다: ${e.message}",
                            "Search error: ${e.message}"
                        ))
                    }
                }
            }
        )
    }

    private fun handleSearchResult(result: SearchResult) {
        val coordinate = result.coordinate
        val placeName = result.name

        // 검색된 위치 주변 지역을 다운로드하기 위한 CustomRegion 생성
        val customRegion = CustomRegion(
            name = placeName,
            centerLat = coordinate.latitude(),
            centerLon = coordinate.longitude(),
            // 검색된 지점 주변 약 50km 반경 (조정 가능)
            minLon = coordinate.longitude() - 0.5,
            maxLon = coordinate.longitude() + 0.5,
            minLat = coordinate.latitude() - 0.3,
            maxLat = coordinate.latitude() + 0.3
        )

        runOnUiThread {
            // 다운로드 확인 다이얼로그
            showDownloadConfirmDialog(customRegion)
        }
    }

    private fun showDownloadConfirmDialog(region: Any) {
        val regionName = when (region) {
            is KoreanRegion -> if (languageManager.currentLanguage == "ko") region.koreanName else region.englishName
            is CustomRegion -> region.name
            else -> "Unknown Region"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString("지역 다운로드", "Download Region"))
            .setMessage(languageManager.getLocalizedString(
                "$regionName 지역의 오프라인 지도를 다운로드하시겠습니까?\n\n기존에 다운로드된 지역이 있다면 삭제됩니다.",
                "Download offline map for $regionName?\n\nExisting downloaded regions will be deleted."
            ))
            .setPositiveButton(languageManager.getLocalizedString("다운로드", "Download")) { _, _ ->
                when (region) {
                    is KoreanRegion -> downloadRegion(region)
                    is CustomRegion -> downloadCustomRegion(region)
                }
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .show()
    }

    private fun downloadRegion(region: KoreanRegion) {
        lifecycleScope.launch {
            offlineRegionManager.downloadRegion(
                regionName = if (languageManager.currentLanguage == "ko") region.koreanName else region.englishName,
                minLon = region.minLon,
                maxLon = region.maxLon,
                minLat = region.minLat,
                maxLat = region.maxLat
            )
        }
    }

    private fun downloadCustomRegion(region: CustomRegion) {
        lifecycleScope.launch {
            offlineRegionManager.downloadRegion(
                regionName = region.name,
                minLon = region.minLon,
                maxLon = region.maxLon,
                minLat = region.minLat,
                maxLat = region.maxLat
            )
        }
    }

    private fun selectTab(tab: OfflineTab) {
        currentTab = tab

        // 모든 탭 비활성화
        updateTabAppearance(tabKoreanRegions, false)
        updateTabAppearance(tabWorldMap, false)
        updateTabAppearance(tabDownloaded, false)

        // 모든 컨텐츠 숨기기
        koreanRegionsContainer.visibility = View.GONE
        worldMapContainer.visibility = View.GONE
        findViewById<LinearLayout>(R.id.downloadedRegionsContainer).visibility = View.GONE

        // 선택된 탭 활성화 및 컨텐츠 표시
        when (tab) {
            OfflineTab.KOREAN_REGIONS -> {
                updateTabAppearance(tabKoreanRegions, true)
                koreanRegionsContainer.visibility = View.VISIBLE
            }
            OfflineTab.WORLD_MAP -> {
                updateTabAppearance(tabWorldMap, true)
                worldMapContainer.visibility = View.VISIBLE
                // TODO: 전세계 지도 컨텐츠 구현
                showToast(languageManager.getLocalizedString(
                    "전세계 지도 기능은 추후 구현 예정입니다",
                    "World map feature will be implemented later"
                ))
            }
            OfflineTab.DOWNLOADED -> {
                updateTabAppearance(tabDownloaded, true)
                findViewById<LinearLayout>(R.id.downloadedRegionsContainer).visibility = View.VISIBLE
                loadDownloadedRegions()
            }
        }
    }

    private fun updateTabAppearance(tabView: LinearLayout, isActive: Boolean) {
        val textView = tabView.getChildAt(0) as TextView

        if (isActive) {
            textView.setTextColor(getColor(R.color.primary_color))
            tabView.setBackgroundColor(getColor(R.color.language_option_selected))
        } else {
            textView.setTextColor(getColor(R.color.setting_desc_color))
            tabView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    private fun showDownloadProgress(regionName: String) {
        downloadProgressContainer.visibility = View.VISIBLE
        progressText.text = languageManager.getLocalizedString(
            "$regionName 다운로드 중...",
            "Downloading $regionName..."
        )
        progressBar.progress = 0
    }

    private fun updateDownloadProgress(progress: Float) {
        val progressInt = (progress * 100).toInt()
        progressBar.progress = progressInt
        progressText.text = languageManager.getLocalizedString(
            "다운로드 중... $progressInt%",
            "Downloading... $progressInt%"
        )
    }

    private fun hideDownloadProgress() {
        downloadProgressContainer.visibility = View.GONE
    }

    private fun loadDownloadedRegions() {
        lifecycleScope.launch {
            val regions = offlineRegionManager.getDownloadedRegions()
            runOnUiThread {
                downloadedRegionsAdapter.updateRegions(regions)

                // 빈 상태 텍스트 처리
                val emptyStateText = findViewById<TextView>(R.id.emptyStateText)
                if (regions.isEmpty()) {
                    downloadedRegionsRecyclerView.visibility = View.GONE
                    emptyStateText.visibility = View.VISIBLE
                    emptyStateText.text = languageManager.getLocalizedString(
                        "다운로드된 지역이 없습니다.\n\n위의 탭에서 지역을 선택하거나\n검색을 통해 다운로드하세요.",
                        "No downloaded regions.\n\nSelect a region from the tabs above\nor download through search."
                    )
                } else {
                    downloadedRegionsRecyclerView.visibility = View.VISIBLE
                    emptyStateText.visibility = View.GONE
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(region: DownloadedRegion) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString("지역 삭제", "Delete Region"))
            .setMessage(languageManager.getLocalizedString(
                "${region.name} 지역을 삭제하시겠습니까?",
                "Delete ${region.name} region?"
            ))
            .setPositiveButton(languageManager.getLocalizedString("삭제", "Delete")) { _, _ ->
                deleteRegion(region)
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .show()
    }

    private fun deleteRegion(region: DownloadedRegion) {
        lifecycleScope.launch {
            val success = offlineRegionManager.deleteRegion(region.id)
            runOnUiThread {
                if (success) {
                    loadDownloadedRegions()
                    showToast(languageManager.getLocalizedString(
                        "${region.name} 삭제 완료",
                        "${region.name} deleted"
                    ))
                } else {
                    showToast(languageManager.getLocalizedString(
                        "삭제 실패",
                        "Delete failed"
                    ))
                }
            }
        }
    }

    private fun updateLanguageTexts() {
        // 검색 힌트
        searchEditText.hint = languageManager.getLocalizedString(
            "도시 또는 지역명 입력 (예: 오키나와, 도쿄, 뉴욕)",
            "Enter city or region name (e.g., Okinawa, Tokyo, New York)"
        )

        // 탭 텍스트
        (tabKoreanRegions.getChildAt(0) as TextView).text = languageManager.getLocalizedString("국내 지역", "Korean Regions")
        (tabWorldMap.getChildAt(0) as TextView).text = languageManager.getLocalizedString("전세계", "World Map")
        (tabDownloaded.getChildAt(0) as TextView).text = languageManager.getLocalizedString("다운로드됨", "Downloaded")

        // 취소 버튼
        cancelDownloadButton.text = languageManager.getLocalizedString("취소", "Cancel")
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::offlineRegionManager.isInitialized) {
            offlineRegionManager.cleanup()
        }
        if (::searchManager.isInitialized) {
            searchManager.cleanup()
        }
    }
}

// 데이터 클래스들
data class KoreanRegion(
    val koreanName: String,
    val englishName: String,
    val centerLon: Double,
    val centerLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double,
    val description: String
)

data class CustomRegion(
    val name: String,
    val centerLon: Double,
    val centerLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double
)

data class DownloadedRegion(
    val id: String,
    val name: String,
    val downloadDate: String,
    val size: String
)