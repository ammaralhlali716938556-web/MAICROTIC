package com.example.mikrotikbypass

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnScan: Button
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar

    // Default MikroTik RouterOS IPs
    private val targetIps = listOf("192.168.88.1", "192.168.1.1")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        tvStatus = findViewById(R.id.tvStatus)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnScan = findViewById(R.id.btnScan)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)

        // Set Click Listeners
        btnScan.setOnClickListener { performScan() }
        btnLogin.setOnClickListener { performLogin() }
    }

    private fun performScan() {
        tvStatus.text = "Scanning for MikroTik Captive Portal..."
        progressBar.visibility = View.VISIBLE
        btnScan.isEnabled = false

        Thread {
            var detected = false
            for (ip in targetIps) {
                if (checkMikroTikPortal(ip)) {
                    detected = true
                    Log.d("MikroTikScan", "Detected at $ip")
                    break
                }
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                btnScan.isEnabled = true
                if (detected) {
                    tvStatus.text = "Portal Detected! Enter credentials below."
                    btnLogin.isEnabled = true
                } else {
                    tvStatus.text = "No standard MikroTik portal found. Ensure you are connected to the Wi-Fi."
                    btnLogin.isEnabled = false
                }
            }
        }.start()
    }

    private fun performLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "Attempting Login..."
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false

        Thread {
            // Try common IPs for login
            val successIp = targetIps.firstOrNull { ip -> attemptLogin(ip, username, password) }

            runOnUiThread {
                progressBar.visibility = View.GONE
                btnLogin.isEnabled = true
                if (successIp != null) {
                    tvStatus.text = "Login Successful on $successIp! You are now connected."
                    Toast.makeText(this, "Access Granted", Toast.LENGTH_LONG).show()
                } else {
                    tvStatus.text = "Login Failed. Check credentials or network connection."
                    Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun checkMikroTikPortal(ip: String): Boolean {
        try {
            val url = URL("http://$ip/login")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val input = BufferedReader(InputStreamReader(connection.inputStream))
                val html = input.readText()
                input.close()
                connection.disconnect()
                
                // MikroTik portals usually contain 'login' and 'dst=' in the URL or form
                return html.contains("login", ignoreCase = true) && 
                       (html.contains("dst=", ignoreCase = true) || html.contains("username"))
            }
        } catch (e: Exception) {
            Log.e("MikroTikScan", "Error checking $ip", e)
        }
        return false
    }

    private fun attemptLogin(ip: String, username: String, password: String): Boolean {
        try {
            val url = URL("http://$ip/login")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            // Standard MikroTik Captive Portal POST parameters
            val postData = "username=$username&password=$password&dst=http%3A%2F%2F192.168.88.1%2Flogin%3Fsrc%3Dredirect"
            
            connection.outputStream.write(postData.toByteArray(StandardCharsets.UTF_8))

            val responseCode = connection.responseCode
            
            // Check if we were redirected (302) or got a 200 OK with login success message
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                val input = BufferedReader(InputStreamReader(connection.inputStream))
                val html = input.readText()
                input.close()
                connection.disconnect()
                
                // Look for success indicators in the HTML response
                return html.contains("logged in", ignoreCase = true) || 
                       html.contains("welcome", ignoreCase = true) ||
                       html.contains("dst=http") // Often redirects back after successful auth
            }
        } catch (e: Exception) {
            Log.e("MikroTikLogin", "Error logging into $ip", e)
        }
        return false
    }
}
