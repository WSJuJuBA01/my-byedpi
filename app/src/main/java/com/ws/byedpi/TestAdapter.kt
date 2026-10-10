package com.ws.byedpi

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TestAdapter : RecyclerView.Adapter<TestAdapter.VH>() {

    private var rows: List<TestRow> = emptyList()
    var proxyMode = false

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvHost: TextView = v.findViewById(R.id.tvHost)
        val tvDirect: TextView = v.findViewById(R.id.tvDirect)
        val tvProxy: TextView = v.findViewById(R.id.tvProxy)
    }

    fun submit(list: List<TestRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_test, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        h.tvName.text = r.target.name
        h.tvHost.text = r.target.host
        bind(h.tvDirect, "Напрямую", r.direct)
        if (proxyMode) {
            bind(h.tvProxy, "ByeDPI", r.proxy)
        } else {
            h.tvProxy.text = "ByeDPI: VPN выключен"
            h.tvProxy.setTextColor(Color.GRAY)
        }
    }

    private fun bind(tv: TextView, label: String, r: CheckResult?) {
        when {
            r == null -> {
                tv.text = "$label: …"
                tv.setTextColor(Color.GRAY)
            }
            r.ok -> {
                tv.text = "$label: ✓ ${r.ms} мс"
                tv.setTextColor(Color.parseColor("#4CAF50"))
            }
            else -> {
                tv.text = "$label: ✗ ${r.error}"
                tv.setTextColor(Color.parseColor("#EF5350"))
            }
        }
    }

    override fun getItemCount(): Int = rows.size
}
