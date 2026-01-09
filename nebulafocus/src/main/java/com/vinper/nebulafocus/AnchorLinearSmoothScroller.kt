package com.vinper.nebulafocus

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

/**
 * 锚点线性平滑滚动器
 *
 * 实现 Keyline 锚点对齐策略：
 * - 头部自由区：焦点自由移动
 * - 锚点锁定区：焦点锁定在屏幕特定位置，列表滚动填充
 * - 尾部自由区：焦点自由移动
 *
 * @param context 上下文
 * @param keylinePercent 锚点位置（屏幕高度百分比，0.0-1.0，默认 0.35）
 *
 * @author Vinper
 */
open class AnchorLinearSmoothScroller(
    context: Context,
    private val keylinePercent: Float = DEFAULT_KEYLINE_PERCENT
) : LinearSmoothScroller(context) {

    companion object {
        /** 默认锚点位置（屏幕 35% 处，黄金分割） */
        const val DEFAULT_KEYLINE_PERCENT = 0.35f

        /** 居中锚点 */
        const val KEYLINE_CENTER = 0.5f

        /** 顶部对齐 */
        const val KEYLINE_TOP = 0f
    }

    /** 是否启用锚点对齐（可动态关闭） */
    var isAnchorEnabled: Boolean = true

    /** 锚点偏移修正值（像素） */
    var keylineOffset: Int = 0

    /**
     * 计算滚动距离以适配目标位置
     *
     * 重写此方法实现锚点对齐逻辑
     */
    override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int
    ): Int {
        if (!isAnchorEnabled) {
            // 禁用锚点时使用默认行为
            return super.calculateDtToFit(viewStart, viewEnd, boxStart, boxEnd, snapPreference)
        }

        // 计算容器（RecyclerView）高度
        val containerHeight = boxEnd - boxStart

        // 计算锚点在容器中的绝对位置
        val keylinePosition = (containerHeight * keylinePercent).toInt() + keylineOffset

        // 计算需要滚动的距离：将 View 顶部对齐到锚点位置
        // dt = viewStart - (boxStart + keylinePosition)
        // 简化：dt = viewStart - boxStart - keylinePosition
        val dt = viewStart - boxStart - keylinePosition

        return dt
    }

    /**
     * 计算滚动速度（每英寸像素）
     *
     * 可重写以自定义滚动速度
     */
    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        // 默认速度：25ms/inch，可根据需要调整
        return 25f / displayMetrics.densityDpi
    }

    /**
     * 滚动时间上限（毫秒）
     */
    override fun calculateTimeForScrolling(dx: Int): Int {
        // 最长 200ms，避免长距离滚动过慢
        return minOf(super.calculateTimeForScrolling(dx), 200)
    }
}

/**
 * 创建锚点滚动器的扩展函数
 */
fun RecyclerView.createAnchorScroller(
    keylinePercent: Float = AnchorLinearSmoothScroller.DEFAULT_KEYLINE_PERCENT
): AnchorLinearSmoothScroller {
    return AnchorLinearSmoothScroller(context, keylinePercent)
}

/**
 * 平滑滚动到指定位置（使用锚点对齐）
 */
fun RecyclerView.smoothScrollToPositionWithAnchor(
    position: Int,
    keylinePercent: Float = AnchorLinearSmoothScroller.DEFAULT_KEYLINE_PERCENT
) {
    val scroller = AnchorLinearSmoothScroller(context, keylinePercent).apply {
        targetPosition = position
    }
    layoutManager?.startSmoothScroll(scroller)
}

/**
 * 自定义 LayoutManager 接口，支持锚点滚动
 */
interface AnchorScrollSupport {

    /** 锚点位置百分比 */
    var keylinePercent: Float

    /** 是否启用锚点滚动 */
    var isAnchorScrollEnabled: Boolean

    /**
     * 执行锚点滚动
     */
    fun smoothScrollToPositionWithAnchor(recyclerView: RecyclerView, position: Int)
}

/**
 * 垂直方向锚点滚动布局管理器
 *
 * 封装 LinearLayoutManager + AnchorLinearSmoothScroller
 */
class AnchorLinearLayoutManager @JvmOverloads constructor(
    context: Context,
    orientation: Int = RecyclerView.VERTICAL,
    reverseLayout: Boolean = false,
    override var keylinePercent: Float = AnchorLinearSmoothScroller.DEFAULT_KEYLINE_PERCENT
) : androidx.recyclerview.widget.LinearLayoutManager(context, orientation, reverseLayout),
    AnchorScrollSupport {

    override var isAnchorScrollEnabled: Boolean = true

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State?,
        position: Int
    ) {
        if (isAnchorScrollEnabled) {
            smoothScrollToPositionWithAnchor(recyclerView, position)
        } else {
            super.smoothScrollToPosition(recyclerView, state, position)
        }
    }

    override fun smoothScrollToPositionWithAnchor(recyclerView: RecyclerView, position: Int) {
        val scroller = AnchorLinearSmoothScroller(recyclerView.context, keylinePercent).apply {
            targetPosition = position
            isAnchorEnabled = isAnchorScrollEnabled
        }
        startSmoothScroll(scroller)
    }
}

/**
 * 计算目标 Item 滚动到锚点位置需要的滚动距离
 *
 * @param recyclerView RecyclerView 实例
 * @param targetPosition 目标位置
 * @param keylinePercent 锚点百分比
 * @return 需要滚动的像素距离（正值向上滚动，负值向下滚动）
 */
fun calculateAnchorScrollDistance(
    recyclerView: RecyclerView,
    targetPosition: Int,
    keylinePercent: Float = AnchorLinearSmoothScroller.DEFAULT_KEYLINE_PERCENT
): Int {
    val layoutManager = recyclerView.layoutManager ?: return 0
    val targetView = layoutManager.findViewByPosition(targetPosition) ?: return 0

    val containerHeight = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
    val keylineY = (containerHeight * keylinePercent).toInt()

    // 目标 View 顶部相对于 RecyclerView 的位置
    val targetTop = targetView.top - recyclerView.paddingTop

    // 需要滚动的距离 = 当前位置 - 目标锚点位置
    return targetTop - keylineY
}
