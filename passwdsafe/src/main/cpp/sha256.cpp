/*
 * Copyright (c) 2003-2014 Rony Shapiro <ronys@users.sourceforge.net>.
 * Copyright (c) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
// sha256.cpp
// SHA256 for PasswordSafe, based on LibTomCrypt by
// Tom St Denis, tomstdenis@iahu.ca, http://libtomcrypt.org
// Rewritten for C++14 by Jeff Harris
//-----------------------------------------------------------------------------
#include <algorithm>
#include <cstdint>
#include <array>
#include <cstring>
#include "sha256.h"

#pragma clang diagnostic push
#pragma ide diagnostic ignored "cppcoreguidelines-pro-bounds-constant-array-index"
#pragma ide diagnostic ignored "cppcoreguidelines-pro-bounds-pointer-arithmetic"
#pragma ide diagnostic ignored "readability-magic-numbers"
#pragma ide diagnostic ignored "cppcoreguidelines-avoid-magic-numbers"

namespace {

inline uint32_t load32H(unsigned const char* buf)
{
    return (static_cast<uint32_t>(buf[0] & 0xffU) << 24U) |
           (static_cast<uint32_t>(buf[1] & 0xffU) << 16U) |
           (static_cast<uint32_t>(buf[2] & 0xffU) << 8U) |
           (static_cast<uint32_t>(buf[3] & 0xffU));
}

inline void store32H(uint32_t val, unsigned char* buf)
{
    buf[0] = static_cast<unsigned char>((val >> 24U) & 0xffU);
    buf[1] = static_cast<unsigned char>((val >> 16U) & 0xffU);
    buf[2] = static_cast<unsigned char>((val >> 8U) & 0xffU);
    buf[3] = static_cast<unsigned char>(val & 0xffU);
}

inline void store64H(uint64_t val, unsigned char* buf)
{
    buf[0] = static_cast<unsigned char>((val >> 56U) & 0xffU);
    buf[1] = static_cast<unsigned char>((val >> 48U) & 0xffU);
    buf[2] = static_cast<unsigned char>((val >> 40U) & 0xffU);
    buf[3] = static_cast<unsigned char>((val >> 32U) & 0xffU);
    buf[4] = static_cast<unsigned char>((val >> 24U) & 0xffU);
    buf[5] = static_cast<unsigned char>((val >> 16U) & 0xffU);
    buf[6] = static_cast<unsigned char>((val >> 8U) & 0xffU);
    buf[7] = static_cast<unsigned char>(val & 0xffU);
}

inline uint32_t RORc(uint32_t val, unsigned int num)
{
    return ((val & 0xFFFFFFFFU) >> (num & 31U)) |
           ((val << (32 - (num & 31U))) & 0xFFFFFFFFU);
}

/* Various logical functions */
inline uint32_t Ch(uint32_t valx, uint32_t valy, uint32_t valz)
{
    return valz ^ (valx & (valy ^ valz));
}

inline uint32_t Maj(uint32_t valx, uint32_t valy, uint32_t valz)
{
    return (((valx | valy) & valz) | (valx & valy));
}

inline uint32_t S(uint32_t val, unsigned int n)
{
    return RORc(val, n);
}

inline uint32_t R(uint32_t val, unsigned int n)
{
    return (val & 0xFFFFFFFFU) >> n;
}

inline uint32_t Sigma0(uint32_t val)
{
    return S(val, 2) ^ S(val, 13) ^ S(val, 22);
}

inline uint32_t Sigma1(uint32_t val)
{
    return S(val, 6) ^ S(val, 11) ^ S(val, 25);
}

inline uint32_t Gamma0(uint32_t val)
{
    return S(val, 7) ^ S(val, 18) ^ R(val, 3);
}

inline uint32_t Gamma1(uint32_t valx)
{
    return S(valx, 17) ^ S(valx, 19) ^ R(valx, 10);
}

inline void RND(
        uint32_t vala, uint32_t valb, uint32_t valc, uint32_t& vald,
        uint32_t vale, uint32_t valf, uint32_t valg, uint32_t& valh,
        uint32_t valw, uint32_t valki)
{
    const uint32_t valt0 =
            valh + Sigma1(vale) + Ch(vale, valf, valg) + valki + valw;
    const uint32_t valt1 = Sigma0(vala) + Maj(vala, valb, valc);
    vald += valt0;
    valh = valt0 + valt1;
}

}

/**
 * Compress 512 bits from the buffer
 * @param buf The buffer being compressed
 */
inline void SHA256::compress(const unsigned char* buf)
{
    /* copy state into S */
    State valS = itsState;

    std::array<uint32_t, 64> valW; // NOLINT(cppcoreguidelines-pro-type-member-init,hicpp-member-init)

    // copy the state into 512-bits into W[0..15]
    for (size_t i = 0; i < 16; i++) {
        valW[i] = load32H(buf + (4 * i));
    }

    // fill W[16..63]
    for (size_t i = 16; i < 64; i++) {
        valW[i] = Gamma1(valW[i - 2]) + valW[i - 7] + Gamma0(valW[i - 15]) + valW[i - 16];
    }

    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[0], 0x428a2f98U);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[1], 0x71374491U);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[2], 0xb5c0fbcfU);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[3], 0xe9b5dba5U);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[4], 0x3956c25bU);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[5], 0x59f111f1U);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[6], 0x923f82a4U);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[7], 0xab1c5ed5U);
    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[8], 0xd807aa98U);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[9], 0x12835b01U);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[10], 0x243185beU);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[11], 0x550c7dc3U);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[12], 0x72be5d74U);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[13], 0x80deb1feU);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[14], 0x9bdc06a7U);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[15], 0xc19bf174U);
    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[16], 0xe49b69c1U);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[17], 0xefbe4786U);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[18], 0x0fc19dc6U);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[19], 0x240ca1ccU);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[20], 0x2de92c6fU);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[21], 0x4a7484aaU);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[22], 0x5cb0a9dcU);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[23], 0x76f988daU);
    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[24], 0x983e5152U);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[25], 0xa831c66dU);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[26], 0xb00327c8U);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[27], 0xbf597fc7U);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[28], 0xc6e00bf3U);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[29], 0xd5a79147U);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[30], 0x06ca6351U);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[31], 0x14292967U);
    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[32], 0x27b70a85U);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[33], 0x2e1b2138U);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[34], 0x4d2c6dfcU);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[35], 0x53380d13U);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[36], 0x650a7354U);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[37], 0x766a0abbU);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[38], 0x81c2c92eU);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[39], 0x92722c85U);
    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[40], 0xa2bfe8a1U);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[41], 0xa81a664bU);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[42], 0xc24b8b70U);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[43], 0xc76c51a3U);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[44], 0xd192e819U);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[45], 0xd6990624U);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[46], 0xf40e3585U);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[47], 0x106aa070U);
    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[48], 0x19a4c116U);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[49], 0x1e376c08U);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[50], 0x2748774cU);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[51], 0x34b0bcb5U);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[52], 0x391c0cb3U);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[53], 0x4ed8aa4aU);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[54], 0x5b9cca4fU);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[55], 0x682e6ff3U);
    RND(valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valW[56], 0x748f82eeU);
    RND(valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valW[57], 0x78a5636fU);
    RND(valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valS[5], valW[58], 0x84c87814U);
    RND(valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valS[4], valW[59], 0x8cc70208U);
    RND(valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valS[3], valW[60], 0x90befffaU);
    RND(valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valS[2], valW[61], 0xa4506cebU);
    RND(valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valS[1], valW[62], 0xbef9a3f7U);
    RND(valS[1], valS[2], valS[3], valS[4], valS[5], valS[6], valS[7], valS[0], valW[63], 0xc67178f2U);

    // feedback
    for (size_t i = 0; i < itsState.size(); i++) {
        itsState[i] += valS[i];
    }
}

/**
 * Process a block of memory though the hash
 * @param inbuf The data to hash
 * @param inlen The length of the data (octets)
 */
void SHA256::update(const unsigned char* inbuf, size_t inlen)
{
    while (inlen > 0) {
        if (itsCurlen == 0 && inlen >= BLOCKSIZE) {
            compress(inbuf);
            itsLength += BLOCKSIZE * 8;
            inbuf += BLOCKSIZE;
            inlen -= BLOCKSIZE;
        } else {
            const size_t len = std::min(inlen, (BLOCKSIZE - itsCurlen));
            memcpy(itsBuf.data() + itsCurlen, inbuf, len);
            itsCurlen += len;
            inbuf += len;
            inlen -= len;
            if (itsCurlen == BLOCKSIZE) {
                compress(itsBuf.data());
                itsLength += BLOCKSIZE * 8;
                itsCurlen = 0;
            }
        }
    }
}

/**
 * Terminate the hash to get the digest
 * @param digest The destination of the hash (32 bytes)
 */
void SHA256::final(std::array<unsigned char, HASHLEN>& digest)
{
    // increase the length of the message
    itsLength += itsCurlen * 8;

    // append the '1' bit
    itsBuf[itsCurlen++] = static_cast<unsigned char>(0x80);

    // if the length is currently above 56 bytes we append zeros
    // then compress.  Then we can fall back to padding zeros and length
    // encoding like normal.
    if (itsCurlen > 56) {
        while (itsCurlen < BLOCKSIZE) {
            itsBuf[itsCurlen++] = 0;
        }
        compress(itsBuf.data());
        itsCurlen = 0;
    }

    // pad up to 56 bytes of zeroes
    while (itsCurlen < 56) {
        itsBuf[itsCurlen++] = 0;
    }

    // store length
    store64H(itsLength, itsBuf.data() + 56);
    compress(itsBuf.data());

    // copy output
    for (size_t i = 0; i < 8; i++) {
        store32H(itsState[i], digest.data() + (4 * i));
    }
}

#pragma clang diagnostic pop
