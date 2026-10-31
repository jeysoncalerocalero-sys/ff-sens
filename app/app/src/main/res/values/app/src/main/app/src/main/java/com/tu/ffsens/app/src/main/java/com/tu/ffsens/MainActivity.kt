package com.tu.ffsens

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private var prefsFile: File? = null
    private var raw = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E0B16"))
            setPadding(48, 64, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "FF SENS"
            setTextColor(Color.parseColor("#C9A8FF"))
            textSize = 26f
        })

        root.addView(TextView(this).apply {
            text = "Sensibilidad real, escrita en la config de Free Fire. Cierra el juego antes de aplicar."
            setTextColor(Color.parseColor("#9C93AE"))
            textSize = 12f
            setPadding(0, 8, 0, 28)
        })

        fun btn(text: String, bg: Int, fg: Int, action: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                setBackgroundColor(bg)
                setTextColor(fg)
                setOnClickListener { action() }
                root.addView(this)
            }

        btn("1. DAR ACCESO A ARCHIVOS", Color.parseColor("#C9A8FF"), Color.parseColor("#1A0F2E")) {
            pedirPermiso()
        }

        btn("2. BUSCAR CONFIG DE FREE FIRE", Color.parseColor("#2A2140"), Color.WHITE) {
            buscar()
        }

        status = TextView(this).apply {
            text = "Sin buscar todavía."
            setTextColor(Color.parseColor("#9C93AE"))
            textSize = 12f
            setPadding(0, 24, 0, 16)
        }
        root.addView(status)

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presets.addView(Button(this).apply {
            text = "MAX"
            setOnClickListener { aplicar(FfSens.maxAll(claves())) }
        })
        presets.addView(Button(this).apply {
            text = "HS 3 DEDOS"
            setOnClickListener { aplicar(FfSens.preset3Dedos(claves())) }
        })
        presets.addView(Button(this).apply {
            text = "ANTI-DISP"
            setOnClickListener { aplicar(FfSens.antiSpread(claves())) }
        })
        root.addView(presets)

        btn("RESTAURAR ÚLTIMO RESPALDO", Color.parseColor("#2A2140"), Color.WHITE) { restaurar() }

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 20)
        }
        root.addView(list)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun pedirPermiso() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            toast("Ya tienes el acceso")
        }
    }

    private fun tienePermiso(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    private fun buscar() {
        if (!tienePermiso()) {
            toast("Primero da el acceso a archivos")
            return
        }
        val f = FfSens.findPrefs()
        if (f == null) {
            status.text = "No encontré la config. Abre Free Fire una vez, cierra, y vuelve a buscar."
            return
        }
        prefsFile = f
        raw = runCatching { f.readText() }.getOrDefault("")
        status.text = "Archivo: ${f.name}\nClaves de sensibilidad: ${claves().size}"
        pintar()
    }

    private fun claves(): List<Pair<String, Int>> = FfSens.keysOf(raw)

    private fun pintar() {
        list.removeAllViews()
        for ((k, v) in claves()) {
            val tv = TextView(this).apply {
                text = "$k = $v"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, 14, 0, 14)
                setOnClickListener { editar(k, v) }
            }
            list.addView(tv)
        }
        if (claves().isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No hay claves de sensibilidad en el archivo."
                setTextColor(Color.parseColor("#9C93AE"))
            })
        }
    }

    private fun editar(key: String, actual: Int) {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(actual.toString())
            setGravity(Gravity.CENTER)
        }
        AlertDialog.Builder(this)
            .setTitle(key)
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                val n = et.text.toString().toIntOrNull() ?: return@setPositiveButton
                val map = claves().associate { it.first to (if (it.first == key) n else it.second) }
                aplicar(map)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aplicar(values: Map<String, Int>) {
        val f = prefsFile ?: return toast("Busca la config primero")
        if (values.isEmpty()) return toast("No hay claves que escribir")
        val nuevo = FfSens.apply(raw, values)
        runCatching {
            FfSens.backup(f, raw)
            f.writeText(nuevo)
            raw = nuevo
        }.onFailure { toast("Error: ${it.message}") }
            .onSuccess {
                toast("Aplicado. Respaldo guardado en Documents/FFSens")
                pintar()
            }
    }

    private fun restaurar() {
        val f = prefsFile ?: return toast("Busca la config primero")
        val b = FfSens.lastBackup() ?: return toast("No hay respaldos")
        runCatching {
            f.writeText(b.readText())
            raw = f.readText()
        }.onSuccess { toast("Restaurado desde ${b.name}"); pintar() }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
