package com.katalonI.panel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GameAdapter(
    private val games: MutableList<Game>,
    private val onToggle: (Game, Boolean) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    inner class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgIcon)
        val name: TextView = view.findViewById(R.id.textName)
        val checkbox: CheckBox = view.findViewById(R.id.checkSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]
        holder.name.text = game.label
        holder.icon.setImageDrawable(game.icon)

        // Avoid triggering listener while recycling views
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = game.selected
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            game.selected = isChecked
            onToggle(game, isChecked)
        }
    }

    override fun getItemCount(): Int = games.size
}
