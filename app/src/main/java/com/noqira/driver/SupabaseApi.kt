package com.noqira.driver

import android.location.Location
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object SupabaseApi {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun rpc(name: String, body: JSONObject, done: (JSONObject?, String?) -> Unit) {
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/$name")
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done(null, e.message ?: "network_error")
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        done(null, "http_${it.code}:$text")
                        return
                    }
                    try { done(JSONObject(text), null) }
                    catch (e: Exception) { done(null, "invalid_response") }
                }
            }
        })
    }

    fun redeem(code: String, deviceId: String, deviceLabel: String, done: (JSONObject?, String?) -> Unit) {
        rpc("noqira_retail_driver_redeem_code", JSONObject()
            .put("p_business_key", BuildConfig.BUSINESS_KEY)
            .put("p_code", code)
            .put("p_device_id", deviceId)
            .put("p_device_label", deviceLabel), done)
    }

    fun getSession(token: String, deviceId: String, done: (JSONObject?, String?) -> Unit) {
        rpc("noqira_retail_driver_app_get", JSONObject()
            .put("p_app_token", token)
            .put("p_device_id", deviceId), done)
    }

    fun update(token: String, deviceId: String, action: String, location: Location? = null,
               ageVerified: Boolean = false, done: (JSONObject?, String?) -> Unit) {
        val body = JSONObject()
            .put("p_app_token", token)
            .put("p_device_id", deviceId)
            .put("p_action", action)
            .put("p_age_verified", ageVerified)
        if (location != null) {
            body.put("p_lat", location.latitude)
                .put("p_lng", location.longitude)
                .put("p_accuracy", location.accuracy.toDouble())
                .put("p_heading", if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL)
        } else {
            body.put("p_lat", JSONObject.NULL).put("p_lng", JSONObject.NULL)
                .put("p_accuracy", JSONObject.NULL).put("p_heading", JSONObject.NULL)
        }
        rpc("noqira_retail_driver_app_update", body, done)
    }
}
