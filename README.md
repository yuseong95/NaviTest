# Whisper + LLaMA3 기반 오프라인 네비게이션 앱

음성으로 경로를 안내받고, 인터넷이 없어도 사용 가능한 스마트 네비게이션!

---

## 프로젝트 개요

이 프로젝트는 **Whisper-small**과 **LLaMA3**를 활용한 음성 기반 네비게이션 앱입니다.  
사용자는 "위스퍼"라고 호출한 뒤 자연어로 목적지를 말하면, Whisper가 이를 텍스트로 변환하고, LLaMA3가 텍스트를 분석해 목적지를 파악한 후 **Mapbox 오프라인 네비게이션**을 통해 경로를 안내합니다.

> 📡 **완전 오프라인 지원**: 음성 인식부터 경로 안내까지, 네트워크 없이도 동작합니다.

---

##  주요 기능

### 🗣️ 1. 음성 기반 네비게이션
- "위스퍼" 호출로 음성 인식 시작
- 예시: "위스퍼! 강남역 어떻게 가야 해?"
- Whisper → 텍스트 변환 → LLaMA3 분석 → 목적지 추출
- Mapbox 네비게이션으로 경로 안내

### 🗺️ 2. 오프라인 지도 & 검색
- **Mapbox SDK** 사용
- 미리 지도 타일 다운로드 가능
- 장소 검색 기능 포함 (오프라인에서도 장소 탐색 가능)

### 🚗 3. 다양한 이동 수단 지원
- 차로 이동 (Driving)
- 도보 이동 (Walking)
- 자전거 이동 (Cycling)
- 도착 시간 및 실시간 위치/방향 안내 제공

### 💬 4. 오프라인 대화형 AI
- 인터넷 연결 없이도 작동하는 **LLaMA3 기반 챗봇**
- 사용자의 질문에 자연스럽게 응답 가능

---

## 🛠️ 기술 스택

| 기술          | 설명                                     |
|---------------|------------------------------------------|
| Whisper-small | 음성 인식 (on-device)                     |
| LLaMA3        | 자연어 처리 및 사용자 질의 이해            |
| Mapbox SDK    | 지도 표시, 경로 탐색, 오프라인 지도 지원    |
| Kotlin / Java | 안드로이드 앱 개발                        |
| TFLite        | Whisper 및 LLaMA3 모델의 on-device 추론 실행 |
<img src="https://img.shields.io/badge/java-007396?style=for-the-badge&logo=java&logoColor=white">
---

## 📦 설치 및 실행 방법

bash
git clone https://github.com/your-username/your-repo.git
cd your-repo
# Android Studio에서 프로젝트 열기
