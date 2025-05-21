package com.capstone.navitest.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchButtonViewModel : ViewModel() {
    // 검색 버튼 표시 여부 상태
    private val _isVisible = MutableStateFlow(true)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    // 검색 UI 표시 여부 상태
    private val _isSearchUIVisible = MutableStateFlow(false)
    val isSearchUIVisible: StateFlow<Boolean> = _isSearchUIVisible.asStateFlow()

    // 검색 버튼 가시성 설정
    fun setSearchButtonVisibility(isVisible: Boolean) {
        viewModelScope.launch {
            _isVisible.value = isVisible
        }
    }

    // 검색 UI 열기
    fun openSearchUI() {
        viewModelScope.launch {
            _isSearchUIVisible.value = true
            // 검색 UI가 열리면 검색 버튼 숨기기
            _isVisible.value = false
        }
    }

    // 검색 UI 닫기
    fun closeSearchUI() {
        viewModelScope.launch {
            _isSearchUIVisible.value = false
            // 내비게이션 중이 아니라면 검색 버튼 다시 표시
            if (!_navigationActive.value) {
                _isVisible.value = true
            }
        }
    }

    // 내비게이션 활성화 상태
    private val _navigationActive = MutableStateFlow(false)
    val navigationActive: StateFlow<Boolean> = _navigationActive.asStateFlow()

    // 내비게이션 상태 설정
    fun setNavigationActive(active: Boolean) {
        viewModelScope.launch {
            _navigationActive.value = active
            // 내비게이션이 활성화되면 검색 버튼 숨기기
            if (active) {
                _isVisible.value = false
            } else if (!_isSearchUIVisible.value) {
                // 내비게이션이 비활성화되고 검색 UI도 표시되지 않으면 버튼 표시
                _isVisible.value = true
            }
        }
    }
}