package com.capstone.navitest.search

import android.content.Context
import android.util.Log
import com.capstone.navitest.navigation.RouteManager
import com.capstone.navitest.ui.LanguageManager
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.result.SearchResult

class SearchManager(
    private val context: Context,
) {
    // SearchEngine 생성 - SearchUI에서 사용
    internal val searchEngine: SearchEngine by lazy {
        // 액세스 토큰 설정 - 간소화
        if (MapboxOptions.accessToken.isEmpty()) {
            MapboxOptions.accessToken = context.getString(com.capstone.navitest.R.string.mapbox_access_token)
        }

        SearchEngine.createSearchEngineWithBuiltInDataProviders(
            ApiType.GEOCODING,
            SearchEngineSettings()
        )
    }

    // 리소스 정리
    fun cleanup() {
        Log.d("SearchManager", "SearchManager cleanup completed")
    }
}