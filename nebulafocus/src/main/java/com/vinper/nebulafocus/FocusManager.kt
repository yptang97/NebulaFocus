package com.vinper.nebulafocus

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/**
 * 焦点管理器
 *
 * 核心职责：
 * - 监听全局焦点变化
 * - 坐标映射计算
 * - 滚动状态管理（硬跟随模式）
 * - 边缘裁剪处理
 *
 * @author Vinper
 */
class FocusManager {

    // region 配置

    /** 是否启用自动焦点跟踪 */
    var isAutoTrackEnabled: Boolean = true

    /** 焦点变化回调 */
    var onFocusChangeListener: ((View?) -> Unit)? = null

    /** 是否启用边缘裁剪 */
    var isClipEnabled: Boolean = true

    // endregion

    // region 状态

    private var focusIndicatorLayout: FocusIndicatorLayout? = null
    private var cursorView: FocusCursorView? = null
    private var focusLayer: View? = null

    /** 当前焦点 View */
    private var currentFocusedView: View? = null

    /** 正在监听的 RecyclerView 集合 */
    private val attachedRecyclerViews = mutableSetOf<RecyclerView>()

    /** 当前是否处于滚动状态 */
    private var isScrolling: Boolean = false

    // endregion

    // region 坐标计算缓存

    private val targetLocation = IntArray(2)
    private val focusLayerLocation = IntArray(2)
    private val visibleRect = Rect()

    // endregion

    // region 监听器

    private val globalFocusChangeListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (isAutoTrackEnabled) {
            onFocusChanged(newFocus)
        }
    }

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (isScrolling && currentFocusedView != null) {
            // 硬跟随模式：每帧强制同步位置
            updateCursorPositionHard()
        }
        true
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_IDLE -> {
                    isScrolling = false
                    cursorView?.isHardFollowMode = false
                    // 滚动结束，使用动画平滑过渡
                    currentFocusedView?.let { updateCursorPosition(it, animate = true) }
                }
                RecyclerView.SCROLL_STATE_DRAGGING,
                RecyclerView.SCROLL_STATE_SETTLING -> {
                    isScrolling = true
                    cursorView?.isHardFollowMode = true
                }
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (isScrolling && currentFocusedView != null) {
                updateCursorPositionHard()
            }
        }
    }

    // endregion

    /**
     * 绑定到 FocusIndicatorLayout
     */
    fun attach(layout: FocusIndicatorLayout) {
        detach() // 先清理旧绑定

        focusIndicatorLayout = layout
        cursorView = layout.cursorView
        focusLayer = layout.getFocusLayer()

        // 注册全局焦点监听
        layout.viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusChangeListener)
        // 注册 PreDraw 监听（用于硬跟随模式）
        layout.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    /**
     * 解除绑定
     */
    fun detach() {
        focusIndicatorLayout?.let { layout ->
            layout.viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusChangeListener)
            layout.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }

        // 清理 RecyclerView 监听
        attachedRecyclerViews.forEach { rv ->
            rv.removeOnScrollListener(scrollListener)
        }
        attachedRecyclerViews.clear()

        focusIndicatorLayout = null
        cursorView = null
        focusLayer = null
        currentFocusedView = null
    }

    /**
     * 绑定 RecyclerView（启用硬跟随模式）
     */
    fun attachRecyclerView(recyclerView: RecyclerView) {
        if (attachedRecyclerViews.add(recyclerView)) {
            recyclerView.addOnScrollListener(scrollListener)
        }
    }

    /**
     * 解绑 RecyclerView
     */
    fun detachRecyclerView(recyclerView: RecyclerView) {
        if (attachedRecyclerViews.remove(recyclerView)) {
            recyclerView.removeOnScrollListener(scrollListener)
        }
    }

    /**
     * 焦点变化回调
     */
    fun onFocusChanged(focusedView: View?) {
        if (focusedView == null) {
            // 焦点丢失
            currentFocusedView = null
            onFocusChangeListener?.invoke(null)
            return
        }

        // 检查是否是有效的焦点目标
        if (!isValidFocusTarget(focusedView)) {
            return
        }

        currentFocusedView = focusedView
        onFocusChangeListener?.invoke(focusedView)

        // 更新指示器位置
        updateCursorPosition(focusedView, animate = !isScrolling)
    }

    /**
     * 手动移动指示器到指定 View
     */
    fun moveCursorTo(targetView: View, animate: Boolean = true) {
        currentFocusedView = targetView
        updateCursorPosition(targetView, animate)
    }

    /**
     * 隐藏指示器
     */
    fun hideCursor() {
        cursorView?.visibility = View.INVISIBLE
    }

    /**
     * 显示指示器
     */
    fun showCursor() {
        cursorView?.visibility = View.VISIBLE
    }

    /**
     * 重置指示器状态
     */
    fun reset() {
        currentFocusedView = null
        cursorView?.reset()
    }

    // region 内部实现

    private fun isValidFocusTarget(view: View): Boolean {
        // 排除指示器自身和焦点层
        if (view === cursorView || view === focusLayer) {
            return false
        }
        // 必须是可见的
        if (view.visibility != View.VISIBLE) {
            return false
        }
        // 必须有有效尺寸
        if (view.width <= 0 || view.height <= 0) {
            return false
        }
        return true
    }

    private fun updateCursorPosition(targetView: View, animate: Boolean) {
        val cursor = cursorView ?: return
        val layer = focusLayer ?: return

        // 计算目标 View 在 Window 中的位置
        targetView.getLocationInWindow(targetLocation)
        layer.getLocationInWindow(focusLayerLocation)

        // 相对坐标 = 目标绝对坐标 - 焦点层绝对坐标
        val relativeX = (targetLocation[0] - focusLayerLocation[0]).toFloat()
        val relativeY = (targetLocation[1] - focusLayerLocation[1]).toFloat()

        android.util.Log.d("FocusManager", "Update cursor: target=(${targetLocation[0]}, ${targetLocation[1]}), " +
                "layer=(${focusLayerLocation[0]}, ${focusLayerLocation[1]}), " +
                "relative=($relativeX, $relativeY), size=${targetView.width}x${targetView.height}")

        // 移动指示器
        cursor.moveTo(
            x = relativeX,
            y = relativeY,
            width = targetView.width,
            height = targetView.height,
            animate = animate
        )

        // 处理边缘裁剪
        if (isClipEnabled) {
            updateClipBounds(targetView)
        }
    }

    private fun updateCursorPositionHard() {
        val targetView = currentFocusedView ?: return
        val cursor = cursorView ?: return
        val layer = focusLayer ?: return

        // 实时计算位置
        targetView.getLocationInWindow(targetLocation)
        layer.getLocationInWindow(focusLayerLocation)

        val relativeX = (targetLocation[0] - focusLayerLocation[0]).toFloat()
        val relativeY = (targetLocation[1] - focusLayerLocation[1]).toFloat()

        // 硬跟随：直接设置位置，无动画
        cursor.hardFollowTo(relativeX, relativeY)

        // 更新裁剪
        if (isClipEnabled) {
            updateClipBounds(targetView)
        }
    }

    private fun updateClipBounds(targetView: View) {
        val cursor = cursorView ?: return

        // 获取目标 View 的可见区域
        val isVisible = targetView.getLocalVisibleRect(visibleRect)

        if (!isVisible) {
            // 完全不可见，隐藏指示器
            cursor.visibility = View.INVISIBLE
            return
        }

        cursor.visibility = View.VISIBLE

        // 计算并应用裁剪
        cursor.calculateAndApplyClip(
            targetVisibleRect = visibleRect,
            targetWidth = targetView.width,
            targetHeight = targetView.height
        )
    }

    // endregion
}
