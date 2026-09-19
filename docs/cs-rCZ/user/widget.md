---
title: Widget na domovské obrazovce
parent: Uživatelská příručka
nav_order: 20
last_updated: 2026-08-30
description: Přidejte widget Meshtastic na domovskou obrazovku a mějte přehled o místních statistikách připojeného rádia bez nutnosti otevírat aplikaci.
aliases:
  - widget
  - home-screen-widget
  - local-stats-widget
---

# Widget na domovské obrazovce

V systému Android nabízí Meshtastic **widget** na domovskou obrazovku, který na první pohled zobrazuje aktuální místní statistiky připojeného rádia – bez nutnosti otevírat aplikaci.

## Co zobrazuje

Widget zobrazuje aktuální místní statistiky **připojeného rádia**:

- Nahoře je **štítek uzlu** s krátkým názvem rádia v jeho vlastních barvách
- **Baterie** – úroveň nabití baterie rádia, nebo _Napájeno_ při napájení z externího zdroje
- **ChUtil** — využití kanálu (jak je kanál LoRa vytížený, v procentech)
- **AirUtil** — využití vysílacího času (jakou část povoleného vysílacího cyklu rádio využívá k vysílání)
- **Provoz** – pakety odeslané / přijaté a zaznamenané duplikáty
- **Přeposláno** – přeposlané pakety a zrušení přeposílání (zobrazuje se, když rádio přeposílá pakety)
- **Diagnostiky** – společný řádek s údaji **Šum** (úroveň okolního šumu v dBm), **Poškozené** (přijaté poškozené pakety) a **Zahozené** (pakety, které rádio zahodilo). Poškozené a zahozené pakety se zobrazí až při hodnotě vyšší než nula, takže u rádia bez provozu se může zobrazovat pouze hodnota šumu
- **Halda** — volná a celková paměť rádia, znázorněná pomocí pruhu
- **Uzly** — počet uzlů online z celkového počtu známých uzlů
- **Doba provozu** — doba provozu rádia od posledního restartu, zobrazená vedle položky Uzly
- **Aktualizováno** – čas poslední aktualizace statistik, zobrazený v dolní části widgetu

Klepnutím na widget otevřete aplikaci nebo pomocí tlačítka obnovení vyžádejte aktuální statistiky.

> ℹ️ **Poznámka:** Hodnoty se vztahují k připojenému rádiu. Pokud se rádio odpojí, widget nahradí statistiky stavovým řádkem – **Odpojeno**, **Připojování** nebo **Zařízení spí**. Nezobrazuje poslední známé hodnoty.

## Přidání widgetu

1. Dotkněte se prázdného místa na domovské obrazovce Androidu a podržte jej.
2. Klepněte na položku **Widgety**.
3. Přetáhněte widget Meshtastic na domovskou obrazovku. Aplikace obsahuje jeden widget, takže v nabídce widgetů se zobrazuje pouze název aplikace.
4. Podle potřeby změňte jeho velikost – rozložení se přizpůsobí dostupnému prostoru.

> ℹ️ **Poznámka:** Widget je k dispozici pouze pro Android. Není k dispozici ve verzích pro Desktop ani iOS.

## Související témata

- [Metriky uzlu](node-metrics) — kompletní historie kvality signálu a místních statistik přímo v aplikaci
- [Připojení](connections) — připojte se k rádiu, aby widget mohl zobrazovat statistiky
- [Local Mesh Discovery](discovery) — channel and airtime utilization across the mesh
