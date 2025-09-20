package com.oz.expressocr

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage

class MainActivity : AppCompatActivity() {

    private val codeList = mutableListOf<String>()
    private lateinit var adapter: CodeListAdapter

    @OptIn(ExperimentalGetImage::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val inputCode = findViewById<EditText>(R.id.input_code)
        val addCode = findViewById<Button>(R.id.add_code)
        val codeListView = findViewById<ListView>(R.id.code_list)
        val findPackage = findViewById<Button>(R.id.find_package)

        adapter = CodeListAdapter(codeList)
        codeListView.adapter = adapter

        addCode.setOnClickListener {
            val code = inputCode.text.toString()
            if (code.isNotEmpty()) {
                codeList.add(code)
                adapter.notifyDataSetChanged()
                inputCode.text.clear()
            }
        }

        findPackage.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putStringArrayListExtra("codes", ArrayList(codeList))
            startActivity(intent)
        }
    }
}