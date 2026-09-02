package com.zcode.remote

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.URI

class AddLinkActivity : AppCompatActivity() {

    private lateinit var store: ConnectionsStore
    private lateinit var linkInput: TextInputEditText

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result?.contents?.trim().orEmpty()
        if (contents.isEmpty()) {
            snackbar(getString(R.string.err_invalid_link))
            return@registerForActivityResult
        }
        linkInput.setText(contents)
        trySave(silent = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)
        store = ConnectionsStore(this)
        linkInput = findViewById(R.id.link_input)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener { trySave(silent = false) }
        findViewById<MaterialButton>(R.id.btn_scan).setOnClickListener {
            scanLauncher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(getString(R.string.scan_prompt))
                setBeepEnabled(false)
            })
        }
    }

    private fun trySave(silent: Boolean) {
        val raw = linkInput.text?.toString()?.trim().orEmpty()
        val url = normalize(raw)
        if (url == null) {
            snackbar(getString(R.string.err_invalid_link))
            return
        }
        val name = store.defaultNameFor(url, store.list().size + 1)
        store.add(name, url)
        if (!silent) Toast.makeText(this, R.string.saved_ok, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun normalize(raw: String): String? {
        var t = raw.trim()
        if (t.startsWith("zcode://")) t = "https://" + t.removePrefix("zcode://")
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://$t"
        return try {
            val host = URI(t).host
            if (host.isNullOrBlank()) null else t
        } catch (e: Exception) {
            null
        }
    }

    private fun snackbar(msg: String) {
        Snackbar.make(linkInput, msg, Snackbar.LENGTH_SHORT).show()
    }
}