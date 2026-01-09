package com.vinper.nebulafocus.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.vinper.nebulafocus.AnchorLinearLayoutManager
import com.vinper.nebulafocus.FocusIndicatorLayout
import com.vinper.nebulafocus.FocusManager
import com.vinper.nebulafocus.R

/**
 * 最小化测试 - 使用普通 LinearLayoutManager
 */
class MinimalTestActivity : AppCompatActivity() {

    private lateinit var focusIndicatorLayout: FocusIndicatorLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var focusManager: FocusManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 XML 布局以确保属性正确传递
        setContentView(R.layout.activity_minimal_test)

        focusIndicatorLayout = findViewById(R.id.focus_indicator_layout)
        recyclerView = findViewById(R.id.recycler_view)

        Log.d("MinimalTest", "=== Activity Created ===")
        Log.d("MinimalTest", "FocusIndicatorLayout: $focusIndicatorLayout")
        Log.d("MinimalTest", "CursorView: ${focusIndicatorLayout.cursorView}")
        Log.d("MinimalTest", "CursorView background: ${focusIndicatorLayout.cursorView.background}")
        
        // 验证背景资源
        focusIndicatorLayout.post {
            val cursor = focusIndicatorLayout.cursorView
            Log.d("MinimalTest", "Cursor background after init: ${cursor.background}")
            
            // 如果背景为空，手动设置
            if (cursor.background == null) {
                cursor.setBackgroundResource(R.drawable.focus_cursor_background)
                Log.d("MinimalTest", "Manually set background resource")
            }
        }

        setupFocusSystem()
        setupRecyclerView()
    }

    private fun setupFocusSystem() {
        focusManager = FocusManager().apply {
            isAutoTrackEnabled = true
            isClipEnabled = true  // 启用边缘裁剪
            
            onFocusChangeListener = { focusedView ->
                Log.d("MinimalTest", "Focus changed to: $focusedView")
            }
        }
        
        focusIndicatorLayout.bindFocusManager(focusManager)
        focusManager.attachRecyclerView(recyclerView)
        
        Log.d("MinimalTest", "FocusManager bound with clipping enabled")
    }

    private fun setupRecyclerView() {
        // 使用锚点滚动布局管理器
        val layoutManager = AnchorLinearLayoutManager(
            context = this,
            orientation = RecyclerView.VERTICAL,
            reverseLayout = false,
            keylinePercent = 0.35f  // 焦点锁定在屏幕 35% 位置
        )
        
        Log.d("MinimalTest", "Using AnchorLinearLayoutManager with keyline at 35%")

        recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = MinimalAdapter((0 until 20).map { "Item $it" })

            // 延迟设置初始焦点
            postDelayed({
                val firstItem = findViewHolderForAdapterPosition(0)?.itemView
                if (firstItem != null) {
                    Log.d("MinimalTest", "=== Setting initial focus ===")
                    Log.d("MinimalTest", "Item size: ${firstItem.width}x${firstItem.height}")
                    
                    firstItem.requestFocus()
                    
                    // 强制触发
                    postDelayed({
                        focusManager.onFocusChanged(firstItem)
                        
                        val cursor = focusIndicatorLayout.cursorView
                        Log.d("MinimalTest", "=== Cursor State ===")
                        Log.d("MinimalTest", "Visibility: ${cursor.visibility}")
                        Log.d("MinimalTest", "Size: ${cursor.width}x${cursor.height}")
                        Log.d("MinimalTest", "LayoutParams: ${(cursor.layoutParams as? ViewGroup.LayoutParams)?.width}x${(cursor.layoutParams as? ViewGroup.LayoutParams)?.height}")
                        Log.d("MinimalTest", "Translation: (${cursor.translationX}, ${cursor.translationY})")
                        Log.d("MinimalTest", "Background: ${cursor.background}")
                        Log.d("MinimalTest", "Elevation: ${cursor.elevation}")
                        
                        // 再次检查
                        postDelayed({
                            Log.d("MinimalTest", "=== Final Check ===")
                            Log.d("MinimalTest", "Cursor actual size: ${cursor.width}x${cursor.height}")
                        }, 100)
                    }, 100)
                } else {
                    Log.e("MinimalTest", "First item not found!")
                }
            }, 300)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        focusManager.detachRecyclerView(recyclerView)
        focusManager.detach()
    }
}

class MinimalAdapter(private val items: List<String>) : RecyclerView.Adapter<MinimalAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 0, 40, 0)
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2D2D2D"))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        init {
            textView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    textView.setBackgroundColor(Color.parseColor("#3D3D3D"))
                    Log.d("MinimalTest", "Item ${adapterPosition} focused")
                } else {
                    textView.setBackgroundColor(Color.parseColor("#2D2D2D"))
                }
            }
        }

        fun bind(text: String) {
            textView.text = text
        }
    }
}
