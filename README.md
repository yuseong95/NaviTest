# Whisper + LLaMA3 기반 오프라인 네비게이션 앱

음성으로 경로를 안내받고, 인터넷이 없어도 사용 가능한 스마트 네비게이션!

---

## 프로젝트 개요

이 프로젝트는 **Whisper-small**과 **LLaMA3**를 활용한 음성 기반 네비게이션 앱입니다.  
사용자는 "위스퍼"라고 호출한 뒤 자연어로 목적지를 말하면, Whisper가 이를 텍스트로 변환하고, LLaMA3가 텍스트를 분석해 목적지를 파악한 후 **Mapbox 오프라인 네비게이션**을 통해 경로를 안내합니다.

>  **완전 오프라인 지원**: 음성 인식부터 경로 안내까지, 네트워크 없이도 동작합니다.

---
## 주요 기능

- 오프라인 음성 인식 (Whisper-small)
- 자연어 목적지 인식 및 해석 (LLaMA3)
- Mapbox 기반 오프라인 경로 안내
- "위스퍼" 호출어 인식 및 명령 처리
- 네트워크 연결 없이 완전 오프라인 동작 지원
---

##  기술 스택

## Environment & Platform

<p align="left">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/On--Device_AI-4CAF50?style=flat&logo=vercel&logoColor=white"/>
  <img src="https://img.shields.io/badge/Qualcomm-3253DC?style=flat&logo=qualcomm&logoColor=white"/>
</p>

## Languages & Frameworks

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Java-007396?style=flat&logo=java&logoColor=white"/>
  <img src="https://img.shields.io/badge/TensorFlow_Lite-FF6F00?style=flat&logo=tensorflow&logoColor=white"/>
</p>

## AI Models & Processing

<p align="left">
  <img src="https://img.shields.io/badge/OpenAI-412991?style=flat&logo=openai&logoColor=white"/>
  <img src="https://img.shields.io/badge/LLaMA3-111111?style=flat&logo=meta&logoColor=white"/>
  <img src="https://img.shields.io/badge/Whisper-00B2FF?style=flat&logo=sonos&logoColor=white"/>
</p>

## Maps & Navigation

<p align="left">
  <img src="https://img.shields.io/badge/Mapbox-000000?style=flat&logo=mapbox&logoColor=white"/>
</p>

## Collaboration & Communication

<p align="left">
  <img src="https://img.shields.io/badge/Git-F05032?style=flat&logo=git&logoColor=white"/>
  <img src="https://img.shields.io/badge/GitHub-181717?style=flat&logo=github&logoColor=white"/>
  <img src="https://img.shields.io/badge/Slack-4A154B?style=flat&logo=slack&logoColor=white"/>
  <img src="https://img.shields.io/badge/HuggingFace-FCC624?style=flat&logo=huggingface&logoColor=black"/>
</p>


---
## 프로젝트 구조

---
## 시스템 요구사항

- Android 13 (API 레벨 29) 이상 권장
- Qualcomm Snapdragon 800 이상 권장
- 최소 8GB RAM 이상 권장
- 최소 저장 공간 2GB 이상 (AI 모델 및 지도 데이터)

---

## 설치 및 실행 방법

```bash
git clone https://github.com/your-username/your-repo.git
cd your-repo
# Android Studio에서 프로젝트 열기
---
## 사용 예시

1. 앱을 실행합니다.
2. "위스퍼"라고 호출하여 음성 인식을 활성화합니다.
3. 자연어로 목적지를 말합니다. 예: 강남역으로 가줘, 서울역 어떻게 가
4. Whisper 모델이 음성을 텍스트로 변환합니다.
5. LLaMA3 모델이 변환된 텍스트를 분석하여 목적지를 파악합니다.
6. Mapbox 오프라인 네비게이션이 최적 경로를 계산하여 안내를 시작합니다.

---
