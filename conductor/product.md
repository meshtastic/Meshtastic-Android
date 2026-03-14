# Initial Concept
A tool for using Android with open-source mesh radios.

# Product Guide

## Overview
Meshtastic-Android is a Kotlin Multiplatform (KMP) application designed to facilitate communication over off-grid, decentralized mesh networks using open-source hardware radios.

## Target Audience
- Off-grid communication enthusiasts and hobbyists
- Outdoor adventurers needing reliable communication without cellular networks
- Emergency response and disaster relief teams

## Core Features
- Direct communication with Meshtastic hardware (via BLE, USB, TCP)
- Decentralized text messaging across the mesh network
- Adaptive node and contact management
- Offline map rendering and device positioning
- Device configuration and firmware updates

## Key Architecture Goals
- Provide a robust, shared KMP core (`core:model`, `core:ble`, `core:repository`, `core:domain`, `core:data`, `core:network`) to support multiple platforms (Android, Desktop, iOS)
- Ensure offline-first functionality and resilient data persistence (Room KMP)
- Decouple UI logic into shared components (`core:ui`, `feature:*`) using Compose Multiplatform