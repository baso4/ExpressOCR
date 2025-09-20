package com.oz.expressocr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.BaseAdapter

class CodeListAdapter(private val codes: MutableList<String>) : BaseAdapter() {

    override fun getCount(): Int = codes.size

    override fun getItem(position: Int): Any = codes[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val context = parent?.context ?: return View(parent!!.context)
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_code, parent, false)

        val codeText = view.findViewById<TextView>(R.id.code_text)
        val deleteButton = view.findViewById<Button>(R.id.delete_button)

        codeText.text = codes[position]
        deleteButton.setOnClickListener {
            codes.removeAt(position)
            notifyDataSetChanged()
        }

        return view
    }
}