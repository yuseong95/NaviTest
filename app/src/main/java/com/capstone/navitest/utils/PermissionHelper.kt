package com.capstone.navitest.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.capstone.navitest.MainActivity

class PermissionHelper(private val activity: MainActivity, private val callback: PermissionCallback) {

    private val locationPermissionRequest = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                activity.initializeAfterPermissionGranted()
            }
            else -> {
                showPermissionDeniedMessage()
            }
        }
    }

    fun checkLocationPermissions() {
        if (hasLocationPermissions()) {
            activity.initializeAfterPermissionGranted()
        } else {
            requestLocationPermissions()
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showPermissionDeniedMessage() {
        // 언어 설정에 따른 메시지 출력
        val message = getLocalizedMessage()
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun getLocalizedMessage(): String {
        // 언어 매니저로부터 현재 언어 설정을 가져와 메시지 반환
        // 임시 구현
        return "위치 권한이 거부되었습니다. 설정에서 권한을 활성화해주세요."
    }

    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }
}