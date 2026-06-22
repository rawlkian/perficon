package com.kian.perficon.util

import android.content.Context
import com.android.apksig.ApkSigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*

object ApkSignerUtil {

    private const val KS_FILE = "internal_signer.p12"
    private const val KS_PASS = "perficon_auto_pass"
    private const val ALIAS = "perficon_auto_key"

    fun sign(context: Context, inputApk: File, outputApk: File) {
        val ksFile = File(context.filesDir, KS_FILE)
        val ksPassArray = KS_PASS.toCharArray()
        if (!ksFile.exists()) {
            val legacyKeystore = File(StorageHelper.rootDir, KS_FILE)
            if (legacyKeystore.isFile) {
                legacyKeystore.copyTo(ksFile, overwrite = false)
            } else {
                generateAndSaveKeystore(ksFile)
            }
        }

        val keystore = KeyStore.getInstance("PKCS12")
        ksFile.inputStream().use { fis ->
            keystore.load(fis, ksPassArray)
        }

        val privateKey = keystore.getKey(ALIAS, ksPassArray) as PrivateKey
        val cert = keystore.getCertificate(ALIAS) as X509Certificate

        val signerConfig = ApkSigner.SignerConfig.Builder(ALIAS, privateKey, listOf(cert)).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    private fun generateAndSaveKeystore(targetFile: File) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val cert = generateCertificate(keyPair)

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(ALIAS, keyPair.private, KS_PASS.toCharArray(), arrayOf(cert))
        
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { fos ->
            ks.store(fos, KS_PASS.toCharArray())
        }
    }

    private fun generateCertificate(keyPair: KeyPair): X509Certificate {
        val owner = X500Name("CN=Perficon User, O=Perficon, C=CN")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 1000L * 60 * 60 * 24 * 365 * 30)

        val builder = JcaX509v3CertificateBuilder(
            owner, serial, notBefore, notAfter, owner, keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
