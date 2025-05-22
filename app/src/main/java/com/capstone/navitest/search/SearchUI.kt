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
import com.capstone.navitest.MainActivity
import com.capstone.navitest.R
import com.capstone.navitest.map.MarkerManager
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.geojson.Point
import com.mapbox.search.ResponseInfo
import com.mapbox.search.offline.OfflineResponseInfo
import com.mapbox.search.offline.OfflineSearchEngine
import com.mapbox.search.offline.OfflineSearchEngineSettings
import com.mapbox.search.offline.OfflineSearchResult
import com.mapbox.search.record.HistoryRecord
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.ui.view.DistanceUnitType
import com.mapbox.search.ui.view.SearchResultsView
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.adapter.engines.SearchEngineUiAdapter

class SearchUI(
    private val activity: MainActivity,
    private val searchManager: SearchManager,
    private val languageManager: LanguageManager,
    private val markerManager: MarkerManager,
    private val viewModel: SearchButtonViewModel
) {
    // UI 컴포넌트
    private val searchBar: EditText = activity.findViewById(R.id.searchEditText)
    private val searchButton: ImageButton = activity.findViewById(R.id.searchButton)
    private val searchContainer: LinearLayout = activity.findViewById(R.id.searchContainer)
    private val backButton: ImageButton = activity.findViewById(R.id.backButton)

    // Mapbox 검색 결과 뷰
    private val searchResultsView: SearchResultsView = activity.findViewById(R.id.searchResultsView)

    // SearchEngineUiAdapter
    private lateinit var searchEngineUiAdapter: SearchEngineUiAdapter

    init {
        setupUI()
    }

    private fun setupUI() {
        // SearchResultsView 초기화
        searchResultsView.initialize(
            SearchResultsView.Configuration(
                CommonSearchViewConfiguration(DistanceUnitType.METRIC)
            )
        )

        // SearchEngineUiAdapter 초기화 - 오프라인 검색 엔진 포함
        searchEngineUiAdapter = SearchEngineUiAdapter(
            view = searchResultsView,
            searchEngine = searchManager.searchEngine,
            offlineSearchEngine = OfflineSearchEngine.create(OfflineSearchEngineSettings())
        )

        // 리스너 객체 생성 - 익명 객체로 구현
        val searchListener = object : SearchEngineUiAdapter.SearchListener {
            override fun onSuggestionsShown(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                Log.d("SearchUI", "Suggestions shown: ${suggestions.size}")
            }

            override fun onSearchResultsShown(suggestion: SearchSuggestion, results: List<SearchResult>, responseInfo: ResponseInfo) {
                Log.d("SearchUI", "Search results shown: ${results.size}")
            }

            override fun onSuggestionSelected(searchSuggestion: SearchSuggestion): Boolean {
                Log.d("SearchUI", "Suggestion selected: ${searchSuggestion.name}")
                return false // 기본 동작 허용
            }

            override fun onSearchResultSelected(searchResult: SearchResult, responseInfo: ResponseInfo) {
                Log.d("SearchUI", "Result selected: ${searchResult.name}")

                // 선택한 결과로 목적지 설정
                val coordinate = searchResult.coordinate
                val resultName = searchResult.name

                val point = Point.fromLngLat(coordinate.longitude(), coordinate.latitude())
                searchManager.setDestinationFromPoint(point)
                markerManager.addMarker(point)

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

                // 검색 UI 숨기기 - ViewModel을 통해
                viewModel.closeSearchUI()
            }

            override fun onError(e: Exception) {
                Log.e("SearchUI", "Search error: ${e.message}", e)

                Toast.makeText(
                    activity,
                    languageManager.getLocalizedString(
                        "검색 중 오류가 발생했습니다: ${e.message}",
                        "Error during search: ${e.message}"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onFeedbackItemClick(responseInfo: ResponseInfo) {
                Log.d("SearchUI", "Feedback item clicked")
            }

            override fun onHistoryItemClick(historyRecord: HistoryRecord) {
                Log.d("SearchUI", "History item clicked (not used)")
            }

            override fun onOfflineSearchResultSelected(
                searchResult: OfflineSearchResult,
                responseInfo: OfflineResponseInfo
            ) {
                Log.d("SearchUI", "Offline search result selected (not used)")
            }

            override fun onOfflineSearchResultsShown(
                results: List<OfflineSearchResult>,
                responseInfo: OfflineResponseInfo
            ) {
                Log.d("SearchUI", "Offline search results shown (not used)")
            }

            override fun onPopulateQueryClick(
                suggestion: SearchSuggestion,
                responseInfo: ResponseInfo
            ) {
                Log.d("SearchUI", "Populate query clicked: ${suggestion.name}")
            }
        }

        // 리스너 등록
        searchEngineUiAdapter.addSearchListener(searchListener)

        // 나머지 UI 설정
        setupUIComponents()
    }

    private fun setupUIComponents() {
        // 검색 버튼 클릭 리스너
        searchButton.setOnClickListener {
            val query = searchBar.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }

        // 검색창 엔터 리스너
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
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

    // 메소드 수정
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
            // 실패시 ViewModel 상태도 원복
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
    }

    private fun setupSearchButton() {
        searchButton.setOnClickListener {
            val query = searchBar.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
    }

    // 뒤로가기 버튼 처리 - ViewModel 사용
    private fun setupBackButton() {
        backButton.setOnClickListener {
            viewModel.closeSearchUI()
        }
    }

    private fun performSearch(query: String) {
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

        // 검색 수행
        searchEngineUiAdapter.search(query)
    }

    // 네트워크 연결 상태 확인 - 최신 API만 사용
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

    // 검색 UI가 현재 표시 중인지 확인하는 메소드
    fun isSearchUIVisible(): Boolean {
        return searchContainer.visibility == View.VISIBLE
    }
}