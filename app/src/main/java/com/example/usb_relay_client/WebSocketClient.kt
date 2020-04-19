//package com.example.usb_relay_client
//
//import android.util.Log
//import android.view.View
//import android.widget.Toast
//import com.google.gson.JsonElement
//import com.google.gson.JsonObject
//import org.java_websocket.client.WebSocketClient
//import org.java_websocket.handshake.ServerHandshake
//import java.net.URI
//
//class WebSocket(private val uri: URI) : WebSocketClient(uri) {
//
//    fun onConnected(data: JsonElement) {
//        data.asJsonArray.forEach {
//            devices.add(it.asString)
//        }
//        runOnUiThread {
//            progressBar.visibility = View.GONE
//            listAdapter.notifyDataSetChanged()
//            Toast.makeText(applicationContext, "Loaded", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    fun onDeviceLoaded(data: JsonElement) {
//        runOnUiThread {
//            data.asJsonArray.forEachIndexed { i, elem ->
//                if (i < relays.size) relays[i].isChecked = elem?.asBoolean ?: false
//            }
//        }
//    }
//
//    fun onChannelSet(data: JsonElement) {
//        val ret: RSetDevice = gson.fromJson(data, RSetDevice::class.java)
//        if (ret.id != devices[curDevice]) return
//        runOnUiThread {
//            relays[ret.channel].isChecked = ret.value
//        }
//    }
//
//    override fun onOpen(serverHandshake: ServerHandshake) {
//        Log.i("Websocket", "Opened")
//        try {
//            val req = Request(
//                method = "list"
//            )
//            devices.clear()
//            client?.send(gson.toJson(req))
//        } catch (ex: Exception) {
//            runOnUiThread {
//                Toast.makeText(
//                    applicationContext,
//                    "Error: ${ex.message}",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//            client = null
//        }
//    }
//
//    override fun onMessage(str: String) {
//        val data: JsonObject = gson.fromJson(str, JsonObject::class.java)
//        val code = data["code"]
//        val errorCode = code?.asInt ?: -1
//        if (errorCode == 0) {
//            when (data["method"].asString) {
//                "list" -> onConnected(data["data"])
//                "get" -> onDeviceLoaded(data["data"])
//                "set" -> onChannelSet(data["data"]);
//            }
//        }
//        runOnUiThread {
//            if (errorCode != 0) {
//                Toast.makeText(
//                    applicationContext,
//                    "Error code: $errorCode",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//            progressBar.visibility = View.GONE
//        }
//    }
//
//    override fun onClose(i: Int, s: String, b: Boolean) {
//        Log.i("Websocket", "Closed $s")
//        client = null
//        runOnUiThread {
//            Toast.makeText(
//                applicationContext,
//                "Disconnected: ${s}",
//                Toast.LENGTH_SHORT
//            )
//            progressBar.visibility = View.VISIBLE
//        }
//    }
//
//    override fun onError(e: java.lang.Exception) {
//        Log.i("Websocket", "Error " + e.message)
//        runOnUiThread {
//            progressBar.visibility = View.GONE
//            Toast.makeText(
//                applicationContext,
//                "Error: ${e.message}",
//                Toast.LENGTH_SHORT
//            )
//                .show()
//        }
//    }
//}