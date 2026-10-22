/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.takserver

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.meshtastic.core.repository.TakCertificateMaterial
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Issues a complete per-install PKI for the local TAK server: a private CA, a server identity for `127.0.0.1` and a
 * single client identity, packed as PKCS#12 containers under a random password.
 *
 * Why BouncyCastle: neither Android nor the JDK exposes a public X.509 certificate *builder*. The provider is held as a
 * private instance and passed explicitly. It is never registered with `java.security.Security`, because Android already
 * ships a stripped provider under the same "BC" name and registration would be ignored or shadow it.
 *
 * Why these encodings: the containers are written with BouncyCastle's explicitly named [PKCS12_KEYSTORE_TYPE], which
 * uses pbeWithSHAAnd3-KeyTripleDES-CBC for both key and certificate bags and a SHA-1 MAC. Android 9 and older cannot
 * parse PBES2/AES containers, which breaks ATAK data package import (#6567), so the output must stay legacy-encoded and
 * must not depend on whatever the provider's unqualified `PKCS12` default happens to be in a given release.
 *
 * The CA private key lives only on the stack of [generate] and is dropped afterwards: no further client certificates
 * can ever be minted for this install, and a compromised store yields nothing a fresh [generate] does not replace.
 */
internal object TakCertificateGenerator {

    /** BouncyCastle keystore type: legacy SHA1/3DES for key and certificate bags, SHA-1 MAC. See class KDoc. */
    private const val PKCS12_KEYSTORE_TYPE = "PKCS12-3DES-3DES"
    private const val SERVER_ALIAS = "meshtastic-tak-server"
    private const val CLIENT_ALIAS = "meshtastic-tak-client"
    private const val CA_ALIAS = "meshtastic-tak-ca"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    private const val KEY_ALGORITHM = "RSA"
    private const val LOOPBACK_HOST = "localhost"
    private const val LOOPBACK_IP = "127.0.0.1"
    private const val SERIAL_BITS = 128
    private const val INSTALL_ID_BYTES = 4
    private const val NOT_BEFORE_SKEW_DAYS = 1L
    private const val PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

    private val provider = BouncyCastleProvider()

    @Suppress("LongMethod")
    fun generate(random: SecureRandom = SecureRandom()): TakCertificateMaterial {
        val installId =
            ByteArray(INSTALL_ID_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(Locale.ROOT, it) }
        val password =
            CharArray(TAK_CERT_PASSWORD_LENGTH) { PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)] }
                .concatToString()

        val caKeys = newKeyPair(random)
        val serverKeys = newKeyPair(random)
        val clientKeys = newKeyPair(random)

        val caName = X500Name("CN=Meshtastic TAK CA $installId, O=Meshtastic")
        val caCert =
            certificate(random, caName, caName, caKeys, caKeys.private) {
                addExtension(Extension.basicConstraints, true, BasicConstraints(0))
                addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
            }
        val serverCert =
            certificate(
                random,
                caName,
                X500Name("CN=$LOOPBACK_HOST, OU=TAK Server, O=Meshtastic"),
                serverKeys,
                caKeys.private,
            ) {
                addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
                addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
                addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    GeneralNames(
                        arrayOf(
                            GeneralName(GeneralName.dNSName, LOOPBACK_HOST),
                            GeneralName(GeneralName.iPAddress, LOOPBACK_IP),
                        ),
                    ),
                )
                addExtension(
                    Extension.authorityKeyIdentifier,
                    false,
                    extensionUtils.createAuthorityKeyIdentifier(caCert),
                )
            }
        val clientCert =
            certificate(
                random,
                caName,
                X500Name("CN=Meshtastic TAK Client $installId, OU=TAK Client, O=Meshtastic"),
                clientKeys,
                caKeys.private,
            ) {
                addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
                addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth))
                addExtension(
                    Extension.authorityKeyIdentifier,
                    false,
                    extensionUtils.createAuthorityKeyIdentifier(caCert),
                )
            }

        val pw = password.toCharArray()
        return TakCertificateMaterial(
            serverP12 = pkcs12(pw) { setKeyEntry(SERVER_ALIAS, serverKeys.private, pw, arrayOf(serverCert, caCert)) },
            clientP12 = pkcs12(pw) { setKeyEntry(CLIENT_ALIAS, clientKeys.private, pw, arrayOf(clientCert, caCert)) },
            truststoreP12 = pkcs12(pw) { setCertificateEntry(CA_ALIAS, caCert) },
            password = password,
        )
    }

    private val extensionUtils by lazy { JcaX509ExtensionUtils() }

    private fun newKeyPair(random: SecureRandom): KeyPair =
        KeyPairGenerator.getInstance(KEY_ALGORITHM).apply { initialize(TAK_CERT_KEY_BITS, random) }.generateKeyPair()

    private fun certificate(
        random: SecureRandom,
        issuer: X500Name,
        subject: X500Name,
        subjectKeys: KeyPair,
        signingKey: PrivateKey,
        extensions: X509v3CertificateBuilder.() -> Unit,
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - TimeUnit.DAYS.toMillis(NOT_BEFORE_SKEW_DAYS))
        val notAfter = Date(now + TimeUnit.DAYS.toMillis(TAK_CERT_VALIDITY_DAYS))
        val serial = BigInteger(SERIAL_BITS, random).setBit(0)
        val builder =
            JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, subjectKeys.public).apply {
                addExtension(
                    Extension.subjectKeyIdentifier,
                    false,
                    extensionUtils.createSubjectKeyIdentifier(subjectKeys.public),
                )
                extensions()
            }
        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(provider).build(signingKey)
        return JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(signer))
    }

    private fun pkcs12(password: CharArray, populate: KeyStore.() -> Unit): ByteArray {
        val keyStore = KeyStore.getInstance(PKCS12_KEYSTORE_TYPE, provider).apply { load(null, null) }
        keyStore.populate()
        return ByteArrayOutputStream().also { keyStore.store(it, password) }.toByteArray()
    }
}
