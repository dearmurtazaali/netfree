package com.example.meshchat

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.nio.charset.StandardCharsets

// ============================================================================
// REAL offline mesh — wraps Google's Nearby Connections API, which
// automatically uses Bluetooth Classic, Bluetooth Low Energy, and Wi-Fi
// Direct together, picking whichever combination works between two nearby
// devices. This is the ONLY file that touches Bluetooth/WiFi Direct code —
// everything in www/index.html just calls these JS-exposed methods and
// listens for events, exactly like the mock functions it's replacing.
//
// How it fits the mesh:
//   - startMesh(username) makes this device BOTH advertise itself (so
//     others can find it) AND discover others, at the same time — a
//     "cluster" topology where nearby phones can all connect to each other.
//   - Every connected device is a peer you can message directly.
//   - No internet, no server, no company involved at all — this is truest
//     to the "azaadi" mesh concept from the whole project.
// ============================================================================

@CapacitorPlugin(
    name = "NearbyMesh",
    permissions = [
        Permission(strings = [
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ], alias = "mesh")
    ]
)
class NearbyMeshPlugin : Plugin() {

    // Every mesh app on the same "channel" can find each other. Change this
    // string if you ever want a build that only talks to itself (e.g. a
    // private/test build), since devices on different service IDs never see
    // each other.
    private val SERVICE_ID = "com.example.meshchat.MESH_SERVICE"

    private lateinit var connectionsClient: ConnectionsClient
    private var myUsername: String = "Someone"

    // endpointId -> display name of the connected peer
    private val connectedEndpoints = mutableMapOf<String, String>()
    // endpointId -> name seen during discovery, before the connection is confirmed
    private val discoveredNames = mutableMapOf<String, String>()

    override fun load() {
        connectionsClient = Nearby.getConnectionsClient(context)
    }

    // ---- Public JS-facing methods ----------------------------------------

    @PluginMethod
    fun startMesh(call: PluginCall) {
        myUsername = call.getString("username") ?: "Someone"
        if (getPermissionState("mesh") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("mesh", call, "meshPermsCallback")
        } else {
            beginAdvertisingAndDiscovery(call)
        }
    }

    @PermissionCallback
    private fun meshPermsCallback(call: PluginCall) {
        if (getPermissionState("mesh") == com.getcapacitor.PermissionState.GRANTED) {
            beginAdvertisingAndDiscovery(call)
        } else {
            call.reject("Bluetooth/location permission was not granted — the mesh can't find nearby devices without it.")
        }
    }

    private fun beginAdvertisingAndDiscovery(call: PluginCall) {
        val strategy = Strategy.P2P_CLUSTER
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()

        connectionsClient.startAdvertising(myUsername, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener {
                connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                    .addOnSuccessListener { call.resolve() }
                    .addOnFailureListener { e -> call.reject("Discovery failed — is Bluetooth turned on? (${e.message})") }
            }
            .addOnFailureListener { e -> call.reject("Advertising failed — is Bluetooth turned on? (${e.message})") }
    }

    @PluginMethod
    fun stopMesh(call: PluginCall) {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        call.resolve()
    }

    @PluginMethod
    fun sendMessage(call: PluginCall) {
        val endpointId = call.getString("endpointId")
        val text = call.getString("text")
        if (endpointId == null || text == null) {
            call.reject("endpointId and text are required")
            return
        }
        val bytes = Payload.fromBytes(text.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, bytes)
        call.resolve()
    }

    // ---- Discovery: finding nearby devices --------------------------------

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            discoveredNames[endpointId] = info.endpointName
            // Auto-request a connection as soon as we see someone — this is
            // what makes the mesh feel instant, like the app "just knows"
            // who's nearby, rather than requiring a manual pairing step.
            connectionsClient.requestConnection(myUsername, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            val name = connectedEndpoints.remove(endpointId)
            val data = JSObject()
            data.put("endpointId", endpointId)
            data.put("name", name ?: "")
            notifyListeners("peerLost", data)
        }
    }

    // ---- Connection lifecycle: accepting/establishing links ---------------

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Covers both directions: if they found us first, this is where
            // we first learn their name.
            if (!discoveredNames.containsKey(endpointId)) {
                discoveredNames[endpointId] = info.endpointName
            }
            // Auto-accept every connection — the mesh is open by design,
            // matching the "anyone nearby can chat" philosophy of the app.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val name = discoveredNames[endpointId] ?: "Nearby device"
                connectedEndpoints[endpointId] = name
                val data = JSObject()
                data.put("endpointId", endpointId)
                data.put("name", name)
                notifyListeners("peerFound", data)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val name = connectedEndpoints.remove(endpointId)
            val data = JSObject()
            data.put("endpointId", endpointId)
            data.put("name", name ?: "")
            notifyListeners("peerLost", data)
        }
    }

    // ---- Receiving messages -------------------------------------------------

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val text = String(bytes, StandardCharsets.UTF_8)
                val data = JSObject()
                data.put("endpointId", endpointId)
                data.put("text", text)
                notifyListeners("messageReceived", data)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op for now — text messages are small enough to arrive in
            // one shot. Hook here later if you add larger file transfers.
        }
    }
}
