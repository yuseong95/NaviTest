package com.quicinc.chatapp.offline
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
import com.quicinc.chatapp.R
import com.quicinc.chatapp.search.SearchManager
import com.quicinc.chatapp.ui.LanguageManager
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

    // 모든 지역을 저장할 전역 변수
    private lateinit var allRegions: List<InternationalRegion>

    // 현재 선택된 탭
    private var currentTab = OfflineTab.KOREAN_REGIONS

    // 다운로드 취소를 위한 플래그
    private var isDownloadCancelled = false

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
        setupAllRegions()
    }

    private fun setupEventListeners() {
        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 검색 버튼을 새로운 스마트 검색으로 연결
        searchButton.setOnClickListener {
            performSmartSearch()  // performSearch() → performSmartSearch()
        }

        // 엔터키로 검색도 새로운 스마트 검색으로 연결
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSmartSearch()  // performSearch() → performSmartSearch()
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
            showCancelDownloadDialog()
        }

        // 다운로드 진행상황 콜백 설정
        offlineRegionManager.setDownloadProgressCallback(object : OfflineRegionManager.DownloadProgressCallback {
            override fun onDownloadStarted(regionName: String) {
                runOnUiThread {
                    isDownloadCancelled = false
                    showDownloadProgress(regionName)
                }
            }

            override fun onDownloadProgress(progress: Float) {
                runOnUiThread {
                    if (!isDownloadCancelled) {
                        updateDownloadProgress(progress)
                    }
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
                    if (!isDownloadCancelled) {
                        showToast(languageManager.getLocalizedString(
                            "$regionName 다운로드 실패: $error",
                            "$regionName download failed: $error"
                        ))
                    }
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

    private fun setupAllRegions() {
        // 모든 지역을 하나의 리스트로 통합
         allRegions = listOf(
            // === 기존 한국 지역들 (country = "대한민국" 추가) ===
            InternationalRegion(
                "수도권 (서울·경기·인천)", "Seoul Metropolitan Area",
                126.978, 37.566,
                126.4, 127.6, 37.2, 38.0,
                "서울특별시, 경기도, 인천광역시 통합 지역", RegionType.METROPOLITAN,
                "대한민국"
            ),
            InternationalRegion(
                "부산광역시", "Busan",
                129.075, 35.180,
                128.8, 129.3, 34.9, 35.4,
                "남부 최대 항구도시", RegionType.CITY,
                "대한민국"
            ),
            InternationalRegion(
                "대구광역시", "Daegu",
                128.601, 35.871,
                128.4, 128.9, 35.6, 36.1,
                "영남권 중심도시", RegionType.CITY,
                "대한민국"
            ),
            InternationalRegion(
                "광주광역시", "Gwangju",
                126.852, 35.160,
                126.6, 127.1, 35.0, 35.4,
                "호남권 중심도시", RegionType.CITY,
                "대한민국"
            ),
            InternationalRegion(
                "대전광역시", "Daejeon",
                127.385, 36.351,
                127.1, 127.7, 36.1, 36.6,
                "충청권 과학도시", RegionType.CITY,
                "대한민국"
            ),
            InternationalRegion(
                "울산광역시", "Ulsan",
                129.311, 35.538,
                129.0, 129.6, 35.3, 35.8,
                "동남권 산업도시", RegionType.CITY,
                "대한민국"
            ),
            // 도 지역들도 동일하게... (생략)

            // === 일본 주요 도시들 ===
            InternationalRegion(
                "도쿄 (Tokyo)", "Tokyo",
                139.6917, 35.6895,
                139.0, 140.5, 35.0, 36.5,
                "일본 수도, 인구 1400만명", RegionType.INTERNATIONAL_MAJOR,
                "일본"
            ),
            InternationalRegion(
                "오사카 (Osaka)", "Osaka",
                135.5023, 34.6937,
                135.0, 136.0, 34.0, 35.5,
                "일본 제2의 도시, 간사이 지역 중심", RegionType.INTERNATIONAL_MAJOR,
                "일본"
            ),
            InternationalRegion(
                "교토 (Kyoto)", "Kyoto",
                135.7681, 35.0116,
                135.2, 136.2, 34.7, 35.5,
                "일본 고대 수도, 전통 문화의 중심", RegionType.INTERNATIONAL_CITY,
                "일본"
            ),
            InternationalRegion(
                "나고야 (Nagoya)", "Nagoya",
                136.9066, 35.1815,
                136.4, 137.4, 34.8, 35.6,
                "일본 중부 최대 도시, 자동차 산업 중심", RegionType.INTERNATIONAL_CITY,
                "일본"
            ),
            InternationalRegion(
                "요코하마 (Yokohama)", "Yokohama",
                139.6380, 35.4437,
                139.2, 140.0, 35.1, 35.8,
                "일본 제2의 항구도시", RegionType.INTERNATIONAL_CITY,
                "일본"
            ),
            InternationalRegion(
                "고베 (Kobe)", "Kobe",
                135.1955, 34.6901,
                134.8, 135.6, 34.4, 35.0,
                "일본 주요 항구도시", RegionType.INTERNATIONAL_CITY,
                "일본"
            ),

            // === 미국 주요 도시들 ===
            InternationalRegion(
                "뉴욕 (New York)", "New York",
                -74.0060, 40.7128,
                -74.5, -73.5, 40.0, 41.5,
                "미국 최대 도시, 금융·문화 중심지", RegionType.INTERNATIONAL_MAJOR,
                "미국"
            ),
            InternationalRegion(
                "로스앤젤레스 (Los Angeles)", "Los Angeles",
                -118.2437, 34.0522,
                -119.0, -117.5, 33.2, 34.8,
                "미국 서부 최대 도시, 할리우드", RegionType.INTERNATIONAL_MAJOR,
                "미국"
            ),
            InternationalRegion(
                "시카고 (Chicago)", "Chicago",
                -87.6298, 41.8781,
                -88.2, -87.0, 41.3, 42.3,
                "미국 중서부 중심도시", RegionType.INTERNATIONAL_MAJOR,
                "미국"
            ),
            InternationalRegion(
                "샌프란시스코 (San Francisco)", "San Francisco",
                -122.4194, 37.7749,
                -122.8, -122.0, 37.4, 38.1,
                "미국 실리콘밸리, 기술 중심지", RegionType.INTERNATIONAL_CITY,
                "미국"
            ),
            InternationalRegion(
                "라스베이거스 (Las Vegas)", "Las Vegas",
                -115.1398, 36.1699,
                -115.7, -114.5, 35.8, 36.5,
                "미국 네바다주 관광·엔터테인먼트 도시", RegionType.INTERNATIONAL_CITY,
                "미국"
            ),
            InternationalRegion(
                "마이애미 (Miami)", "Miami",
                -80.1918, 25.7617,
                -80.6, -79.8, 25.4, 26.1,
                "미국 플로리다주 국제적 관광도시", RegionType.INTERNATIONAL_CITY,
                "미국"
            ),
            InternationalRegion(
                "시애틀 (Seattle)", "Seattle",
                -122.3321, 47.6062,
                -122.8, -121.8, 47.2, 47.9,
                "미국 서북부 기술·항공 중심지", RegionType.INTERNATIONAL_CITY,
                "미국"
            )
        )

        koreanRegionsContainer.removeAllViews()

        // 권역별로 그룹핑해서 표시
        val groupedRegions = allRegions.groupBy { it.type }

        // 수도권 먼저 표시
        groupedRegions[RegionType.METROPOLITAN]?.let { regions ->
            addRegionGroupHeader("수도권")
            regions.forEach { region ->
                val regionCard = createRegionCard(region)
                koreanRegionsContainer.addView(regionCard)
            }
        }

        // 광역시
        groupedRegions[RegionType.CITY]?.let { regions ->
            addRegionGroupHeader("광역시")
            regions.forEach { region ->
                val regionCard = createRegionCard(region)
                koreanRegionsContainer.addView(regionCard)
            }
        }

        // 도 지역
        groupedRegions[RegionType.PROVINCE]?.let { regions ->
            addRegionGroupHeader("도 지역")
            regions.forEach { region ->
                val regionCard = createRegionCard(region)
                koreanRegionsContainer.addView(regionCard)
            }
        }

        // 특별자치도
        groupedRegions[RegionType.SPECIAL]?.let { regions ->
            addRegionGroupHeader("특별자치도")
            regions.forEach { region ->
                val regionCard = createRegionCard(region)
                koreanRegionsContainer.addView(regionCard)
            }
        }

        // 해외 주요 도시
        groupedRegions[RegionType.INTERNATIONAL_MAJOR]?.let { regions ->
            addRegionGroupHeader("해외 주요 도시")
            regions.forEach { region ->
                val regionCard = createRegionCard(region)
                koreanRegionsContainer.addView(regionCard)
            }
        }

        // 해외 일반 도시
        groupedRegions[RegionType.INTERNATIONAL_CITY]?.let { regions ->
            addRegionGroupHeader("해외 도시")
            regions.forEach { region ->
                val regionCard = createRegionCard(region)
                koreanRegionsContainer.addView(regionCard)
            }
        }
    }

    private fun addRegionGroupHeader(groupName: String) {
        val headerView = TextView(this).apply {
            text = groupName
            textSize = 16f
            setTextColor(getColor(R.color.primary_color))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(16, 24, 16, 8)
        }
        koreanRegionsContainer.addView(headerView)
    }

    private fun createRegionCard(region: InternationalRegion): View {  // KoreanRegion → InternationalRegion
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
                // 기존 지역이 있는지 확인 후 다운로드
                checkExistingRegionsAndDownload(region)
            } else {
                showToast(languageManager.getLocalizedString(
                    "오프라인 지도 다운로드는 인터넷 연결이 필요합니다",
                    "Internet connection required for offline map download"
                ))
            }
        }

        return cardView
    }

    private fun checkExistingRegionsAndDownload(region: InternationalRegion) {
        lifecycleScope.launch {
            val existingRegions = offlineRegionManager.getDownloadedRegions()

            runOnUiThread {
                if (existingRegions.isNotEmpty()) {
                    // 기존 지역이 있는 경우 사용자에게 확인
                    showReplaceRegionDialog(region, existingRegions)
                } else {
                    // 기존 지역이 없는 경우 바로 다운로드
                    downloadRegion(region)
                }
            }
        }
    }

    private fun showReplaceRegionDialog(newRegion: KoreanRegion, existingRegions: List<DownloadedRegion>) {
        val regionName = if (languageManager.currentLanguage == "ko") newRegion.koreanName else newRegion.englishName
        val existingRegionNames = existingRegions.joinToString(", ") { it.name }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString("기존 지역 교체", "Replace Existing Region"))
            .setMessage(languageManager.getLocalizedString(
                "현재 다운로드된 지역: $existingRegionNames\n\n" +
                        "$regionName 지역을 다운로드하면 기존 지역이 모두 삭제됩니다.\n\n" +
                        "계속하시겠습니까?",
                "Currently downloaded: $existingRegionNames\n\n" +
                        "Downloading $regionName will delete all existing regions.\n\n" +
                        "Do you want to continue?"
            ))
            .setPositiveButton(languageManager.getLocalizedString("교체하기", "Replace")) { _, _ ->
                downloadRegion(newRegion)
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .setIcon(R.drawable.ic_download)
            .show()
    }

    // ✅ 기존 Mapbox 검색 (기존 performSearch를 이름만 변경)
    private fun performMapboxSearch(query: String) {
        // 기존 performSearch() 메서드의 내용을 그대로 복사
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
                                    // 추가 suggestions 처리
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

    private fun performSmartSearch() {
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

        Log.d("OfflineMapActivity", "Smart search for: $query")

        // 1단계: 정확한 매칭 검색
        val exactMatch = findExactMatch(query)
        if (exactMatch != null) {
            Log.d("OfflineMapActivity", "Found exact match: ${exactMatch.koreanName}")
            showExactMatchDialog(exactMatch, query)
            return
        }

        // 2단계: 부분 매칭 검색
        val partialMatches = findPartialMatches(query)
        if (partialMatches.isNotEmpty()) {
            Log.d("OfflineMapActivity", "Found ${partialMatches.size} partial matches")
            showMultipleMatchesDialog(partialMatches, query)
            return
        }

        // 3단계: 같은 국가 내 검색
        val countryMatches = findCountryMatches(query)
        if (countryMatches.isNotEmpty()) {
            Log.d("OfflineMapActivity", "Found ${countryMatches.size} country matches")
            showCountryMatchesDialog(countryMatches, query)
            return
        }

        // 4단계: 기존 Mapbox API 검색
        Log.d("OfflineMapActivity", "No predefined matches, using Mapbox search")
        performMapboxSearch(query)
    }


    // ✅ 정확한 매칭 검색
    private fun findExactMatch(query: String): InternationalRegion? {
        return allRegions.find { region ->
            // 도시 이름만 추출해서 정확히 매칭
            extractCityName(region.koreanName).equals(query, ignoreCase = true) ||
                    extractCityName(region.englishName).equals(query, ignoreCase = true) ||
                    // 전체 이름 매칭
                    region.koreanName.equals(query, ignoreCase = true) ||
                    region.englishName.equals(query, ignoreCase = true)
        }
    }

    // ✅ 부분 매칭 검색
    private fun findPartialMatches(query: String): List<InternationalRegion> {
        return allRegions.filter { region ->
            region.koreanName.contains(query, ignoreCase = true) ||
                    region.englishName.contains(query, ignoreCase = true) ||
                    extractCityName(region.koreanName).contains(query, ignoreCase = true) ||
                    extractCityName(region.englishName).contains(query, ignoreCase = true)
        }.take(5) // 최대 5개까지만
    }

    // ✅ 같은 국가 내 검색
    private fun findCountryMatches(query: String): List<InternationalRegion> {
        // 국가명으로 검색
        val countryKeywords = mapOf(
            "일본" to "일본",
            "japan" to "일본",
            "미국" to "미국",
            "america" to "미국",
            "usa" to "미국",
            "한국" to "대한민국",
            "korea" to "대한민국"
        )

        val targetCountry = countryKeywords[query.lowercase()]

        return if (targetCountry != null) {
            allRegions.filter { it.country == targetCountry }.take(8)
        } else {
            emptyList()
        }
    }

    // ✅ 도시 이름 추출 (괄호 제거)
    private fun extractCityName(fullName: String): String {
        // "도쿄 (Tokyo)" → "도쿄"
        // "Seoul Metropolitan Area" → "Seoul"
        return fullName.split(" ")[0].split("(")[0].trim()
    }

    // ✅ 정확한 매칭 다이얼로그
    private fun showExactMatchDialog(region: InternationalRegion, searchQuery: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString("검색 결과", "Search Result"))
            .setMessage(
                languageManager.getLocalizedString(
                    "검색어 '$searchQuery'와 일치하는 지역을 찾았습니다:\n\n" +
                            "📍 ${region.koreanName}\n" +
                            "🌏 ${region.country}\n" +
                            "📄 ${region.description}\n\n" +
                            "이 지역을 다운로드하시겠습니까?",

                    "Found a region matching '$searchQuery':\n\n" +
                            "📍 ${region.englishName}\n" +
                            "🌏 ${region.country}\n" +
                            "📄 ${region.description}\n\n" +
                            "Download this region?"
                )
            )
            .setPositiveButton(languageManager.getLocalizedString("다운로드", "Download")) { _, _ ->
                checkExistingRegionsAndDownload(region)
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .setNeutralButton(languageManager.getLocalizedString("다른 검색", "Other Search")) { _, _ ->
                performMapboxSearch(searchQuery)
            }
            .show()
    }

    // ✅ 여러 매칭 결과 다이얼로그
    private fun showMultipleMatchesDialog(regions: List<InternationalRegion>, searchQuery: String) {
        val regionNames = regions.map { region ->
            if (languageManager.currentLanguage == "ko") {
                "${region.koreanName} (${region.country})"
            } else {
                "${region.englishName} (${region.country})"
            }
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString(
                "'$searchQuery' 검색 결과 (${regions.size}개)",
                "'$searchQuery' Search Results (${regions.size})"
            ))
            .setItems(regionNames) { _, which ->
                val selectedRegion = regions[which]
                checkExistingRegionsAndDownload(selectedRegion)
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .setNeutralButton(languageManager.getLocalizedString("다른 검색", "Other Search")) { _, _ ->
                performMapboxSearch(searchQuery)
            }
            .show()
    }

    // ✅ 국가별 매칭 결과 다이얼로그
    private fun showCountryMatchesDialog(regions: List<InternationalRegion>, searchQuery: String) {
        val country = regions.firstOrNull()?.country ?: ""

        val regionNames = regions.map { region ->
            if (languageManager.currentLanguage == "ko") {
                "${region.koreanName}"
            } else {
                "${region.englishName}"
            }
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString(
                "$country 지역 (${regions.size}개)",
                "$country Regions (${regions.size})"
            ))
            .setMessage(languageManager.getLocalizedString(
                "'$searchQuery'로 검색하신 ${country}의 다운로드 가능한 지역들입니다:",
                "Available $country regions for '$searchQuery':"
            ))
            .setItems(regionNames) { _, which ->
                val selectedRegion = regions[which]
                checkExistingRegionsAndDownload(selectedRegion)
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .setNeutralButton(languageManager.getLocalizedString("다른 검색", "Other Search")) { _, _ ->
                performMapboxSearch(searchQuery)
            }
            .show()
    }

    private fun handleSearchResult(result: SearchResult) {
        val coordinate = result.coordinate
        val placeName = result.name

        // 검색된 위치 주변 지역을 다운로드하기 위한 CustomRegion 생성
        val customRegion = CustomRegion(
            name = placeName,
            centerLat = coordinate.latitude(),
            centerLon = coordinate.longitude(),
            // 검색된 지점 주변 약 100km 반경으로 확장
            minLon = coordinate.longitude() - 1.0,
            maxLon = coordinate.longitude() + 1.0,
            minLat = coordinate.latitude() - 0.7,
            maxLat = coordinate.latitude() + 0.7
        )

        runOnUiThread {
            // 기존 지역이 있는지 확인 후 다운로드
            checkExistingRegionsAndDownloadCustom(customRegion)
        }
    }

    private fun checkExistingRegionsAndDownloadCustom(customRegion: CustomRegion) {
        lifecycleScope.launch {
            val existingRegions = offlineRegionManager.getDownloadedRegions()

            runOnUiThread {
                if (existingRegions.isNotEmpty()) {
                    // 기존 지역이 있는 경우 사용자에게 확인
                    showReplaceCustomRegionDialog(customRegion, existingRegions)
                } else {
                    // 기존 지역이 없는 경우 바로 다운로드
                    downloadCustomRegion(customRegion)
                }
            }
        }
    }

    private fun showReplaceCustomRegionDialog(customRegion: CustomRegion, existingRegions: List<DownloadedRegion>) {
        val existingRegionNames = existingRegions.joinToString(", ") { it.name }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString("기존 지역 교체", "Replace Existing Region"))
            .setMessage(languageManager.getLocalizedString(
                "현재 다운로드된 지역: $existingRegionNames\n\n" +
                        "${customRegion.name} 지역을 다운로드하면 기존 지역이 모두 삭제됩니다.\n\n" +
                        "계속하시겠습니까?",
                "Currently downloaded: $existingRegionNames\n\n" +
                        "Downloading ${customRegion.name} will delete all existing regions.\n\n" +
                        "Do you want to continue?"
            ))
            .setPositiveButton(languageManager.getLocalizedString("교체하기", "Replace")) { _, _ ->
                downloadCustomRegion(customRegion)
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .setIcon(R.drawable.ic_download)
            .show()
    }

    private fun downloadRegion(region: InternationalRegion) {
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

    private fun showCancelDownloadDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString("다운로드 취소", "Cancel Download"))
            .setMessage(languageManager.getLocalizedString(
                "다운로드를 취소하시겠습니까?\n진행 중인 데이터는 삭제됩니다.",
                "Cancel the download?\nProgress will be lost."
            ))
            .setPositiveButton(languageManager.getLocalizedString("취소", "Cancel")) { _, _ ->
                isDownloadCancelled = true
                offlineRegionManager.cancelCurrentDownload()
                hideDownloadProgress()
                showToast(languageManager.getLocalizedString(
                    "다운로드가 취소되었습니다",
                    "Download cancelled"
                ))
            }
            .setNegativeButton(languageManager.getLocalizedString("계속", "Continue"), null)
            .show()
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
        cancelDownloadButton.isEnabled = true
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
        cancelDownloadButton.isEnabled = false
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
                "${region.name} 지역을 삭제하시겠습니까?\n삭제된 데이터는 복구할 수 없습니다.",
                "Delete ${region.name} region?\nDeleted data cannot be recovered."
            ))
            .setPositiveButton(languageManager.getLocalizedString("삭제", "Delete")) { _, _ ->
                deleteRegion(region)
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .setIcon(R.drawable.ic_close)
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

// 데이터 클래스들 - 지역 타입 추가
enum class RegionType {
    METROPOLITAN,        // 수도권
    CITY,               // 광역시
    PROVINCE,           // 도
    SPECIAL,            // 특별자치도
    INTERNATIONAL_MAJOR, // 해외 주요 도시
    INTERNATIONAL_CITY   // 해외 일반 도시
}

data class InternationalRegion(
    val koreanName: String,
    val englishName: String,
    val centerLon: Double,
    val centerLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double,
    val description: String,
    val type: RegionType,
    val country: String = ""
)

typealias KoreanRegion = InternationalRegion

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