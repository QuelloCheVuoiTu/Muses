package com.example.museo_gui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView

class TypeAdapter(
    private val types: MutableList<String?>,
    private val selectedTypes: MutableSet<String?>
) : RecyclerView.Adapter<TypeAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_type_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val type = types.get(position)
        holder.checkBox.setText(type)
        holder.checkBox.setChecked(selectedTypes.contains(type))

        holder.checkBox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                selectedTypes.add(type)
            } else {
                selectedTypes.remove(type)
            }
        })
    }

    override fun getItemCount(): Int {
        return types.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var checkBox: CheckBox

        init {
            checkBox = itemView.findViewById<CheckBox?>(R.id.checkbox_type)
        }
    }
}