# WebRTC의 native 코드는 Java 클래스와 메서드를 이름으로 찾는다.
# R8이 이름을 바꾸거나 지우면 빌드는 통과하고 실행 중에 죽는다.
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# JNI_OnLoad가 org.jni_zero.JniInit을 이름으로 조회한다.
# 이 규칙이 없으면 .so는 정상 로드된 뒤 ClassNotFoundException으로 프로세스가 죽는다.
-keep class org.jni_zero.** { *; }
-keepclassmembers class org.jni_zero.** { *; }
-dontwarn org.jni_zero.**
