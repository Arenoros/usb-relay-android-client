package com.example.usb_relay_client

//import io.ktor.client.HttpClient
//import io.ktor.client.features.websocket.DefaultClientWebSocketSession
//import io.ktor.client.features.websocket.WebSockets
//import io.ktor.client.features.websocket.ws
//import io.ktor.http.HttpMethod
//import io.ktor.http.cio.websocket.Frame
//import io.ktor.http.cio.websocket.readBytes
//import io.ktor.http.cio.websocket.readText
//import io.ktor.util.KtorExperimentalAPI
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.GlobalScope
//import kotlinx.coroutines.launch

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException


open class Request(val method: String)

data class RGetDevice(val id: String) : Request(method = "get")
data class RSetDevice(val id: String, val channel: Int, val value: Boolean) :
    Request(method = "set")

data class CurrentDevState(val id: String, val channels: Array<Boolean>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CurrentDevState

        if (id != other.id) return false
        if (!channels.contentEquals(other.channels)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + channels.contentHashCode()
        return result
    }
}

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    private var devices: ArrayList<String> = arrayListOf("<NoDevices>")
    private lateinit var relays: Array<Switch>
    private lateinit var listAdapter: ArrayAdapter<String>
    private lateinit var progressBar: ProgressBar
    private var curDevice: Int = 0
    private var reconnect = false
    private val gson = Gson()
    private val settings_name = "Settings"
    private lateinit var settings: SharedPreferences
    private fun testPopUp() {
        val builder = AlertDialog.Builder(this@MainActivity)

        // Set the alert dialog title
        builder.setTitle("App background color")

        // Display a message on alert dialog
        builder.setMessage("Are you want to set the app background color to RED?")

        // Set a positive button and its click listener on alert dialog
        builder.setPositiveButton("YES") { dialog, which ->
            // Do something when user press the positive button
            Toast.makeText(
                applicationContext,
                "Ok, we change the app background.",
                Toast.LENGTH_SHORT
            ).show()

            // Change the app background color
            //root_layout.setBackgroundColor(Color.RED)
        }


        // Display a negative button on alert dialog
        builder.setNegativeButton("No") { dialog, which ->
            Toast.makeText(applicationContext, "You are not agree.", Toast.LENGTH_SHORT).show()
        }


        // Display a neutral button on alert dialog
        builder.setNeutralButton("Cancel") { _, _ ->
            Toast.makeText(applicationContext, "You cancelled the dialog.", Toast.LENGTH_SHORT)
                .show()
        }

        // Finally, make the alert dialog using builder
        val dialog: AlertDialog = builder.create()

        // Display the alert dialog on app interface
        dialog.show()
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        curDevice = position
        if (devices[curDevice] == "<NoDevices>") return
        val req = RGetDevice(devices[position])
        client?.send(gson.toJson(req))
    }

    override fun onNothingSelected(arg0: AdapterView<*>) {

    }

    private var client: WebSocketClient? = null

    private fun connectToHost() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE
        val addr = findViewById<TextInputEditText>(R.id.address)
        connectWebSocket(addr.text.toString().trim())//
//        GlobalScope.launch(Dispatchers.IO) {
//            connectToDevice("192.168.88.75")//)
//        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val connect = findViewById<FloatingActionButton>(R.id.Connect)
        connect?.setOnClickListener { connectToHost() }
        settings = getSharedPreferences(settings_name, Context.MODE_PRIVATE)
        val spinner = findViewById<Spinner>(R.id.DeviceList)
        if (spinner != null) {
            listAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, devices)
            listAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = listAdapter
            spinner.onItemSelectedListener = this
        }
        progressBar = findViewById<ProgressBar>(R.id.progressBar)
        relays = Array(8) {
            val relayId = "relay$it"
            val resID = resources.getIdentifier(relayId, "id", packageName)
            val switch = findViewById<Switch>(resID)
            switch.setOnCheckedChangeListener { compoundButton, isChecked ->
                if (!compoundButton.isPressed) {
                    return@setOnCheckedChangeListener
                }
                if (curDevice < devices.size) {
                    val req = RSetDevice(id = devices[curDevice], channel = it, value = isChecked)
                    client?.send(gson.toJson(req))
                }
                compoundButton.isChecked = !isChecked
            }
            switch
        }
        val addr = findViewById<TextInputEditText>(R.id.address)
        addr?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val editor: Editor = settings.edit()
                editor.putString("Host", s.toString().trim())
                editor.apply()
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {

            }
        })
        val str = settings.getString("Host", null)
        if (str != null) {
            addr?.setText(str)
        }
    }

    private fun connectWebSocket(host: String) {
        val uri: URI
        try {
            uri = URI("ws://${host}:4444")
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            return
        }
        if (client != null) {
            client?.close()
        }
        client = object : WebSocketClient(uri) {

            fun onDevicesLoaded(data: JsonElement) {
                data.asJsonArray.forEach {
                    devices.add(it.asString)
                }
                if (devices.isEmpty()) {
                    devices.add("<NoDevices>")
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    listAdapter.notifyDataSetChanged()
                    Toast.makeText(applicationContext, "Loaded", Toast.LENGTH_SHORT).show()
                }
            }

            fun onDeviceUpdate(data: JsonElement) {
                runOnUiThread {
                    data.asJsonArray.forEachIndexed { i, elem ->
                        if (i < relays.size) relays[i].isChecked = elem?.asBoolean ?: false
                    }
                }
            }

            fun onChannelSet(data: JsonElement) {
                val ret: RSetDevice = gson.fromJson(data, RSetDevice::class.java)
                if (ret.id != devices[curDevice]) return
                runOnUiThread {
                    relays[ret.channel].isChecked = ret.value
                }
            }

            override fun onOpen(serverHandshake: ServerHandshake) {
                Log.i("Websocket", "Opened")
                try {
                    val req = Request(
                        method = "list"
                    )
                    client?.send(gson.toJson(req))
                    runOnUiThread {
                        devices.clear()
                        listAdapter.notifyDataSetChanged()
                        Toast.makeText(applicationContext, "Loaded", Toast.LENGTH_SHORT).show()
                    }
                } catch (ex: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Error: ${ex.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    client = null
                }
            }

            override fun onMessage(str: String) {
                val data: JsonObject = gson.fromJson(str, JsonObject::class.java)
                val code = data["code"]
                val errorCode = code?.asInt ?: -1
                var errText: String? = null
                if (errorCode == 0) {
                    when (data["method"].asString) {
                        "list" -> onDevicesLoaded(data["data"])
                        "get" -> onDeviceUpdate(data["data"])
                        "set" -> onChannelSet(data["data"])
                    }
                } else {
                    errText = data["error"].asString
                }
                runOnUiThread {
                    if (errorCode != 0) {
                        Toast.makeText(
                            applicationContext, errText ?: "Error code: $errorCode",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    progressBar.visibility = View.GONE
                }
            }

            override fun onClose(i: Int, s: String, b: Boolean) {
                Log.i("Websocket", "Closed $s")
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Disconnected: ${s}",
                        Toast.LENGTH_SHORT
                    ).show()
                    //progressBar.visibility = View.VISIBLE
                }
            }

            override fun onError(e: java.lang.Exception) {
                Log.i("Websocket", "Error " + e.message)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        applicationContext,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
        client?.connect()
        progressBar.visibility = View.VISIBLE
    }


}
