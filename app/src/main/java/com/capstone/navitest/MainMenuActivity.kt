// src/main/java/com/capstone/navitest/MainMenuActivity.kt
package com.capstone.navitest

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        // 바텀 네비게이션 뷰
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // TODO: 홈 화면 처리 (fragment 전환 등)
                    true
                }
                R.id.nav_ai -> {
                    // AI 채팅 모듈(App 모듈)로 이동
                    val chatIntent = Intent(this@MainMenuActivity,
                        MainActivityid::class.java)
                    startActivity(chatIntent)
                    true
                }
                R.id.nav_navigation -> {
                    // 네비게이션 시작용 액티비티로 이동
                    // 예: MainActivityDI 라는 이름이라면
                    val navIntent = Intent(this@MainMenuActivity,
                        MainActivity::class.java)
                    startActivity(navIntent)
                    true
                }
                else -> false
            }
        }
    }
}
