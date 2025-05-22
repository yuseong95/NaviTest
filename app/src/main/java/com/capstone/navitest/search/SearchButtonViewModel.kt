package com.capstone.navitest.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.util.Log

class SearchButtonViewModel : ViewModel() {

    // 기본 상태들
    private val _isSearchUIVisible = MutableStateFlow(false)
    val isSearchUIVisible: StateFlow<Boolean> = _isSearchUIVisible.asStateFlow()

    private val _navigationActive = MutableStateFlow(false)
    val navigationActive: StateFlow<Boolean> = _navigationActive.asStateFlow()

    private val _hasDestination = MutableStateFlow(false)

    // 검색 버튼 가시성은 다른 상태들을 조합하여 계산 - Flow<Boolean>로 선언
    val isSearchButtonVisible: Flow<Boolean> = combine(
        _isSearchUIVisible,
        _navigationActive,
        _hasDestination
    ) { searchUIVisible, navActive, hasDestination ->
        // 검색 UI가 열려있거나, 내비게이션이 활성화되어 있으면 버튼 숨김
        val shouldHide = searchUIVisible || navActive
        val shouldShow = !shouldHide

        Log.d("SearchButtonViewModel", "Button visibility calculation - searchUI: $searchUIVisible, navActive: $navActive, hasDestination: $hasDestination, result: $shouldShow")
        shouldShow
    }

    // 검색 UI 열기
    fun openSearchUI() {
        viewModelScope.launch {
            Log.d("SearchButtonViewModel", "Opening search UI")
            _isSearchUIVisible.value = true
        }
    }

    // 검색 UI 닫기
    fun closeSearchUI() {
        viewModelScope.launch {
            Log.d("SearchButtonViewModel", "Closing search UI")
            _isSearchUIVisible.value = false
        }
    }

    // 내비게이션 상태 설정
    fun setNavigationActive(active: Boolean) {
        viewModelScope.launch {
            Log.d("SearchButtonViewModel", "Setting navigation active: $active")
            _navigationActive.value = active
        }
    }

    // 목적지 설정 상태
    fun setHasDestination(hasDestination: Boolean) {
        viewModelScope.launch {
            Log.d("SearchButtonViewModel", "Setting has destination: $hasDestination")
            _hasDestination.value = hasDestination
        }
    }

    // 상태 초기화 (앱 시작시나 모든 것을 리셋할 때)
    fun resetToInitialState() {
        viewModelScope.launch {
            Log.d("SearchButtonViewModel", "Resetting to initial state")
            _isSearchUIVisible.value = false
            _navigationActive.value = false
            _hasDestination.value = false
        }
    }
}