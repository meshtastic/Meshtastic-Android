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
- Direct communication with Meshtastic hardware (via BLE, USB, TCP, MQTT)
- Decentralized text messaging across the mesh network
- Unified cross-platform notifications for messages and node events
- Adaptive node and contact management
- Offline map rendering and device positioning
- Device configuration and firmware updates
- Unified cross-platform debugging and packet inspection

## Key Architecture Goals
- Provide a robust, shared KMP core (`core:model`, `core:ble`, `core:repository`, `core:domain`, `core:data`, `core:network`, `core:service`) to support multiple platforms (Android, Desktop, iOS)
- Ensure offline-first functionality and resilient data persistence (Room KMP)
- Decouple UI and navigation logic into shared feature modules (`core:ui`, `feature:*`) using Compose Multiplatform