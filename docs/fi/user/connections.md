---
title: Yhteydet
nav_order: 2
last_updated: 2026-10-21
description: Connect your phone or desktop to a Meshtastic node over Bluetooth, USB, or TCP/IP.
aliases:
  - bluetooth
  - usb
  - tcp
  - pariliitos
---

# Yhteydet

Meshtastic supports multiple transport methods to communicate between your phone or desktop and a node.

## Bluetooth (BLE)

Bluetooth Low Energy on oletus ja yleisin yhteystapa Androidilla.

### Radion pariliittäminen

1. Power on your node. Most nodes advertise over Bluetooth as soon as they boot, so there is no pairing mode to enter. Nodes with a color touchscreen ship with Bluetooth switched off, so turn it on from the node's own on-screen menu first.
2. Avaa sovellus ja siirry **Yhdistä**-välilehdelle.
3. Tap **Scan for Bluetooth devices**. Nearby Meshtastic nodes appear in the list.
4. Select your node from the list.
5. Android asks you to pair. If your node has a screen, it shows a six-digit PIN. Type that into the Android dialog. If your node has no screen, the PIN is `123456`.

![Scanning for Bluetooth devices, with a discovered node in the list](../../assets/screenshots/connections_bluetooth_scan.png)

To change the pairing method, or to turn Bluetooth on for a node that ships with it off, open **Settings → Device configuration → Bluetooth**. See [Settings — Radio & User](settings-radio-user). For more information, see [Bluetooth configuration](https://meshtastic.org/docs/configuration/radio/bluetooth) on meshtastic.org.

Vaihda bluetooth-, verkko- ja USB-yhteyksien välillä käyttämällä yhteyskortin alapuolella olevaa yhteysvalitsinta (vain yksi yhteystapa voi olla aktiivinen kerrallaan):

![Yhteydet-näkymä, jossa yhteystavan valitsin näyttää Bluetooth-, Verkko- ja USB-vaihtoehdot](../../assets/screenshots/connections_transport_filters.png)

On hardware with no Bluetooth LE the selector leaves out **Bluetooth**, and on hardware with no USB host it leaves out **USB**. With **Network** as the only transport left, the selector is hidden.

> 💡 **Tip:** If your node doesn't appear, check that it isn't already connected to another phone, or out of range.

Näytössä kerrotaan kaikki sovelluksen puolella olevat syyt, jotka estävät haun, sekä niiden ratkaisut:

| Näet seuraavan                                                           | Mitä se tarkoittaa                                                                                                                                                                           |
| ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kortti pyytää **Lähistön laitteet** -käyttöoikeutta                      | Käyttöoikeutta ei ole myönnetty. **Myönnä käyttöoikeus** pyytää sitä. Kun Android lakkaa kysymästä, painikkeeksi muuttuu **Avaa asetukset**. |
| **Bluetooth on pois käytöstä**                                           | Bluetooth-sovitin on poistettu käytöstä – kortti avaa Bluetooth-asetukset.                                                                                                   |
| **Bluetooth-laitteiden haku edellyttää myös sijaintipalvelujen käyttöä** | Vain Android 11:ssä ja vanhemmissa versioissa: käyttöoikeus on myönnetty, mutta järjestelmän sijaintipalvelut ovat pois käytöstä.            |
| No card, and **No Bluetooth devices seen** under the header              | Nothing on this side is blocking the scan. The node is out of range, off, or already connected elsewhere.                                                    |

The explanation lives in that card, not in the scan control: tapping **Scan for Bluetooth devices** after you have declined once asks Android again directly.

### Yhteyden tila

The **Connect** tab icon in the navigation bar shows the connection state by color:

| Ikoni | Tila                 | Kuvaus                                                                                                                  |
| ----- | -------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 🟢    | Yhdistetty           | Active link to the node. The badge shows the transport in use: Bluetooth, Wi-Fi, or USB |
| 🟠    | Yhdistetään          | Yhteyden muodostus käynnissä                                                                                            |
| 🔴    | Ei yhdistetty        | No active connection. The app retries automatically, with a growing delay between attempts              |
| 🟡    | Laite on lepotilassa | The node is in light sleep. The app is waiting for it to wake and reconnect, not failing                |

Nämä ovat neljä yhteystilaa, joita sovellus käyttää. "Laite lepotilassa" on normaali virransäästöasetuksissa eikä vaadi mitään toimenpiteitä.

Yhteyttä muodostettaessa tilailmaisin näyttää nykyisen yhteyden tilan — napauta **Lopeta yhteyden muodostaminen** keskeyttääksesi yrityksen:

![Yhdistämisen tila](../../assets/screenshots/connections_connecting.png)

Once connected, the connection card shows the node's name and firmware version, its signal strength over Bluetooth, and its LoRa modem preset and region, for example "Long Range - Fast · United States". A node on custom modem settings shows **Custom** in place of the preset. Tap the preset row to open the LoRa screen.

With no node chosen yet, the connection card reads **No device selected**. A pane with nothing to list says so under its header, with a hint:

| Pane      | What it shows                                                                                                                                                                                                                                                                |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bluetooth | **No Bluetooth devices seen**, and "Ensure you're within range of the device." Start a scan from the header.                                                                                                                                 |
| Verkko    | **No network devices seen**, and "Ensure you're connected to the same network as the device." Shown only while nothing has been discovered and **Recent Network Devices** is empty. **Add device manually…** stays below it. |
| USB       | **No USB devices detected**, and "Connect a device with a USB data cable to use serial."                                                                                                                                                                     |

### Bluetoothin vianmääritys

- **Node not found:** Turn Bluetooth off and back on. On Android 11 and older, also check that system location services are switched on. Those releases do not return scan results without them.
- **Bluetooth scan couldn't start:** Try again, and toggle Bluetooth off and on if it repeats.
- **Connection drops:** Move closer to the node; check for interference.
- **Pairing failed, or pairing did not complete:** Check that the **Nearby devices** permission is granted, then pair again.
- **Paritus hylätty:** Poista laite Androidin Bluetooth-asetuksista ja yritä uudelleen.
- **Could not establish a stable connection after repeated attempts:** The app stopped retrying after three failed handshakes. A node that keeps failing here is usually crashing on reconnect. Power-cycle the node, then tap it again on the **Connect** tab to start a fresh attempt.

## USB-sarjaporttiyhteys

USB-yhteydet tarjoavat langallisen vaihtoehdon, hyödyllinen työpöytäkäytössä tai kun Bluetooth ei ole käytettävissä.

### Asetukset

1. Connect your node to your phone with a USB data cable. Charge-only cables carry no data lines, and a node on one never appears in the list.
2. The app prompts for USB permission. Tap **Allow**.
3. Yhteys muodostetaan automaattisesti.

> ℹ️ **Huomautus:** USB-yhteydet edellyttävät Android-laitteissa OTG-tukea.

### Troubleshooting USB

- **USB permission denied:** Unplug the node and plug it back in. Android asks again on reconnect.
- **No node in the list:** Check that the cable carries data rather than only power, and that the phone supports OTG.

## TCP/IP (Verkko)

Some Meshtastic nodes support Wi-Fi/Ethernet connectivity, allowing TCP-based connections over your local network. Get the node onto your network first. Connect to it over Bluetooth or USB, open **Settings → Device configuration → Network**, and under **Wi-Fi Options** turn on **WiFi Enabled** and enter the **SSID** and **Password**. The Network screen appears only for nodes whose hardware supports Wi-Fi or Ethernet. Once the node has an address, come back and connect to it over the network.

### Yhdistäminen verkon kautta

1. Make sure the node is on the same local network as your phone or desktop.
2. On the **Connect** tab, select **Network** in the transport selector.
3. Choose the node one of two ways:
   - **Scan for network devices**: toggle this on to auto-discover nodes that advertise themselves on the local network (mDNS / `_meshtastic._tcp`). Löydetyt laitteet näkyvät luettelossa; yhdistä napauttamalla haluamaasi laitetta.
   - **Add device manually…**: enter the node's IP address (or hostname) and port (default: `4403`).
4. Previously used network addresses are remembered under **Recent Network Devices** for reconnection (touch & hold to remove one).

Network discovery uses mDNS, which only works when both your phone and the node are on the same subnet. If the phone is not on Wi-Fi at all, the app warns that a network scan may find nothing. On Android 17 and newer, the app needs the **Local network permission** to reach a node on your own Wi-Fi at all, not only to discover it, so typing the address by hand does not work around a denied permission. Grant it from the card on the Network pane, or from the **Permissions** section of **Settings**. A node on a public address, or one reached over a VPN, needs no permission.

### Milloin TCP-yhteyttä kannattaa käyttää

- Node is on the same local network
- Testing with a simulated node
- Ympäristöt, joissa Bluetoothissa on häiriöongelmia

### WiFi-määritys mPWRD-OS:lle

**Settings → Wi-Fi Provisioning for mPWRD-OS** is a separate, narrower tool. It sends Wi-Fi credentials over Bluetooth to **mPWRD-OS** devices only, using their own protocol. It does not configure Wi-Fi on an ordinary Meshtastic node. It is available on both Android and desktop.

1. Open the screen and wait while the app finds the device over Bluetooth.
2. Tap **Scan for Networks**, then pick a network from **Available Networks**, or turn on **Hidden network** and type the name into **Network Name (SSID)**.
3. Enter the **Password** and tap **Apply**.

If the scan reports **No networks found** or fails outright, move the phone closer to the device and scan again. If **Failed to apply Wi-Fi configuration** comes back, check the password and try again.

## After Your First Connection

Being connected is not the same as being able to transmit.

A node leaves the factory with no LoRa region set, and it does not transmit until you set one. When you connect such a node, the **Connect** tab shows a **Set your region** card; tap it to open the LoRa screen and choose the region you are in.

Once the region is set, the tab warns you if the node is receive-only: a **Transmit is disabled** card, reading "This device can receive but will not send anything over LoRa." Tap it to open the same screen and turn **Transmit Enabled** back on. Only one of the two cards appears at a time. An unset region already stops the node transmitting, so the app names that first and holds the transmit card back until you have set a region.

For more information, see [Settings — Radio & User](settings-radio-user#lora-config).

## Uudelleenyhdistämisen toiminta

The app reconnects to the last selected node on startup. You can switch transports from the **Connect** tab at any time.

To disconnect, tap the disconnect button on the **Connect** tab:

![Disconnect from the node](../../assets/screenshots/connections_disconnect.png)

## Työpöytäyhteydet

Työpöydällä (Linux/macOS/Windows) sovellus tukee:

- **Bluetooth (BLE)** — Kable-kirjaston kautta; toimii macOS:llä, Linuxilla ja Windowsilla
- **USB-sarjaportti** — ensisijainen langallinen yhteystapa
- **TCP/IP** — for network-connected nodes

Katso [Työpöytäsovellus](desktop) alustakohtaiset tiedot ja pikanäppäimet.

## Aiheeseen liittyvät aiheet

- [Aloitus](onboarding) — ensikäynnistyksen käyttöönotto ja käyttöoikeudet
- [Settings — Radio & User](settings-radio-user) — Bluetooth, region, and network configuration
- [Messages & Channels](messages-and-channels) — send your first message once the node is connected
- [Radiot](nodes) — katso, ketkä muut ovat mesh-verkossasi
- [Työpöytäsovellus](desktop) — työpöytäkohtaiset yhteystiedot
- [Supported devices](https://meshtastic.org/docs/hardware/devices) — full list of compatible nodes on meshtastic.org
