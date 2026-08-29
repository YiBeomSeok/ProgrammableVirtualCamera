# Programmable Virtual Camera for Android

Programmable Virtual Camera exposes images, videos, network streams, and physical cameras through a common Android camera pipeline.

The project starts with an application-level WebRTC prototype and will expand toward an AOSP virtual camera that Camera2 and WebRTC applications can use as a standard camera source.

## Status

Early technical validation. There is no usable release yet.

## Planned capabilities

- Switch between a physical camera, image, video, and network stream
- Process frames through a native real-time pipeline
- Apply face-aware effects using ML Kit metadata
- Expose processed frames through the Android virtual camera stack
- Measure latency, frame drops, CPU, GPU, and memory usage

## Repository scope

This repository will contain:

- Runnable sample applications
- Public interfaces and integration guides
- Versioned APK, AAR, and native binary releases
- Benchmark and compatibility results

The internal AOSP patches and native processing implementation are developed separately. Public samples will consume versioned binaries, so they can be cloned and run without including the core source.
