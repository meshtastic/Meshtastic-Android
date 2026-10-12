---
title: This Device as a Node
parent: User Guide
nav_order: 23
last_updated: 2026-10-12
description: Run the mesh node on your phone or desktop itself, with no radio attached, over Bluetooth, Wi-Fi, a LoRa stick, sound and more.
aliases:
  - node
  - phone node
  - in-process
  - bearers
  - this device
---

# This Device as a Node

Your phone or desktop can be the mesh node itself. Instead of connecting to a radio, the app runs a node in-process: it holds its own keys and node number, hears and sends packets over whatever your device has, and shows up in the mesh like any other node. The rest of the app works exactly as it does with a radio.

## Selecting it

1. Open the **Connect** tab.
2. Pick **This device** in the transport selector.
3. Tap the **This device** entry.

The first time, the node creates its keys and names itself from them. Rename it under **Settings → User**. Your device stays the selected connection across restarts, and the app reconnects to the node on boot as it would to a radio.

Only one connection is active at a time. Selecting a radio stops the node; selecting **This device** again starts it with the same identity and settings.

## Bearers

A bearer is one medium the node carries packets over. Switch each one on or off; the node stays up with whatever is enabled, and a bearer that is off releases its radio or socket.

Bearers that firmware also has are switched under **Settings → Network configuration**, with the same switches a radio shows, or on the **This device** card in the Connect tab. Both write the same setting, so either place shows the other's change:

| Switch | What it does |
| --- | --- |
| **UDP broadcasting** | Reaches every Meshtastic node on the same Wi-Fi or wired network, including radios with UDP enabled and `meshtasticd`. |
| **BLE Broadcast** | Carries packets in Bluetooth advertisements. Works between phones and desktops, and with radios running firmware that carries it. |
| **BLE GATT Peer** | Connects to nearby nodes over Bluetooth as a mesh peer, in both directions. |

**LoRa** follows the LoRa region, as on a radio, and has no switch on the card: set a region under **Settings → LoRa configuration** with a supported LoRa stick plugged in and the node is on the air. With no region set, the stick is idle in both directions. Transmit power is held low because the stick is powered by the device.

Bearers that only this device has are switched only on the **This device** card:

| Switch | What it does | Needs |
| --- | --- | --- |
| **Wi-Fi Aware** | Phone to phone over Wi-Fi with no network and no Bluetooth. Android only. Does not run while the phone is a hotspot. | Nearby devices permission |
| **Audio** | Packets as sound, to anything in earshot. Keeps the microphone open while on. | Microphone permission |
| **Video** | Packets as QR codes on screen, read by another device's camera. Runs only while the app is open. | Camera permission |

The card shows every bearer with its state. A bearer that is on but not running says why: a permission to grant, a stick not attached, or a medium this device lacks. Tap **Grant** where one is offered.

## Permissions

The node asks for the permissions its enabled bearers need, and nothing more: Bluetooth (scan, connect and advertise) for the Bluetooth bearers, local network access for UDP, nearby devices for Wi-Fi Aware, microphone for audio, camera for video. A denied permission turns that bearer's row into a request; the others keep running.

## The LoRa stick

A CH341A-based LoRa stick (Meshtadpole, meshstick, meshtoad) plugs into the phone over USB-C with an OTG adapter, or into a desktop's USB port. Android asks once for USB permission when the stick is attached. The phone powers the stick, so it does not charge while one is plugged in.

## Relaying

By default the node forwards nothing for anyone else. To relay, set **Rebroadcast mode** under **Settings → Device configuration**, as on a radio. A phone with a LoRa stick and a Bluetooth bearer enabled then bridges nearby phones onto the LoRa mesh.

## Your identity

The node's keys live on this device only. To carry them to another device, export a device profile under **Settings → Export configuration** and import it there; the private key in the security section moves the identity. Uninstalling the app deletes the node's keys.

**Factory reset** under **Settings → Administration** clears settings and known nodes and keeps the identity. A full reset also replaces the keys, so the node comes back as a new node.

## Battery

Bluetooth scanning and a plugged-in stick cost battery. The node slows its Bluetooth scan when the screen is off and resumes when you return. Switch off bearers you are not using.

## Desktop

On the desktop app the same **This device** entry runs the node on your computer: UDP everywhere, Bluetooth on Linux and macOS, a stick over USB, audio and video. Each data directory is its own node, so two app instances with different data directories are two nodes.
