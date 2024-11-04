package com.fluttersecurebiometrics.flutter_biometric_auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class FlutterBiometricAuthPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var keyStore: KeyStore
    private lateinit var cipher: Cipher
    private val KEY_NAME = "com.fluttersecurebiometrics.biometrics_key"
    private var activity: FragmentActivity? = null
    private var failedAttempts = 0 // Başarısız deneme sayacı

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_biometric_auth")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity as? FragmentActivity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity as? FragmentActivity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "authenticate" -> authenticateUser(result)
            else -> result.notImplemented()
        }
    }

    private fun authenticateUser(result: Result) {
        val currentActivity = activity ?: run {
            result.error("NO_ACTIVITY", "Activity is not available", null)
            return
        }

        try {
            generateKey()
            if (initializeCipher()) {
                val cryptoObject = BiometricPrompt.CryptoObject(cipher)
                val executor = ContextCompat.getMainExecutor(currentActivity)

                val biometricPrompt = BiometricPrompt(currentActivity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(authResult: BiometricPrompt.AuthenticationResult) {
                            failedAttempts = 0 // Başarılı doğrulama sonrası sayacı sıfırla
                            result.success(true) // Başarılı doğrulama
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            logSecurityError(errorCode, errString) // Hata durumunda güvenlik günlüğü
                            handleAuthenticationError(errorCode, result, errString)
                        }

                        override fun onAuthenticationFailed() {
                            failedAttempts++
                            if (failedAttempts >= 3) { // 3 başarısız denemeden sonra kilitle
                                result.error("LOCKED_OUT", "Çok fazla başarısız deneme. Lütfen tekrar deneyin.", null)
                                failedAttempts = 0
                            } else {
                                result.success(false) // Başarısız deneme
                            }
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Biyometrik Kimlik Doğrulama")
                    .setSubtitle("Lütfen kimliğinizi doğrulayın")
                    .setNegativeButtonText("İptal")
                    .build()

                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                result.error("CIPHER_INIT_ERROR", "Şifre oluşturulamadı", null)
            }
        } catch (e: Exception) {
            result.error("AUTH_ERROR", "Biyometrik doğrulama sırasında hata oluştu: ${e.localizedMessage}", null)
        }
    }

    private fun handleAuthenticationError(errorCode: Int, result: Result, errString: CharSequence) {
        if (isDebuggerConnected()) { // Debugger kontrolü
            result.error("DEBUGGER_DETECTED", "Yetkisiz erişim tespit edildi", null)
        } else if (errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
            result.error("LOCKED_OUT", "Biyometrik doğrulama geçici olarak devre dışı. Lütfen şifre ile giriş yapın.", null)
        } else {
            result.error("AUTH_ERROR", "Doğrulama hatası: $errString", null)
        }
    }

    private fun logSecurityError(errorCode: Int, errString: CharSequence) {
        Log.e("BiometricAuth", "Hata Kodu: $errorCode, Hata: $errString") // Hata günlüğü
    }

    private fun generateKey() {
        keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .build()
        )
        keyGenerator.generateKey()
    }

    private fun initializeCipher(): Boolean {
        return try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            keyStore.load(null)
            val key = keyStore.getKey(KEY_NAME, null) as SecretKey
            cipher.init(Cipher.ENCRYPT_MODE, key)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Debugger tespiti
    private fun isDebuggerConnected(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }
}
