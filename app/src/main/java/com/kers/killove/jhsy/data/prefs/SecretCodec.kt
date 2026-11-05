package com.kers.killove.jhsy.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 敏感字段的本地加密（AndroidKeyStore + AES/GCM）。
 *
 * 用途：Wallhaven / 翻译 / AI / Git / 网盘 / 高德等密钥不再以明文落在
 * DataStore 的 `wallpaperc_settings.preferences_pb` 里。密钥由系统
 * AndroidKeyStore 生成并保管，应用只保存密文；卸载应用或清除数据后
 * 密钥随之失效，密文不可再用（这是预期行为）。
 *
 * 兼容策略：
 * - 旧版本写入的是明文（没有前缀）。[decrypt] 对无前缀的值原样返回，
 *   于是升级后第一次读取仍然可用；[encrypt] 在下次保存时写成密文，
 *   完成无感迁移。
 * - 密文格式：`enc1:` + Base64(IV[12] + ciphertext + tag[16])。
 * - 任何一步失败都不抛给调用方：加密失败退化为明文（至少不丢数据），
 *   解密失败返回空串（密钥失效时按未配置处理）。
 */
object SecretCodec {

    private const val TAG = "SecretCodec"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "miroflweat_secret_v1"
    private const val PREFIX = "enc1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    @Volatile
    private var cachedKey: SecretKey? = null

    /** 是否已是密文。 */
    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    /**
     * 加密；空串和已加密的值原样返回，避免重复加密。
     * 加密失败时降级为明文并记录日志，保证功能可用。
     */
    fun encrypt(plain: String): String {
        if (plain.isEmpty() || isEncrypted(plain)) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "加密失败，回退明文存储", t)
            plain
        }
    }

    /**
     * 解密；无前缀的值视为旧版明文，原样返回。
     * 解密失败（密钥失效 / 数据损坏）返回空串。
     */
    fun decrypt(stored: String): String {
        if (stored.isEmpty() || !isEncrypted(stored)) return stored
        return try {
            val raw = Base64.decode(stored.substring(PREFIX.length), Base64.NO_WRAP)
            if (raw.size <= IV_LENGTH) return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_BITS, raw, 0, IV_LENGTH)
            )
            String(cipher.doFinal(raw, IV_LENGTH, raw.size - IV_LENGTH), Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "解密失败，按未配置处理", t)
            ""
        }
    }

    /**
     * 取（或首次创建）Keystore 中的 AES 密钥。
     * 用 @Synchronized 是因为主进程与 :svc 进程、以及并发的设置读写都可能触发首次创建。
     */
    @Synchronized
    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        val key = existing ?: generateKey()
        cachedKey = key
        return key
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
