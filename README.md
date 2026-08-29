# Programmable Virtual Camera for Android

An Android/AOSP implementation that exposes programmable image sources as a standard camera device.

Planned sources include a physical camera, local images and videos, and network streams. Frames will pass through a native real-time processing pipeline before being exposed to Camera2/WebRTC consumers through Android's virtual camera stack.

## Status

Project scaffolding and technical validation.

## Intended components

- Android virtual-camera owner system app
- Camera2/WebRTC consumer demo
- C++/JNI frame-processing pipeline
- EGL/OpenGL rendering and YUV/RGBA conversion
- ML Kit face-aware effects
- Cuttlefish integration and AOSP patches
- Latency, frame-drop, CPU, GPU, and memory benchmarks
