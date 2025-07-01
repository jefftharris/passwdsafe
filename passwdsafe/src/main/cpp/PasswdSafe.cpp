/*
 * Copyright (©) 2009-2013 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */

#include <cstddef>
#include <array>

#include <jni.h>

#include "org_pwsafe_lib_crypto_SHA256Pws.h"
#include "sha256.h"
#include "Util.h"

#pragma clang diagnostic push
#pragma ide diagnostic ignored "readability-magic-numbers"
#pragma ide diagnostic ignored "cppcoreguidelines-avoid-magic-numbers"
#pragma ide diagnostic ignored "cppcoreguidelines-pro-type-reinterpret-cast"

namespace {

/**
 * Implementation of digestNNative so stack can be cleaned by caller
 * @param env JNI environment
 * @param inbuf Input byte array
 * @param iter Number of iterations
 * @return The digested bytes
 */
[[gnu::noinline]] jbyteArray digestNNativeImpl(JNIEnv* env,
                                               jbyteArray inbuf,
                                               jint iter)
{
    const jsize plen = env->GetArrayLength(inbuf);
    jbyte *pdata = env->GetByteArrayElements(inbuf, nullptr);
    std::array<unsigned char, SHA256::HASHLEN> output{};

    SHA256 hash0;
    hash0.update(reinterpret_cast<unsigned char *>(pdata), (size_t)plen);
    hash0.final(output);

    for (jint i = 0; i < iter; ++i) {
        SHA256 loopHash;
        loopHash.update(output.data(), output.size());
        loopHash.final(output);
    }

    jbyteArray outputArray = env->NewByteArray(output.size());
    env->SetByteArrayRegion(outputArray, 0, output.size(),
                            reinterpret_cast<jbyte *>(output.data()));

    env->ReleaseByteArrayElements(inbuf, pdata, 0);
    return outputArray;
}

}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_pwsafe_lib_crypto_SHA256Pws_digestNNative(
        JNIEnv *env,
        [[maybe_unused]] jclass clazz,
        jbyteArray inbuf,
        jint iter)
{
    jbyteArray outputArray = digestNNativeImpl(env, inbuf, iter);
    burnStack(sizeof(unsigned long) * 74);
    return outputArray;
}


#pragma clang diagnostic pop
