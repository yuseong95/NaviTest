package com.capstone.navitest

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.geojson.Point
import java.io.InputStreamReader

object LocationData {
    private val locationMap = mapOf(
        "hansung university" to Point.fromLngLat(127.0068, 37.5826),
        "seoul station" to Point.fromLngLat(126.9707, 37.5547),
        "gangnam station" to Point.fromLngLat(127.0276, 37.4979),
        "hongdae" to Point.fromLngLat(126.9239, 37.5563),
        "sinchon station" to Point.fromLngLat(126.9386, 37.5598),
        "city hall" to Point.fromLngLat(126.977, 37.5663),
        "itaewon" to Point.fromLngLat(126.9945, 37.5349),
        "gwanghwamun" to Point.fromLngLat(126.9769, 37.5714),
        "coex" to Point.fromLngLat(127.0592, 37.5116),
        "jamsil station" to Point.fromLngLat(127.1002, 37.5139),
        "namsan tower" to Point.fromLngLat(126.988208, 37.551224)
    )

    private var normalizationMap: Map<String, List<String>> = emptyMap()

    fun initialize(context: Context) {
        try {
            val inputStream = context.assets.open("location_normalization_map.json")
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)

            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            normalizationMap = Gson().fromJson(reader, type)

            Log.d("LocationData", "Normalization map loaded: ${normalizationMap.size} entries")
        } catch (e: Exception) {
            Log.e("LocationData", "Failed to load normalization map", e)
        }
    }

    fun getLocationPointFromText(text: String): Point? {
        val lowerText = text.lowercase().trim().replace(" ", "")

        Log.d("whisperDebug", lowerText)

        // 1. 정규화된 이름을 찾음
        val normalized = normalizationMap.entries.firstOrNull { (_, variants) ->
            variants.any { lowerText.contains(it) }
        }?.key

        // 2. 있으면 locationMap에서 가져옴
        if (normalized != null) {
            Log.d("whisperDebug", normalized)
            Log.d("whisperDebug", "${locationMap[normalized]}" )
            return locationMap[normalized]
        }

        // 3. 없으면 직접 keyword 매칭
        return locationMap.entries.firstOrNull { (keyword, _) ->
            lowerText.contains(keyword)
        }?.value
    }
}
