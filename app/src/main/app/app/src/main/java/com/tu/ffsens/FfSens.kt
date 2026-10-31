package com.tu.ffsens

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FfSens {

    private const val PKG = "com.dts.freefireth"

    private fun candidatos() = listOf(
        File("/sdcard/Android/data/$PKG/files"),
        File("/sdcard/Android/data/$PKG/files/shared_prefs"),
        File("/sdcard/Android/data/$PKG/shared_prefs"),
        File("/sdcard/Android/data/$PKG/files/Download"),
        File("/sdcard/Download"),
        File("/sdcard/Documents"),
        File("/data/data/$PKG/shared_prefs"),
        File("/data/user/0/$PKG/shared_prefs")
    )

    fun findPrefs(): File? {
        for (dir in candidatos()) {
            if (!dir.isDirectory) continue
            val hit = dir.walkTopDown()
                .maxDepth(3)
                .filter { it.isFile && it.extension == "xml" }
                .filter { it.length() in 1..8_000_000L }
                .firstOrNull { runCatching { keysOf(it.readText()).isNotEmpty() }.getOrDefault(false) }
            if (hit != null) return hit
        }
        return null
    }

    private val RE = Regex("""<int\s+name="([^"]*[Ss]ens[^"]*)"\s+value="(-?\d+)"\s*/>""")

    fun keysOf(raw: String): List<Pair<String, Int>> =
        RE.findAll(raw).map { it.groupValues[1] to it.groupValues[2].toInt() }.toList()

    fun apply(raw: String, values: Map<String, Int>): String {
        var out = raw
        for ((k, v) in values) {
            out = out.replace(
                Regex("""(<int\s+name="${Regex.escape(k)}"\s+value=")(-?\d+)(")""")
            ) { m -> m.groupValues[1] + v + m.groupValues[3] }
        }
        return out
    }

    private fun dirRespaldos(): File {
        val d = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "FFSens"
        )
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun backup(f: File, raw: String) {
        val sello = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        File(dirRespaldos(), "${f.nameWithoutExtension}_$sello.xml").writeText(raw)
    }

    fun lastBackup(): File? =
        dirRespaldos().listFiles()
            ?.filter { it.isFile && it.extension == "xml" }
            ?.maxByOrNull { it.lastModified() }

    private fun clase(k: String): String {
        val n = k.lowercase()
        return when {
            n.contains("freelook") || n.contains("free_look") -> "free"
            n.contains("sniper") || n.contains("awm") || n.contains("8x") -> "sniper"
            n.contains("4x") -> "x4"
            n.contains("2x") -> "x2"
            n.contains("reddot") || n.contains("red_dot") || n.contains("holo") -> "reddot"
            else -> "general"
        }
    }

    fun maxAll(keys: List<Pair<String, Int>>): Map<String, Int> =
        keys.associate { it.first to 200 }

    fun preset3Dedos(keys: List<Pair<String, Int>>): Map<String, Int> =
        keys.associate { it.first to when (clase(it.first)) {
            "general" -> 190
            "reddot" -> 165
            "x2" -> 150
            "x4" -> 140
            "sniper" -> 120
            "free" -> 180
            else -> 160
        } }

    fun antiSpread(keys: List<Pair<String, Int>>): Map<String, Int> =
        keys.associate { it.first to when (clase(it.first)) {
            "general" -> 185
            "reddot" -> 130
            "x2" -> 115
            "x4" -> 100
            "sniper" -> 95
            "free" -> 170
            else -> 140
        } }
