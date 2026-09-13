package com.katalonI.panel

import android.content.Context

object Prefs {
    private const val NAME = "kata_loni_prefs"
    private const val KEY_SELECTED_GAMES = "selected_games"
    private const val KEY_SERVER_IP = "server_ip"
    private const val KEY_SERVER_PORT = "server_port"

    fun getSelectedGames(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SELECTED_GAMES, emptySet()) ?: emptySet()
    }

    fun setSelectedGames(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SELECTED_GAMES, packages).apply()
    }

    fun getServerIp(context: Context): String {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_IP, "") ?: ""
    }

    fun getServerPort(context: Context): Int {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SERVER_PORT, 443)
    }

    fun saveServer(context: Context, ip: String, port: Int) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_IP, ip).putInt(KEY_SERVER_PORT, port).apply()
    }
}
