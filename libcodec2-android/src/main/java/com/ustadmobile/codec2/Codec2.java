package com.ustadmobile.codec2;

import androidx.annotation.RequiresApi;

@RequiresApi(23)
public class Codec2 {

    static {
        System.loadLibrary("codec2");
        System.loadLibrary("Codec2JNI");
    }

    // codec2 modes
    public static final int CODEC2_MODE_3200 = 0;
    public static final int CODEC2_MODE_2400 = 1;
    public static final int CODEC2_MODE_1600 = 2;
    public static final int CODEC2_MODE_1400 = 3;
    public static final int CODEC2_MODE_1300 = 4;
    public static final int CODEC2_MODE_1200 = 5;
    public static final int CODEC2_MODE_700C = 8;
    public static final int CODEC2_MODE_450=10;
    public static final int CODEC2_MODE_450PWB=11;

    // freedv modes
    public static final int FREEDV_MODE_1600 = 0;
    public static final int FREEDV_MODE_2400A = 3;
    public static final int FREEDV_MODE_2400B = 4;
    public static final int FREEDV_MODE_800XA = 5;
    public static final int FREEDV_MODE_700C = 6;
    public static final int FREEDV_MODE_700D = 7;
    public static final int FREEDV_MODE_2020 = 8;
    public static final int FREEDV_MODE_2020B = 16;
    public static final int FREEDV_MODE_700E = 13;

    // freedv data modes
    public static final int FREEDV_MODE_FSK_LDPC = 9;
    public static final int FREEDV_MODE_DATAC1 = 10;
    public static final int FREEDV_MODE_DATAC3 = 12;
    public static final int FREEDV_MODE_DATAC0 = 14;

    // raw codec2
    public native static long create(int mode);
    public native static int destroy(long con);

    public native static int getSamplesPerFrame(long con);
    public native static int getBitsSize(long con);

    public native static long encode(long con, short[] inputSamples, byte[] outputBits);
    public native static long decode(long con, short[] outputSamples, byte[] inputsBits);

    // raw fsk
    public native static long fskCreate(int sampleFrequency, int symbolRate, int toneFreq, int toneSpacing, int gain);
    public native static int fskDestroy(long conFsk);

    public native static int fskDemodBitsBufSize(long conFsk);
    public native static int fskModSamplesBufSize(long conFsk);
    public native static int fskDemodSamplesBufSize(long conFsk);
    public native static int fskModBitsBufSize(long conFsk);
    public native static int fskSamplesPerSymbol(long conFsk);
    public native static int fskNin(long conFsk);

    public native static long fskModulate(long conFsk, short[] outputSamples, byte[] inputBits);
    public native static long fskDemodulate(long conFsk, short[] inputSamples, byte[] outputBits);

    // freedv
    public native static long freedvCreate(int mode, boolean isSquelchEnabled, float squelchSnr, long framesPerBurst);
    public native static int freedvDestroy(long conFreedv);

    public native static int freedvGetMaxSpeechSamples(long conFreedv);
    public native static int freedvGetMaxModemSamples(long conFreedv);
    public native static int freedvGetNSpeechSamples(long conFreedv);
    public native static int freedvGetNomModemSamples(long conFreedv);
    public native static int freedvNin(long conFreedv);
    public native static float freedvGetModemStat(long conFreedv);

    public native static long freedvTx(long conFreedv, short[] outputModemSamples, short[] inputSpeechSamples);
    public native static long freedvRx(long conFreedv, short[] outputSpeechSamples, short[] inputModemSamples);

    // freedv raw data
    public native static int freedvGetBitsPerModemFrame(long conFreedv);
    public native static int freedvGetNTxSamples(long conFreedv);

    public native static long freedvRawDataRx(long conFreedv, byte[] outputRawData, short[] inputDataSamples);
    public native static long freedvRawDataTx(long conFreedv, short[] outputDataSamples, byte[] inputRawData);
    public native static long freedvRawDataPreambleTx(long conFreedv, short[] outputDataSamples);
    public native static long freedvRawDataPostambleTx(long conFreedv, short[] outputDataSamples);

}