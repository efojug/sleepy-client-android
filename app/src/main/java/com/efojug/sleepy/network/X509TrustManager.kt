package com.efojug.sleepy.network

import android.annotation.SuppressLint
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// 创建一个不做任何检查的 X509TrustManager
val trustAllCerts = arrayOf<TrustManager>(
    @SuppressLint("CustomX509TrustManager")
    object : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { /* fuck */ }
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { /* fuck */ }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
)