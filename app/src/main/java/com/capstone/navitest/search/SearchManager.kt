package com.capstone.navitest.search

import android.content.Context
import android.util.Log
import com.capstone.navitest.navigation.RouteManager
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.common.AsyncOperationTask
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion

class SearchManager(
    private val context: Context,
    private val routeManager: RouteManager,
    private val languageManager: LanguageManager
) {
    // SearchEngine 생성 - 공식 가이드에 따른 방식
    internal val searchEngine: SearchEngine by lazy {
        // 액세스 토큰 설정 - 이는 앱 시작 시 MainActivity에서 이미 설정되었을 수 있음
        if (MapboxOptions.accessToken.isNullOrEmpty()) {
            MapboxOptions.accessToken = context.getString(com.capstone.navitest.R.string.mapbox_access_token)
        }

        SearchEngine.createSearchEngineWithBuiltInDataProviders(
            ApiType.GEOCODING,
            SearchEngineSettings()  // 기본 생성자 사용
        )
    }

    // 검색 요청 태스크 트래킹을 위한 변수
    private var searchRequestTask: AsyncOperationTask? = null
    private var selectSuggestionTask: AsyncOperationTask? = null

    fun searchForPlaces(query: String, callback: SearchResultsCallback) {
        // 기존 검색 작업 취소
        searchRequestTask?.cancel()

        // 언어 코드 변환 (ko -> KOREAN, en -> ENGLISH)
        val languageCode = when (languageManager.currentLanguage.lowercase()) {
            "ko" -> IsoLanguageCode.KOREAN
            "en" -> IsoLanguageCode.ENGLISH
            else -> IsoLanguageCode.ENGLISH
        }

        // 최신 API에 맞게 SearchOptions 설정
        val options = SearchOptions(
            languages = listOf(languageCode),
            limit = 5
        )

        // SearchSuggestionsCallback 사용 (Mapbox API에 맞게)
        searchRequestTask = searchEngine.search(
            query,
            options,
            object : SearchSuggestionsCallback {
                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    Log.d("SearchManager", "Search suggestions: ${suggestions.size}")
                    callback.onSearchSuggestions(suggestions)

                    // 제안이 있으면 첫 번째 제안을 자동으로 선택하여 결과를 가져올 수도 있음
                    /*
                    if (suggestions.isNotEmpty()) {
                        selectSuggestion(suggestions[0], object : SearchSelectionCallback {
                            override fun onResult(suggestion: SearchSuggestion, result: SearchResult, responseInfo: ResponseInfo) {
                                callback.onSearchResults(listOf(result))
                            }
                            override fun onError(suggestion: SearchSuggestion, e: Exception) {
                                callback.onSearchError(e.message ?: "Error selecting suggestion")
                            }
                            // 기타 필요한 오버라이드
                        })
                    }
                    */
                }

                override fun onError(e: Exception) {
                    Log.e("SearchManager", "Search error: ${e.message}")
                    callback.onSearchError(e.message ?: "Unknown error")
                }
            }
        )
    }

    fun selectSuggestion(suggestion: SearchSuggestion, callback: SearchSelectionCallback) {
        // 기존 선택 작업 취소
        selectSuggestionTask?.cancel()

        // 선택 작업 실행 및 트래킹
        selectSuggestionTask = searchEngine.select(suggestion, callback)
    }

    fun setDestinationFromSearchResult(searchResult: SearchResult): Point {
        // SearchResult에서 라우팅 가능한 포인트를 가져오거나 기본 좌표 사용
        val destination = searchResult.routablePoints?.firstOrNull()?.point
            ?: searchResult.coordinate

        // 경로 관리자에 목적지 설정
        routeManager.setDestination(destination)

        return destination
    }

    // 리소스 정리 메소드
    fun cleanup() {
        // 진행 중인 작업 취소
        searchRequestTask?.cancel()
        selectSuggestionTask?.cancel()
    }

    // 콜백 인터페이스
    interface SearchResultsCallback {
        fun onSearchResults(results: List<SearchResult>)
        fun onSearchSuggestions(suggestions: List<SearchSuggestion>)
        fun onSearchError(message: String)
    }

    fun setDestinationFromPoint(point: Point): Point {
        // 경로 관리자에 목적지 설정
        routeManager.setDestination(point)
        return point
    }
}