package com.capstone.navitest.search

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.capstone.navitest.MainActivity
import com.capstone.navitest.R
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.geojson.Point
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.common.IsoCountryCode
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import java.util.concurrent.atomic.AtomicInteger

class SearchUI(
    private val activity: MainActivity,
    private val searchManager: SearchManager,
    private val languageManager: LanguageManager,
    private val viewModel: SearchButtonViewModel
) {
    // UI 컴포넌트
    private val searchBar: EditText = activity.findViewById(R.id.searchEditText)
    private val searchButton: ImageButton = activity.findViewById(R.id.searchButton)
    private val searchContainer: LinearLayout = activity.findViewById(R.id.searchContainer)
    private val backButton: ImageButton = activity.findViewById(R.id.backButton)

    // RecyclerView를 사용한 검색 결과 표시
    private val searchResultsRecyclerView: RecyclerView = activity.findViewById(R.id.searchResultsView)
    private lateinit var searchResultsAdapter: CombinedSearchResultsAdapter

    // 검색 상태 관리
    private var searchBoxResults: List<SearchResult> = emptyList()
    private var geocodingResults: List<SearchResult> = emptyList()
    private val pendingSearches = AtomicInteger(0) // 진행 중인 검색 수

    init {
        setupUI()
    }

    private fun setupUI() {
        // RecyclerView 설정
        setupRecyclerView()

        // UI 컴포넌트 설정
        setupUIComponents()
    }

    private fun setupRecyclerView() {
        // 람다 파라미터 타입 명시
        searchResultsAdapter = CombinedSearchResultsAdapter { combinedResult: SearchManager.CombinedSearchResult ->
            onSearchResultSelected(combinedResult.result)
        }

        searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = searchResultsAdapter
        }
    }

    private fun setupUIComponents() {
        // 검색 버튼 클릭 리스너
        searchButton.setOnClickListener {
            val query = searchBar.text.toString().trim()
            if (query.isNotEmpty()) {
                performDualSearch(query)
            }
        }

        // 검색창 엔터 리스너
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    performDualSearch(query)
                }
                true
            } else {
                false
            }
        }

        // 뒤로가기 버튼 리스너
        backButton.setOnClickListener {
            Log.d("SearchUI", "Back button clicked")
            viewModel.closeSearchUI()
        }

        // 초기 상태는 검색 UI 숨김
        searchContainer.visibility = View.GONE
    }

    fun showSearchUI() {
        Log.d("SearchUI", "Showing search UI")

        // 네트워크 연결 확인
        if (!isNetworkAvailable()) {
            Toast.makeText(
                activity,
                languageManager.getLocalizedString(
                    "검색 기능은 온라인 상태에서만 사용 가능합니다.",
                    "Search feature is only available when online."
                ),
                Toast.LENGTH_LONG
            ).show()
            viewModel.closeSearchUI()
            return
        }

        searchContainer.visibility = View.VISIBLE
        searchBar.requestFocus()
        showKeyboard(searchBar)
    }

    fun hideSearchUI() {
        Log.d("SearchUI", "Hiding search UI")
        searchContainer.visibility = View.GONE
        hideKeyboard()
        searchBar.text.clear()
        searchResultsAdapter.clearResults()

        // 검색 상태 초기화
        searchBoxResults = emptyList()
        geocodingResults = emptyList()
        pendingSearches.set(0)
    }

    private fun performDualSearch(query: String) {
        // 네트워크 연결 확인
        if (!isNetworkAvailable()) {
            Toast.makeText(
                activity,
                languageManager.getLocalizedString(
                    "검색 기능은 온라인 상태에서만 사용 가능합니다.",
                    "Search feature is only available when online."
                ),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // 키보드 숨기기
        hideKeyboard()

        Log.d("SearchUI", "Performing dual search for: $query")

        // 검색 상태 초기화
        searchBoxResults = emptyList()
        geocodingResults = emptyList()
        pendingSearches.set(2) // Search Box + Geocoding = 2개

        // 기존 결과 클리어
        searchResultsAdapter.clearResults()

        // 1. Search Box API 검색 (POI/랜드마크)
        performSearchBoxSearch(query)

        // 2. Geocoding API 검색 (주소)
        performGeocodingSearch(query)
    }

    private fun performSearchBoxSearch(query: String) {
        Log.d("SearchUI", "Performing Search Box search for: $query")

        // ✅ 개선된 SearchOptions - 더 넓은 범위, 더 많은 결과
        val searchOptions = SearchOptions.Builder()
            .countries(listOf(IsoCountryCode.SOUTH_KOREA))
            .languages(listOf(
                if (languageManager.currentLanguage == "ko") IsoLanguageCode.KOREAN else IsoLanguageCode.ENGLISH
            ))
            .limit(10) // ✅ 10개로 증가
            // ✅ 현재 위치 기준이 아닌 일반 검색을 위해 proximity 제거
            .build()

        searchManager.searchBoxEngine.search(
            query = query,
            options = searchOptions,
            callback = object : SearchSuggestionsCallback {
                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    Log.d("SearchUI", "SearchBox suggestions: ${suggestions.size}")

                    if (suggestions.isNotEmpty()) {
                        // ✅ 모든 suggestions 처리 (최대 10개)
                        processAllSuggestions(suggestions, "SearchBox")
                    } else {
                        onSearchBoxCompleted(emptyList())
                    }
                }

                override fun onError(e: Exception) {
                    Log.e("SearchUI", "SearchBox suggestions error: ${e.message}", e)
                    onSearchBoxCompleted(emptyList())
                }
            }
        )
    }

    private fun performGeocodingSearch(query: String) {
        Log.d("SearchUI", "Performing Geocoding search for: $query")

        // ✅ 개선된 SearchOptions - 더 넓은 범위, 더 많은 결과
        val searchOptions = SearchOptions.Builder()
            .countries(listOf(IsoCountryCode.SOUTH_KOREA))
            .languages(listOf(
                if (languageManager.currentLanguage == "ko") IsoLanguageCode.KOREAN else IsoLanguageCode.ENGLISH
            ))
            .limit(10) // ✅ 10개로 증가
            .build()

        searchManager.geocodingEngine.search(
            query = query,
            options = searchOptions,
            callback = object : SearchSuggestionsCallback {
                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    Log.d("SearchUI", "Geocoding suggestions: ${suggestions.size}")

                    if (suggestions.isNotEmpty()) {
                        // ✅ 모든 suggestions 처리 (최대 10개)
                        processAllSuggestions(suggestions, "Geocoding")
                    } else {
                        onGeocodingCompleted(emptyList())
                    }
                }

                override fun onError(e: Exception) {
                    Log.e("SearchUI", "Geocoding suggestions error: ${e.message}", e)
                    onGeocodingCompleted(emptyList())
                }
            }
        )
    }

    // ✅ 새로운 메서드: 모든 suggestions를 병렬로 처리
    private fun processAllSuggestions(suggestions: List<SearchSuggestion>, source: String) {
        val allResults = mutableListOf<SearchResult>()
        val remainingSuggestions = AtomicInteger(suggestions.size)

        Log.d("SearchUI", "Processing ${suggestions.size} suggestions from $source")

        suggestions.forEach { suggestion ->
            val engine = if (source == "SearchBox") searchManager.searchBoxEngine else searchManager.geocodingEngine

            engine.select(
                suggestion = suggestion,
                callback = object : SearchSelectionCallback {
                    override fun onResult(
                        suggestion: SearchSuggestion,
                        result: SearchResult,
                        responseInfo: ResponseInfo
                    ) {
                        synchronized(allResults) {
                            allResults.add(result)
                        }
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }

                    override fun onResults(
                        suggestion: SearchSuggestion,
                        results: List<SearchResult>,
                        responseInfo: ResponseInfo
                    ) {
                        synchronized(allResults) {
                            allResults.addAll(results)
                        }
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }

                    override fun onSuggestions(
                        suggestions: List<SearchSuggestion>,
                        responseInfo: ResponseInfo
                    ) {
                        // 일반적으로 빈 구현
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }

                    override fun onError(e: Exception) {
                        Log.e("SearchUI", "$source select error: ${e.message}", e)
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }
                }
            )
        }
    }

    private fun checkAndCompleteSearch(
        source: String,
        allResults: MutableList<SearchResult>,
        remainingSuggestions: AtomicInteger
    ) {
        val remaining = remainingSuggestions.decrementAndGet()
        if (remaining == 0) {
            // 모든 suggestions 처리 완료
            Log.d("SearchUI", "$source completed with ${allResults.size} total results")

            when (source) {
                "SearchBox" -> onSearchBoxCompleted(allResults.toList())
                "Geocoding" -> onGeocodingCompleted(allResults.toList())
            }
        }
    }

    private fun onSearchBoxCompleted(results: List<SearchResult>) {
        searchBoxResults = results
        Log.d("SearchUI", "SearchBox search completed with ${results.size} results")

        // 결과 로깅
        results.forEachIndexed { index, result ->
            Log.d("SearchUI", "SearchBox[$index]: ${result.name} - ${result.address?.formattedAddress()}")
        }

        // 남은 검색 수 감소
        val remaining = pendingSearches.decrementAndGet()

        // 즉시 결과 업데이트 (빠른 응답성)
        updateCombinedResults()

        // 모든 검색이 완료되었는지 확인
        if (remaining == 0) {
            onAllSearchesCompleted()
        }
    }

    private fun onGeocodingCompleted(results: List<SearchResult>) {
        geocodingResults = results
        Log.d("SearchUI", "Geocoding search completed with ${results.size} results")

        // 결과 로깅
        results.forEachIndexed { index, result ->
            Log.d("SearchUI", "Geocoding[$index]: ${result.name} - ${result.address?.formattedAddress()}")
        }

        // 남은 검색 수 감소
        val remaining = pendingSearches.decrementAndGet()

        // 즉시 결과 업데이트 (빠른 응답성)
        updateCombinedResults()

        // 모든 검색이 완료되었는지 확인
        if (remaining == 0) {
            onAllSearchesCompleted()
        }
    }

    private fun updateCombinedResults() {
        activity.runOnUiThread {
            val combinedResults = mutableListOf<SearchManager.CombinedSearchResult>()

            // ✅ Search Box 결과 추가 (POI/랜드마크 우선, 유명도순 정렬)
            searchBoxResults.forEach { result ->
                combinedResults.add(
                    SearchManager.CombinedSearchResult(
                        result = result,
                        type = SearchManager.SearchResultType.POI,
                        source = "Search Box"
                    )
                )
            }

            // Geocoding 결과 추가 (주소)
            geocodingResults.forEach { result ->
                // 중복 제거: 같은 이름이나 비슷한 좌표의 결과는 제외
                val isDuplicate = combinedResults.any { existing ->
                    isSimilarResult(existing.result, result)
                }

                if (!isDuplicate) {
                    combinedResults.add(
                        SearchManager.CombinedSearchResult(
                            result = result,
                            type = SearchManager.SearchResultType.ADDRESS,
                            source = "Geocoding"
                        )
                    )
                }
            }

            // ✅ 결과 정렬: POI 우선, 그 다음 주소 (유명도/관련성 고려)
            combinedResults.sortWith(compareBy<SearchManager.CombinedSearchResult> { it.type.ordinal }
                .thenBy { it.result.name.length }) // 이름이 짧은 것이 더 유명한 랜드마크일 가능성

            Log.d("SearchUI", "Updated combined results: ${combinedResults.size} total (POI: ${searchBoxResults.size}, Address: ${geocodingResults.filter { geocoding -> !combinedResults.any { combined -> combined.source == "Search Box" && isSimilarResult(combined.result, geocoding) } }.size})")
            searchResultsAdapter.setResults(combinedResults)
        }
    }

    private fun onAllSearchesCompleted() {
        Log.d("SearchUI", "All searches completed")

        activity.runOnUiThread {
            val totalResults = searchBoxResults.size + geocodingResults.size

            if (totalResults == 0) {
                Toast.makeText(
                    activity,
                    languageManager.getLocalizedString(
                        "검색 결과가 없습니다.",
                        "No search results found."
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Log.d("SearchUI", "Search completed - Total: $totalResults (SearchBox: ${searchBoxResults.size}, Geocoding: ${geocodingResults.size})")
            }
        }
    }

    // 결과 유사성 판단 (중복 제거용)
    private fun isSimilarResult(result1: SearchResult, result2: SearchResult): Boolean {
        // 이름이 같으면 중복으로 간주
        if (result1.name.equals(result2.name, ignoreCase = true)) {
            return true
        }

        // 좌표가 매우 가까우면 중복으로 간주 (100m 이내)
        val coord1 = result1.coordinate
        val coord2 = result2.coordinate
        val distance = calculateDistance(
            coord1.latitude(), coord1.longitude(),
            coord2.latitude(), coord2.longitude()
        )

        return distance < 0.1 // 100m 이내
    }

    // 두 좌표 간의 거리 계산 (km)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // 지구 반지름 (km)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    private fun onSearchResultSelected(searchResult: SearchResult) {
        Log.d("SearchUI", "Result selected: ${searchResult.name}")

        // 선택한 결과로 목적지 설정
        val coordinate = searchResult.coordinate
        val resultName = searchResult.name

        val point = Point.fromLngLat(coordinate.longitude(), coordinate.latitude())

        // MainActivity에 목적지 설정
        activity.setDestinationFromSearch(point)

        // ViewModel에 목적지 설정 상태 알림
        viewModel.setHasDestination(true)

        // 목적지 설정 메시지
        Toast.makeText(
            activity,
            languageManager.getLocalizedString(
                "$resultName 목적지로 설정됨",
                "$resultName set as destination"
            ),
            Toast.LENGTH_SHORT
        ).show()

        // 검색 UI 숨기기
        viewModel.closeSearchUI()
    }

    // 네트워크 연결 상태 확인
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showKeyboard(view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity.currentFocus ?: View(activity)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}