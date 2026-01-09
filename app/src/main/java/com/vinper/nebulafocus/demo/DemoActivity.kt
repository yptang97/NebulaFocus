package com.vinper.nebulafocus.demo

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.vinper.nebulafocus.AnchorLinearLayoutManager
import com.vinper.nebulafocus.FocusIndicatorLayout
import com.vinper.nebulafocus.FocusManager
import com.vinper.nebulafocus.R

/**
 * NebulaFocus 演示 Activity
 *
 * 展示焦点指示器在 RecyclerView 中的效果：
 * - Spring 物理动画
 * - 锚点滚动 (Keyline)
 * - 硬跟随模式（滚动时同步）
 * - 边缘裁剪
 *
 * @author Vinper
 */
class DemoActivity : AppCompatActivity() {

    private lateinit var focusIndicatorLayout: FocusIndicatorLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var focusManager: FocusManager

    private val adapter = DemoAdapter()

    /** 锚点位置（屏幕百分比） */
    private val keylinePercent = 0.35f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        initViews()
        setupFocusSystem()
        setupRecyclerView()
    }

    private fun initViews() {
        focusIndicatorLayout = findViewById(R.id.focus_indicator_layout)
        recyclerView = findViewById(R.id.recycler_view)
    }

    private fun setupFocusSystem() {
        focusManager = FocusManager().apply {
            // 启用边缘裁剪
            isClipEnabled = true

            // 焦点变化回调
            onFocusChangeListener = { focusedView ->
                focusedView?.let {
                    // 可以在这里处理焦点变化逻辑
                }
            }
        }

        // 绑定焦点管理器
        focusIndicatorLayout.bindFocusManager(focusManager)

        // 绑定 RecyclerView（启用硬跟随模式）
        focusManager.attachRecyclerView(recyclerView)
    }

    private fun setupRecyclerView() {
        // 使用锚点滚动布局管理器
        val layoutManager = AnchorLinearLayoutManager(
            context = this,
            orientation = RecyclerView.VERTICAL,
            reverseLayout = false,
            keylinePercent = keylinePercent
        )

        recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = this@DemoActivity.adapter

            // 加载数据
            this@DemoActivity.adapter.submitList(generateDemoData(30))

            // 延迟设置初始焦点，确保 View 已完成测量
            postDelayed({
                val firstItem = findViewHolderForAdapterPosition(0)?.itemView
                if (firstItem != null) {
                    firstItem.requestFocus()
                    // 手动触发焦点管理器更新（兜底）
                    focusManager.onFocusChanged(firstItem)
                    android.util.Log.d("NebulaFocus", "First item focused: ${firstItem.width}x${firstItem.height}")
                } else {
                    android.util.Log.e("NebulaFocus", "First item not found!")
                }
            }, 100)
        }
    }

    private fun generateDemoData(count: Int): List<DemoItem> {
        return (0 until count).map { index ->
            DemoItem(
                id = index,
                title = "Content Item $index",
                description = "This is the description for item $index"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        focusManager.detachRecyclerView(recyclerView)
    }
}

/**
 * 演示数据模型
 */
data class DemoItem(
    val id: Int,
    val title: String,
    val description: String
)

/**
 * 演示适配器
 */
class DemoAdapter : RecyclerView.Adapter<DemoAdapter.ViewHolder>() {

    private val items = mutableListOf<DemoItem>()

    fun submitList(newItems: List<DemoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_demo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.text_title)
        private val descText: TextView = itemView.findViewById(R.id.text_description)

        init {
            // 设置可获取焦点
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true

            // 设置焦点变化监听
            itemView.setOnFocusChangeListener { view, hasFocus ->
                // 可以在这里添加焦点状态的视觉反馈
                view.isSelected = hasFocus
            }

            // 设置点击事件
            itemView.setOnClickListener {
                // 处理点击事件
            }
        }

        fun bind(item: DemoItem) {
            titleText.text = item.title
            descText.text = item.description
        }
    }
}
