#include <jni.h>
#include <cstdlib>
#include "codec2/codec2.h"

namespace Java_com_ustadmobile_codec2_Codec2 {

    struct Context {
        struct CODEC2 *c2;
        short *buf; //raw audio data
        unsigned char *bits; //codec2 data
        int nsam; //nsam: number of samples per frame - e.g. raw (uncompressed) size = samples per frame
        int nbit;
        int nbyte;//size of one frame of codec2 data
    };

    static Context *getContext(jlong jp) {
        auto p = (unsigned long) jp;
        Context *con;
        con = (Context *) p;
        return con;
    }

    static jlong create(JNIEnv *env, jclass clazz, int mode) {
        struct Context *con;
        con = (struct Context *) malloc(sizeof(struct Context));
        struct CODEC2 *c;
        c = codec2_create(mode);
        con->c2 = c;
        con->nsam = codec2_samples_per_frame(c);
        con->nbit = codec2_bits_per_frame(con->c2);
        con->buf = (short*)malloc(con->nsam*sizeof(short));
        con->nbyte = (con->nbit +  7) / 8;
        con->bits = (unsigned char*)malloc(con->nbyte*sizeof(char));
        auto pv = (unsigned long) con;
        return pv;
    }

    static jint c2spf(JNIEnv *env, jclass clazz, jlong n) {
        Context *con = getContext(n);
        return con->nsam;
    }

    static jint c2bits(JNIEnv *env, jclass clazz, jlong n) {
        Context *con = getContext(n);
        return con->nbyte;
    }

    static jint destroy(JNIEnv *env, jclass clazz, jlong n) {
        Context *con = getContext(n);
        codec2_destroy(con->c2);
        free(con->bits);
        free(con->buf);
        free(con);
        return 0;
    }

    static jlong encode(JNIEnv *env, jclass clazz, jlong n, jshortArray inputBuffer, jbyteArray outputBits) {
        Context *con = getContext(n);
        jshort *jbuf = env->GetShortArrayElements(inputBuffer, nullptr);
        for (int i = 0; i < con->nsam; i++) {
            auto v = (short) jbuf[i];
            con->buf[i] = v;
        }
        env->ReleaseShortArrayElements(inputBuffer, jbuf, 0);

        codec2_encode(con->c2, con->bits, con->buf);

        jbyte *jbits = env->GetByteArrayElements(outputBits, nullptr);
        for (int i = 0; i < con->nbyte; i++) {
            jbits[i] = (char)con->bits[i];
        }
        env->ReleaseByteArrayElements(outputBits, jbits, 0);
        return 0;
    }

    static jlong decode(JNIEnv *env, jclass clazz, jlong n, jshortArray outputSamples, jbyteArray inputBits) {
        Context *con = getContext(n);
        env->GetByteArrayRegion(inputBits, 0, con->nbyte, reinterpret_cast<jbyte*>(con->bits));
        codec2_decode_ber(con->c2, con->buf, con->bits, 0.0);
        env->SetShortArrayRegion(outputSamples, 0, con->nsam, con->buf);
        return 0;
    }

    static JNINativeMethod method_table[] = {
        // codec2
        {"create", "(I)J",                      (void *) create},
        {"getSamplesPerFrame", "(J)I",          (void *) c2spf},
        {"getBitsSize", "(J)I",                 (void *) c2bits},
        {"destroy", "(J)I",                     (void *) destroy},
        {"encode", "(J[S[B)J",                  (void *) encode},
        {"decode", "(J[S[B)J",                  (void *) decode},
    };
}

using namespace Java_com_ustadmobile_codec2_Codec2;

extern "C" jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    } else {
        jclass clazz = env->FindClass("com/ustadmobile/codec2/Codec2");
        if (clazz) {
            jint ret = env->RegisterNatives(clazz, method_table,
                                            sizeof(method_table) / sizeof(method_table[0]));
            env->DeleteLocalRef(clazz);
            return ret == 0 ? JNI_VERSION_1_6 : JNI_ERR;
        } else {
            return JNI_ERR;
        }
    }
}