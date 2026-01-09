package com.vinper.nebulafocus

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * 焦点指示器容器布局
 *
 * 采用隔离渲染层架构：
 * - contentContainer: 放置业务 UI
 * - focusLayer: 透明覆盖层，仅放置指示器 View
 *
 * 性能关键：指示器尺寸变化只触发 focusLayer 重排，不影响业务层
 *
 * @author Vinper
 */
class FocusIndicatorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 业务内容容器 */
    private val contentContainer: FrameLayout

    /** 焦点层容器 */
    private val focusLayer: FrameLayout

    /** 焦点指示器视图 */
    val cursorView: FocusCursorView

    /** 焦点管理器 */
    private var focusManager: FocusManager? = null

    init {
        // 创建业务内容容器
        contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            elevation = 0f
            android.util.Log.d("FocusIndicatorLayout", "ContentContainer created")
        }
        super.addView(contentContainer)

        // 创建焦点层（透明、不裁剪子 View）
        focusLayer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false
            // 不拦截任何触摸事件
            isClickable = false
            isFocusable = false
            // 确保在最上层
            elevation = 100f
            android.util.Log.d("FocusIndicatorLayout", "FocusLayer created with elevation: $elevation")
        }
        super.addView(focusLayer)

        // 解析自定义属性并创建指示器
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FocusIndicatorLayout)
        val cursorBackground = typedArray.getResourceId(
            R.styleable.FocusIndicatorLayout_cursorBackground, 0
        )
        val cursorPadding = typedArray.getDimensionPixelSize(
            R.styleable.FocusIndicatorLayout_cursorPadding, 0
        )
        val cursorPaddingLeft = typedArray.getDimensionPixelSize(
            R.styleable.FocusIndicatorLayout_cursorPaddingLeft, cursorPadding
        )
        val cursorPaddingTop = typedArray.getDimensionPixelSize(
            R.styleable.FocusIndicatorLayout_cursorPaddingTop, cursorPadding
        )
        val cursorPaddingRight = typedArray.getDimensionPixelSize(
            R.styleable.FocusIndicatorLayout_cursorPaddingRight, cursorPadding
        )
        val cursorPaddingBottom = typedArray.getDimensionPixelSize(
            R.styleable.FocusIndicatorLayout_cursorPaddingBottom, cursorPadding
        )
        typedArray.recycle()

        // 创建焦点指示器
        cursorView = FocusCursorView(context).apply {
            // 初始尺寸设为 1x1 而非 0x0，避免被系统忽略
            layoutParams = LayoutParams(1, 1)
            if (cursorBackground != 0) {
                setBackgroundResource(cursorBackground)
            }
            setCursorPadding(cursorPaddingLeft, cursorPaddingTop, cursorPaddingRight, cursorPaddingBottom)
            
            // 确保指示器在焦点层的最上方
            elevation = 10f
            
            android.util.Log.d("FocusIndicatorLayout", "CursorView created with background: $cursorBackground, elevation: $elevation")
        }
        focusLayer.addView(cursorView)
    }

    /**
     * 获取焦点层容器（用于坐标计算）
     */
    fun getFocusLayer(): View = focusLayer

    /**
     * 获取内容容器
     */
    fun getContentContainer(): ViewGroup = contentContainer

    /**
     * 设置指示器背景
     */
    fun setCursorBackground(resId: Int) {
        cursorView.setBackgroundResource(resId)
    }

    /**
     * 设置指示器 Padding
     */
    fun setCursorPadding(padding: Int) {
        cursorView.setCursorPadding(padding)
    }

    /**
     * 设置指示器四边 Padding
     */
    fun setCursorPadding(left: Int, top: Int, right: Int, bottom: Int) {
        cursorView.setCursorPadding(left, top, right, bottom)
    }

    /**
     * 绑定焦点管理器
     */
    fun bindFocusManager(manager: FocusManager) {
        focusManager = manager
        manager.attach(this)
    }

    /**
     * 解绑焦点管理器
     */
    fun unbindFocusManager() {
        focusManager?.detach()
        focusManager = null
    }

    /**
     * 手动触发焦点更新
     */
    fun notifyFocusChanged(focusedView: View?) {
        focusManager?.onFocusChanged(focusedView)
    }

    // region View 拦截 - 将子 View 添加到 contentContainer

    override fun addView(child: View?) {
        if (isInternalView(child)) {
            super.addView(child)
        } else {
            contentContainer.addView(child)
        }
    }

    override fun addView(child: View?, index: Int) {
        if (isInternalView(child)) {
            super.addView(child, index)
        } else {
            contentContainer.addView(child, index)
        }
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        if (isInternalView(child)) {
            super.addView(child, params)
        } else {
            contentContainer.addView(child, params)
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (isInternalView(child)) {
            super.addView(child, index, params)
        } else {
            contentContainer.addView(child, index, params)
        }
    }

    override fun addView(child: View?, width: Int, height: Int) {
        if (isInternalView(child)) {
            super.addView(child, width, height)
        } else {
            contentContainer.addView(child, width, height)
        }
    }

    override fun removeView(view: View?) {
        if (isInternalView(view)) {
            super.removeView(view)
        } else {
            contentContainer.removeView(view)
        }
    }

    override fun removeViewAt(index: Int) {
        contentContainer.removeViewAt(index)
    }

    override fun removeAllViews() {
        contentContainer.removeAllViews()
    }

    private fun isInternalView(view: View?): Boolean {
        return view === contentContainer || view === focusLayer
    }

    // endregion

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unbindFocusManager()
    }
}
