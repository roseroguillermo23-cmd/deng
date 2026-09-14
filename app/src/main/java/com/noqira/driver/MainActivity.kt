package com.noqira.driver

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var pairScreen: View
    private lateinit var orderScreen: View
    private lateinit var codeInput: EditText
    private lateinit var pairMessage: TextView
    private lateinit var pairProgress: ProgressBar
    private lateinit var orderTitle: TextView
    private lateinit var orderStatus: TextView
    private lateinit var customerName: TextView
    private lateinit var customerReference: TextView
    private lateinit var orderTotal: TextView
    private lateinit var paymentLabel: TextView
    private lateinit var gpsState: TextView
    private lateinit var orderMessage: TextView
    private lateinit var startRouteButton: Button
    private lateinit var deliveredButton: Button
    private var customerPhone = ""

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) startTrackingService() else orderMessage.text = "Debe permitir ubicación para realizar la entrega."
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            gpsState.text = intent?.getStringExtra(TrackingService.EXTRA_STATUS) ?: "GPS activo"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        configureActions()
        handleDeepLink(intent)
        val token = SessionStore.token(this)
        if (token.isNullOrBlank()) showPairScreen() else loadSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, statusReceiver,
            IntentFilter(TrackingService.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun bindViews() {
        pairScreen = findViewById(R.id.pairScreen); orderScreen = findViewById(R.id.orderScreen)
        codeInput = findViewById(R.id.codeInput); pairMessage = findViewById(R.id.pairMessage); pairProgress = findViewById(R.id.pairProgress)
        orderTitle = findViewById(R.id.orderTitle); orderStatus = findViewById(R.id.orderStatus)
        customerName = findViewById(R.id.customerName); customerReference = findViewById(R.id.customerReference)
        orderTotal = findViewById(R.id.orderTotal); paymentLabel = findViewById(R.id.paymentLabel)
        gpsState = findViewById(R.id.gpsState); orderMessage = findViewById(R.id.orderMessage)
        startRouteButton = findViewById(R.id.startRouteButton); deliveredButton = findViewById(R.id.deliveredButton)
    }

    private fun configureActions() {
        findViewById<Button>(R.id.pairButton).setOnClickListener { redeemCode() }
        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            action("accept") { ok -> if (ok) requestLocationAndStart() }
        }
        startRouteButton.setOnClickListener { action("start_route") { ok -> if (ok) loadSession() } }
        deliveredButton.setOnClickListener { confirmDelivery() }
        findViewById<Button>(R.id.stopGpsButton).setOnClickListener {
            stopService(Intent(this, TrackingService::class.java)); gpsState.text = "GPS pausado manualmente"
        }
        findViewById<Button>(R.id.callButton).setOnClickListener {
            if (customerPhone.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone")))
        }
        findViewById<Button>(R.id.whatsAppButton).setOnClickListener {
            val digits = customerPhone.filter { it.isDigit() }
            if (digits.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val code = intent?.data?.takeIf { it.scheme == "noqira-driver" }?.getQueryParameter("code")
        if (!code.isNullOrBlank()) codeInput.setText(code.take(6))
    }

    private fun redeemCode() {
        val code = codeInput.text.toString().trim()
        if (!code.matches(Regex("^[0-9]{6}$"))) { pairMessage.text = "Ingrese los 6 dígitos."; return }
        pairProgress.visibility = View.VISIBLE; pairMessage.text = "Vinculando este teléfono…"
        val device = SessionStore.deviceId(this)
        val label = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        SupabaseApi.redeem(code, device, label) { data, error -> runOnUiThread {
            pairProgress.visibility = View.GONE
            if (error != null || data?.optBoolean("ok") != true) {
                pairMessage.text = friendly(data?.optString("error") ?: error ?: "error")
                return@runOnUiThread
            }
            SessionStore.saveToken(this, data.optString("app_token"))
            renderSession(data)
        } }
    }

    private fun loadSession() {
        val token = SessionStore.token(this) ?: return showPairScreen()
        SupabaseApi.getSession(token, SessionStore.deviceId(this)) { data, error -> runOnUiThread {
            if (error != null || data?.optBoolean("ok") != true) {
                SessionStore.clearToken(this); stopService(Intent(this, TrackingService::class.java)); showPairScreen()
                pairMessage.text = "La entrega venció o fue reemplazada. Ingrese un nuevo código."
            } else renderSession(data)
        } }
    }

    private fun renderSession(data: JSONObject) {
        pairScreen.visibility = View.GONE; orderScreen.visibility = View.VISIBLE
        val order = data.optJSONObject("order") ?: JSONObject()
        val customer = order.optJSONObject("customerData")
        val name = customer?.optString("name")?.takeIf { it.isNotBlank() } ?: order.optString("customer", "Cliente")
        customerPhone = customer?.optString("phone")?.takeIf { it.isNotBlank() } ?: order.optString("phone", "")
        val reference = customer?.optString("reference")?.takeIf { it.isNotBlank() } ?: order.optString("reference", "Sin referencia")
        orderTitle.text = "Pedido ${data.optString("order_id", order.optString("id"))}"
        orderStatus.text = "${data.optString("dispatch_status")} · ${data.optString("delivery_status")}"
        customerName.text = name
        customerReference.text = "Referencia: $reference\nTeléfono: $customerPhone"
        orderTotal.text = "$" + String.format("%.2f", order.optDouble("total", 0.0))
        paymentLabel.text = order.optString("paymentLabel", "")
        val gps = data.optBoolean("gps_active")
        gpsState.text = if (gps) "● GPS activo · puede bloquear la pantalla" else "GPS todavía no iniciado"
        startRouteButton.isEnabled = data.optBoolean("can_start_route")
        deliveredButton.isEnabled = data.optBoolean("can_deliver")
        if (data.optBoolean("completed")) {
            gpsState.text = "Entrega finalizada"
            stopService(Intent(this, TrackingService::class.java))
        }
    }

    private fun showPairScreen() {
        orderScreen.visibility = View.GONE; pairScreen.visibility = View.VISIBLE
    }

    private fun action(action: String, after: (Boolean) -> Unit = {}) {
        val token = SessionStore.token(this) ?: return
        orderMessage.text = "Procesando…"
        SupabaseApi.update(token, SessionStore.deviceId(this), action) { data, error -> runOnUiThread {
            val ok = error == null && data?.optBoolean("ok") == true
            if (ok) {
                orderMessage.text = when (action) {
                    "accept" -> "Pedido aceptado. Activando GPS…"
                    "start_route" -> "Ruta iniciada. El cliente ya puede seguirlo en vivo."
                    else -> "Actualizado"
                }
            } else {
                orderMessage.text = friendly(data?.optString("error") ?: error ?: "error")
            }
            after(ok)
        } }
    }

    private fun requestLocationAndStart() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) { startTrackingService(); return }
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startTrackingService() {
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
        gpsState.text = "● GPS iniciando · puede bloquear la pantalla"
        orderMessage.text = "Mantenga activa la ubicación del teléfono. La app seguirá enviando señal con la pantalla bloqueada."
    }

    private fun confirmDelivery() {
        AlertDialog.Builder(this)
            .setTitle("Confirmar entrega")
            .setMessage("¿Verificó que quien recibe bebidas alcohólicas es mayor de 18 años?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, entregar") { _, _ ->
                val token = SessionStore.token(this) ?: return@setPositiveButton
                SupabaseApi.update(token, SessionStore.deviceId(this), "delivered", null, true) { data, error -> runOnUiThread {
                    if (error == null && data?.optBoolean("ok") == true) {
                        stopService(Intent(this, TrackingService::class.java))
                        gpsState.text = "Entrega finalizada"
                        orderMessage.text = "Pedido entregado. Esta autorización temporal ya terminó."
                        startRouteButton.isEnabled = false; deliveredButton.isEnabled = false
                    } else orderMessage.text = friendly(data?.optString("error") ?: error ?: "error")
                } }
            }.show()
    }

    private fun friendly(error: String): String = when (error.substringBefore(':')) {
        "invalid_or_expired_code" -> "Código incorrecto o vencido. Solicite uno nuevo a la licorería."
        "already_paired" -> "Este código ya fue vinculado a otro teléfono."
        "too_many_attempts" -> "Demasiados intentos. Espere unos minutos."
        "handoff_required" -> "La licorería todavía no confirmó la salida del pedido."
        "route_required" -> "Primero debe iniciar la ruta."
        "age_verification_required" -> "Debe confirmar la verificación de mayoría de edad."
        "invalid_or_expired" -> "La autorización temporal venció o fue reemplazada."
        else -> "No se pudo completar la operación: $error"
    }
}
