# Programmable Virtual Camera Public

실행 가능한 샘플, 공개 인터페이스, 사용 문서와 배포 바이너리를 관리하는 Public 저장소다.

Workspace의 `public/` 경로에 있을 때는 작업 전에 상위 `../CLAUDE.md`, `../docs/PHASE1_PLAN.local.md`와 `../docs/AGENT_HANDOFF.local.md`를 읽는다. 독립적으로 클론한 환경에서는 이 저장소의 공개 범위와 코딩 스타일만 적용한다.

코딩 스타일은 @docs/CODING_STYLE.md를 따른다.

## 작업 분담

- 코드: Claude Code
- 문서: Codex

Workspace에서는 서로의 담당 파일을 건드리지 않는다. 공통 작업 요청과 결과는 상위 `../docs/AGENT_HANDOFF.local.md`에서만 주고받는다.

## 현재 상태

Phase 1 - 앱 레벨 영상 소스 PoC. AOSP 수정 없이 로컬 PNG/MP4를 WebRTC 영상 프레임으로 내보내는 것이 목표.

- WebRTC: `io.github.webrtc-sdk:android` (prefix 없는 표준 변형, `org.webrtc` 네임스페이스)
- 검증 환경: Apple Silicon Android 에뮬레이터 (arm64-v8a)
- Cuttlefish와 AOSP 전체 빌드는 GCP Ubuntu x86-64 Spot VM에서 Phase 3부터 진행한다.

Public 샘플은 배포된 versioned binary를 사용하며 비공개 Core 소스에 직접 의존하지 않는다.

## 주의

- AOSP 전체 소스, 빌드 결과, 테스트 영상, signing material을 저장소에 넣지 않는다.
- 로컬 계획, Agent handoff와 서버 운영 문서를 이 저장소에 복사하지 않는다.
- 실행하지 않은 테스트나 측정하지 않은 성능을 문서나 커밋에 쓰지 않는다.
