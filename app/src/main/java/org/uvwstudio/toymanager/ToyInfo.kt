package org.uvwstudio.toymanager


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.uvwstudio.toymanager.R


data class InventoryItem(
    val name: String,
    val rfid: String,
    val detail: String = "",
    val photoPath: String? = null // 以后可以用它加载照片
)

class InventoryAdapter(
    private val items: List<InventoryItem>,
    private val onItemClick: (InventoryItem) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder>() {

    class InventoryViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvRfid: TextView = view.findViewById(R.id.tvRfid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_row, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = items[position]
        holder.tvIndex.text = (position + 1).toString()
        holder.tvName.text = item.name
        holder.tvRfid.text = item.rfid

        holder.view.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
