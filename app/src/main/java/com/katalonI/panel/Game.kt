package com.katalonI.panel

import android.graphics.drawable.Drawable

data class Game(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var selected: Boolean
)
