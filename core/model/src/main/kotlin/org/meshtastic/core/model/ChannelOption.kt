/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress("MagicNumber")

package org.meshtastic.core.model

import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.math.floor

/** hash a string into an integer using the djb2 algorithm by Dan Bernstein http://www.cse.yorku.ca/~oz/hash.html */
private fun hash(name: String): UInt { // using UInt instead of Long to match RadioInterface.cpp results
    var hash = 5381u
    for (c in name) {
        hash += (hash shl 5) + c.code.toUInt()
    }
    return hash
}

private val ModemPreset.bandwidth: Float
    get() {
        for (option in ChannelOption.entries) {
            if (option.modemPreset == this) return option.bandwidth
        }
        return 0f
    }

private fun LoRaConfig.bandwidth(regionInfo: RegionInfo?) = if (use_preset) {
    modem_preset.bandwidth * if (regionInfo?.wideLora == true) 3.25f else 1f
} else {
    when (bandwidth) {
        31 -> .03125f
        62 -> .0625f
        200 -> .203125f
        400 -> .40625f
        800 -> .8125f
        1600 -> 1.6250f
        else -> bandwidth / 1000f
    }
}

val LoRaConfig.numChannels: Int
    get() {
        val regionInfo = RegionInfo.fromRegionCode(region)
        if (regionInfo == null) return 0

        val bw = bandwidth(regionInfo)
        if (bw <= 0f) return 1 // Return 1 if bandwidth is zero or negative

        val num = floor((regionInfo.freqEnd - regionInfo.freqStart) / bw)
        // If the regional frequency range is smaller than the bandwidth, the firmware would
        // fall back to a default preset. In the app, we return 1 to avoid a crash.
        return if (num > 0) num.toInt() else 1
    }

internal fun LoRaConfig.channelNum(primaryName: String): Int = when {
    channel_num != 0 -> channel_num
    numChannels == 0 -> 0
    else -> (hash(primaryName) % numChannels.toUInt()).toInt() + 1
}

internal fun LoRaConfig.radioFreq(channelNum: Int): Float {
    if ((override_frequency ?: 0f) != 0f) return (override_frequency ?: 0f) + (frequency_offset ?: 0f)
    val regionInfo = RegionInfo.fromRegionCode(region)
    return if (regionInfo != null) {
        (regionInfo.freqStart + bandwidth(regionInfo) / 2) + (channelNum - 1) * bandwidth(regionInfo)
    } else {
        0f
    }
}

/**
 * Regulatory regions for radio usage
 *
 * @property regionCode The region code
 * @property description A human readable description of the region
 * @property freqStart The starting frequency in MHz
 * @property freqEnd The ending frequency in MHz
 * @property wideLora Whether the region uses wide Lora
 * @see
 *   [LoRaWAN Regional Parameters](https://lora-alliance.org/wp-content/uploads/2020/11/lorawan_regional_parameters_v1.0.3reva_0.pdf)
 */
@Suppress("MagicNumber")
enum class RegionInfo(
    val regionCode: RegionCode,
    val description: String,
    val freqStart: Float,
    val freqEnd: Float,
    val wideLora: Boolean = false,
) {
    /** This needs to be last. Same as US. */
    UNSET(RegionCode.UNSET, "Please set a region", 902.0f, 928.0f),

    /**
     * United States
     *
     * @see [Springer Link](https://link.springer.com/content/pdf/bbm%3A978-1-4842-4357-2%2F1.pdf)
     * @see [The Things Network](https://www.thethingsnetwork.org/docs/lorawan/regional-parameters/)
     */
    US(RegionCode.US, "United States", 902.0f, 928.0f),

    /** European Union 433MHz */
    EU_433(RegionCode.EU_433, "European Union 433MHz", 433.0f, 434.0f),

    /**
     * European Union 868MHz
     *
     * Special Note: The link above describes LoRaWAN's band plan, stating a power limit of 16 dBm. This is their own
     * suggested specification, we do not need to follow it. The European Union regulations clearly state that the power
     * limit for this frequency range is 500 mW, or 27 dBm. It also states that we can use interference avoidance and
     * spectrum access techniques (such as LBT + AFA) to avoid a duty cycle. (Please refer to line P page 22 of this
     * document.)
     *
     * @see
     *   [ETSI EN 300 220-2 V3.1.1](https://www.etsi.org/deliver/etsi_en/300200_300299/30022002/03.01.01_60/en_30022002v030101p.pdf)
     */
    EU_868(RegionCode.EU_868, "European Union 868MHz", 869.4f, 869.65f),

    /** China */
    CN(RegionCode.CN, "China", 470.0f, 510.0f),

    /**
     * Japan
     *
     * @see [ARIB STD-T108](https://www.arib.or.jp/english/html/overview/doc/5-STD-T108v1_5-E1.pdf)
     * @see [Qiita](https://qiita.com/ammo0613/items/d952154f1195b64dc29f)
     */
    JP(RegionCode.JP, "Japan", 920.5f, 923.5f),

    /**
     * Australia / New Zealand
     *
     * @see [IoT Spectrum Fact Sheet](https://www.iot.org.au/wp/wp-content/uploads/2016/12/IoTSpectrumFactSheet.pdf)
     * @see
     *   [IoT Spectrum in NZ Briefing Paper](https://iotalliance.org.nz/wp-content/uploads/sites/4/2019/05/IoT-Spectrum-in-NZ-Briefing-Paper.pdf)
     */
    ANZ(RegionCode.ANZ, "Australia / Brazil / New Zealand", 915.0f, 928.0f),

    /**
     * Korea
     *
     * @see [Law.go.kr](https://www.law.go.kr/LSW/admRulLsInfoP.do?admRulId=53943&efYd=0)
     * @see
     *   [LoRaWAN Regional Parameters](https://resources.lora-alliance.org/technical-specifications/rp002-1-0-4-regional-parameters)
     */
    KR(RegionCode.KR, "Korea", 920.0f, 923.0f),

    /**
     * Taiwan, 920-925Mhz, limited to 0.5W indoor or coastal, 1.0W outdoor. 5.8.1 in the Low-power Radio-frequency
     * Devices Technical Regulations
     *
     * @see [NCC Taiwan](https://www.ncc.gov.tw/english/files/23070/102_5190_230703_1_doc_C.PDF)
     * @see [National Gazette](https://gazette.nat.gov.tw/egFront/e_detail.do?metaid=147283)
     */
    TW(RegionCode.TW, "Taiwan", 920.0f, 925.0f),

    /**
     * Russia Note:
     * - We do LBT, so 100% is allowed.
     *
     * @see [Digital.gov.ru](https://digital.gov.ru/uploaded/files/prilozhenie-12-k-reshenyu-gkrch-18-46-03-1.pdf)
     */
    RU(RegionCode.RU, "Russia", 868.7f, 869.2f),

    /** India */
    IN(RegionCode.IN, "India", 865.0f, 867.0f),

    /**
     * New Zealand 865MHz
     *
     * @see [RSM NZ](https://rrf.rsm.govt.nz/smart-web/smart/page/-smart/domain/licence/LicenceSummary.wdk?id=219752)
     * @see
     *   [IoT Spectrum in NZ Briefing Paper](https://iotalliance.org.nz/wp-content/uploads/sites/4/2019/05/IoT-Spectrum-in-NZ-Briefing-Paper.pdf)
     */
    NZ_865(RegionCode.NZ_865, "New Zealand 865MHz", 864.0f, 868.0f),

    /** Thailand */
    TH(RegionCode.TH, "Thailand", 920.0f, 925.0f),

    /**
     * Ukraine 433MHz 433,05-434,7 Mhz 10 mW
     *
     * @see [NKZRZI](https://nkrzi.gov.ua/images/upload/256/5810/PDF_UUZ_19_01_2016.pdf)
     */
    UA_433(RegionCode.UA_433, "Ukraine 433MHz", 433.0f, 434.7f),

    /**
     * Ukraine 868MHz 868,0-868,6 Mhz 25 mW
     *
     * @see [NKZRZI](https://nkrzi.gov.ua/images/upload/256/5810/PDF_UUZ_19_01_2016.pdf)
     */
    UA_868(RegionCode.UA_868, "Ukraine 868MHz", 868.0f, 868.6f),

    /**
     * Malaysia 433MHz 433 - 435 MHz at 100mW, no restrictions.
     *
     * @see [MCMC](https://www.mcmc.gov.my/skmmgovmy/media/General/pdf/Short-Range-Devices-Specification.pdf)
     */
    MY_433(RegionCode.MY_433, "Malaysia 433MHz", 433.0f, 435.0f),

    /**
     * Malaysia 919MHz 919 - 923 Mhz at 500mW, no restrictions. 923 - 924 MHz at 500mW with 1% duty cycle OR frequency
     * hopping. Frequency hopping is used for 919 - 923 MHz.
     *
     * @see [MCMC](https://www.mcmc.gov.my/skmmgovmy/media/General/pdf/Short-Range-Devices-Specification.pdf)
     */
    MY_919(RegionCode.MY_919, "Malaysia 919MHz", 919.0f, 924.0f),

    /**
     * Singapore 923MHz SG_923 Band 30d: 917 - 925 MHz at 100mW, no restrictions.
     *
     * @see
     *   [IMDA](https://www.imda.gov.sg/-/media/imda/files/regulation-licensing-and-consultations/ict-standards/telecommunication-standards/radio-comms/imdatssrd.pdf)
     */
    SG_923(RegionCode.SG_923, "Singapore 923MHz", 917.0f, 925.0f),

    /**
     * Philippines 433MHz 433 - 434.7 MHz <10 mW erp, NTC approved device required
     *
     * @see [Firmware Issue #4948](https://github.com/meshtastic/firmware/issues/4948#issuecomment-2394926135)
     */
    PH_433(RegionCode.PH_433, "Philippines 433MHz", 433.0f, 434.7f),

    /**
     * Philippines 868MHz 868 - 869.4 MHz <25 mW erp, NTC approved device required
     *
     * @see [Firmware Issue #4948](https://github.com/meshtastic/firmware/issues/4948#issuecomment-2394926135)
     */
    PH_868(RegionCode.PH_868, "Philippines 868MHz", 868.0f, 869.4f),

    /**
     * Philippines 915MHz 915 - 918 MHz <250 mW EIRP, no external antenna allowed
     *
     * @see [Firmware Issue #4948](https://github.com/meshtastic/firmware/issues/4948#issuecomment-2394926135)
     */
    PH_915(RegionCode.PH_915, "Philippines 915MHz", 915.0f, 918.0f),

    /** 2.4 GHZ WLAN Band equivalent. Only for SX128x chips. */
    LORA_24(RegionCode.LORA_24, "2.4 GHz", 2400.0f, 2483.5f, wideLora = true),

    /**
     * Australia / New Zealand 433MHz 433.05 - 434.79 MHz, 25mW EIRP max, No duty cycle restrictions
     *
     * @see [ACMA](https://www.acma.gov.au/licences/low-interference-potential-devices-lipd-class-licence)
     * @see [NZ Gazette](https://gazette.govt.nz/notice/id/2022-go3100)
     */
    ANZ_433(RegionCode.ANZ_433, "Australia / New Zealand 433MHz", 433.05f, 434.79f),

    /**
     * Kazakhstan 433MHz 433.075 - 434.775 MHz <10 mW EIRP, Low Powered Devices (LPD)
     *
     * @see [Firmware Issue #7204](https://github.com/meshtastic/firmware/issues/7204)
     */
    KZ_433(RegionCode.KZ_433, "Kazakhstan 433MHz", 433.075f, 434.775f),

    /**
     * Kazakhstan 863MHz 863 - 868 MHz <25 mW EIRP, 500kHz channels allowed, must not be used at airfields
     *
     * @see [Firmware Issue #7204](https://github.com/meshtastic/firmware/issues/7204)
     */
    KZ_863(RegionCode.KZ_863, "Kazakhstan 863MHz", 863.0f, 868.0f, wideLora = true),

    /**
     * Nepal 865Mhz 865 - 868 Mhz
     *
     * @see [Firmware Issue #7380](https://github.com/meshtastic/firmware/pull/7380)
     */
    NP_865(RegionCode.NP_865, "Nepal 865MHz", 865.0f, 868.0f, wideLora = false),

    /**
     * Brazil 902MHz 902 - 907.5 MHz
     *
     * @see [Firmware Issue #7399](https://github.com/meshtastic/firmware/pull/7399)
     */
    BR_902(RegionCode.BR_902, "Brazil 902MHz", 902.0f, 907.5f, wideLora = false),
    ;

    companion object {
        fun fromRegionCode(regionCode: RegionCode): RegionInfo? = entries.find { it.regionCode == regionCode }
    }
}

enum class ChannelOption(val modemPreset: ModemPreset, val bandwidth: Float) {
    // Grouped by range and speed for better readability
    VERY_LONG_SLOW(ModemPreset.VERY_LONG_SLOW, 0.0625f),
    LONG_TURBO(ModemPreset.LONG_TURBO, 0.500f),
    LONG_FAST(ModemPreset.LONG_FAST, 0.250f),
    LONG_MODERATE(ModemPreset.LONG_MODERATE, 0.125f),
    LONG_SLOW(ModemPreset.LONG_SLOW, 0.125f),
    MEDIUM_FAST(ModemPreset.MEDIUM_FAST, 0.250f),
    MEDIUM_SLOW(ModemPreset.MEDIUM_SLOW, 0.250f),
    SHORT_FAST(ModemPreset.SHORT_FAST, 0.250f),
    SHORT_SLOW(ModemPreset.SHORT_SLOW, 0.250f),
    SHORT_TURBO(ModemPreset.SHORT_TURBO, 0.500f),
    ;

    companion object {
        /** The default channel option for new configurations. */
        val DEFAULT = LONG_FAST

        /** Finds the ChannelOption corresponding to the given ModemPreset. Returns null if no match is found. */
        fun from(modemPreset: ModemPreset?): ChannelOption? {
            if (modemPreset == null) return null
            // The `entries` property is preferred over `values()` since Kotlin 1.9
            return entries.find { it.modemPreset == modemPreset }
        }
    }
}
