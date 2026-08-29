# Programmable Virtual Camera

이미지, 영상, 실제 카메라 및 네트워크 스트림을 Android 표준 카메라 장치처럼 노출하는 AOSP 시스템 레벨 Virtual Camera.

## 문서

- 코딩 스타일: @docs/CODING_STYLE.md
- 개발 계획과 Phase 정의: `docs/PROJECT_PLAN.local.md` (로컬 전용)
- 현재 Phase 상세 계획: `docs/PHASE1_PLAN.local.md` (로컬 전용)
- 커밋과 PR 작성: `docs/COMMIT_AND_PR_STYLE.local.md` (로컬 전용)
- Codex와 Claude 사이 작업 전달: `docs/AGENT_HANDOFF.local.md` (로컬 전용)

## 작업 분담

- 코드: Claude Code
- 문서: Codex

같은 워킹트리를 공유하므로 서로의 담당 파일을 건드리지 않는다. `docs/AGENT_HANDOFF.local.md`만 두 Agent가 함께 사용하는 소통 창구다.

Claude Code는 구현을 시작하기 전에 코딩 스타일, 현재 Phase 계획과 handoff의 요청을 읽는다. 계획에 없는 아키텍처 변경이 필요하면 구현을 확대하지 않고 handoff에 판단이 필요한 내용을 남긴다.

## 현재 상태

Phase 1 - 앱 레벨 영상 소스 PoC. AOSP 수정 없이 로컬 PNG/MP4를 WebRTC 영상 프레임으로 내보내는 것이 목표.

- WebRTC: `io.github.webrtc-sdk:android` (prefix 없는 표준 변형, `org.webrtc` 네임스페이스)
- 검증 환경: Apple Silicon Android 에뮬레이터 (arm64-v8a)
- Cuttlefish와 AOSP 전체 빌드는 GCP Ubuntu x86-64 Spot VM에서 Phase 3부터 진행한다.

공개 저장소에는 실행 가능한 샘플, 공개 인터페이스, 문서와 배포 바이너리를 둔다. AOSP 패치와 native 핵심 구현은 `ProgrammableVirtualCamera-Core` 비공개 저장소에서 관리한다.

## 주의

- AOSP 전체 소스, 빌드 결과, 테스트 영상, signing material을 저장소에 넣지 않는다.
- 실행하지 않은 테스트나 측정하지 않은 성능을 문서나 커밋에 쓰지 않는다.
