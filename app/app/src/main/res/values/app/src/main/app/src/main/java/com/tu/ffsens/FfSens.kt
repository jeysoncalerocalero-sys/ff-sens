package com.tu.ffsens

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FfSens {

    const val FF_DIR = "/storage/emulated/0/Android/data/com.dts.freefireth"

    private val intRe = Regex("<int\\s+name=\"([^\"]+)\"\\s+value=\"(-?\\d+)\"\\s*/>")
    private val interest = Regex("(?i)(sens|aim|scope|reddot|sniper|spread|disp|general|free)")

    fun findPrefs(): File? {
        val root = File(FF_DIR)
        if (!root.exists()) return null
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".xml") && it.name.contains("pref", true) }
            .maxByOrNull { it.length() }
    }

    fun keysOf(text: String): List<Pair<String, Int>> =
        intRe.findAll(text)
            .filter { interest.containsMatchIn(it.groupValues[1]) }
            .map { it.groupValues[1] to it.groupValues[2].toInt() }
            .distinctBy { it.first }
            .toList()

    fun classify(key: String): String {
        val k = key.lowercase()
        return when {
            k.contains("spread") || k.contains("disp") -> "spread"
            k.contains("sniper") || k.contains("8x") -> "sniper"
            k.contains("4x") -> "4x"
            k.contains("2x") -> "2x"
            k.contains("red") -> "red"
            k.contains("aim") -> "aim"
            k.contains("general") || k.contains("free") || k.contains("drag") -> "general"
            else -> "other"
        }
    }

    fun apply(text: String, values: Map<String, Int>): String =
        intRe.replace(text) { m ->
            val key = m.groupValues[1]
            val v = values[key] ?: return@replace m.value
            "<int name=\"$key\" value=\"$v\" />"
        }

    fun maxAll(keys: List<Pair<String, Int>>): Map<String, Int> =
        keys.associate { it.first to 100 }

    fun preset3Dedos(keys: List<Pair<String, Int>>): Map<String, Int> =
        keys.associate { key ->
            key.first to when (classify(key.first)) {
                "spread" -> 0
                "sniper" -> 55
                "4x" -> 60
                "2x" -> 70
                "red" -> 80
                "aim" -> 55
                "general" -> 100
                else -> key.second
            }
        }

    fun antiSpread(keys: List<Pair<String, Int>>): Map<String, Int> =
        keys.associate { key ->
            key.first to if (classify(key.first) == "spread") 0 else key.second
        }

    fun backupDir(): File {
        val d = File("/storage/emulated/0/Documents/FFSens")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun backup(file: File, text: String): File {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(backupDir(), "backup-$ts-${file.name}")
        out.writeText(text)
        return out
    }

    fun lastBackup(): File? =
        backupDir().listFiles()?.filter { it.name.endsWith(".xml") }?.maxByOrNull { it.lastModified() }
}
