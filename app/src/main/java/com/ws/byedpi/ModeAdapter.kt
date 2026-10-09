package com.ws.byedpi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class ModeAdapter(
    private val onPick: (Int) -> Unit
) : RecyclerView.Adapter<ModeAdapter.VH>() {

    private var selected = 0

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvDesc)
    }

    fun select(i: Int) {
        val old = selected
        selected = i
        notifyItemChanged(old)
        notifyItemChanged(i)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mode, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = Config.strategies[position]
        val on = position == selected
        val dp = h.itemView.resources.displayMetrics.density
        val primary = MaterialColors.getColor(
            h.card, com.google.android.material.R.attr.colorPrimary
        )
        val surface = MaterialColors.getColor(
            h.card, com.google.android.material.R.attr.colorSurface
        )

        h.tvTitle.text = (if (on) "✓  " else "") + s.title
        h.tvDesc.text = s.desc
        h.card.strokeWidth = ((if (on) 3 else 1) * dp).toInt()
        h.card.strokeColor = if (on) primary else 0x33888888
        h.card.setCardBackgroundColor(
            if (on) ColorUtils.setAlphaComponent(primary, 45) else surface
        )
        h.card.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onPick(p)
        }
    }

    override fun getItemCount(): Int = Config.strategies.size
}
