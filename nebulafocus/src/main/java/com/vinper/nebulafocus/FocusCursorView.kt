package com.vinper.nebulafocus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * 焦点指示器视图
 *
 * 核心特性：
 * - 通过 LayoutParams 改变尺寸（支持 9-Patch，严禁使用 Scale）
 * - 使用 translationX/Y 控制位置
 * - 支持 Spring 物理动画
 * - 支持边缘裁剪
 *
 * @author Vinper
 */
class FocusCursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // region 配置参数

    /** 内边距配置（正值外扩，负值内缩） */
    var cursorPaddingLeft: Int = 0
    var cursorPaddingTop: Int = 0
    var cursorPaddingRight: Int = 0
    var cursorPaddingBottom: Int = 0

    /** Spring 动画配置 */
    var springStiffness: Float = SpringForce.STIFFNESS_MEDIUM
        set(value) {
            field = value
            updateSpringConfig()
        }

    var springDampingRatio: Float = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        set(value) {
            field = value
            updateSpringConfig()
        }

    /** 尺寸动画时长 (ms) */
    var sizeAnimDuration: Long = 200L

    // endregion

    // region 动画引擎

    private val springX: SpringAnimation by lazy {
        SpringAnimation(this, SpringAnimation.TRANSLATION_X).apply {
            spring = createSpringForce()
        }
    }

    private val springY: SpringAnimation by lazy {
        SpringAnimation(this, SpringAnimation.TRANSLATION_Y).apply {
            spring = createSpringForce()
        }
    }

    private var sizeAnimatorWidth: android.animation.ValueAnimator? = null
    private var sizeAnimatorHeight: android.animation.ValueAnimator? = null

    private fun createSpringForce(): SpringForce {
        return SpringForce().apply {
            stiffness = springStiffness
            dampingRatio = springDampingRatio
        }
    }

    private fun updateSpringConfig() {
        springX.spring = createSpringForce()
        springY.spring = createSpringForce()
    }

    // endregion

    // region 状态管理

    /** 当前目标位置 */
    private var targetX: Float = 0f
    private var targetY: Float = 0f

    /** 当前目标尺寸 */
    private var targetWidth: Int = 0
    private var targetHeight: Int = 0

    /** 是否已完成首次初始化（冷启动不执行动画） */
    private var isInitialized: Boolean = false

    /** 硬跟随模式（滚动时禁用动画） */
    var isHardFollowMode: Boolean = false
        set(value) {
            field = value
            if (value) {
                cancelAllAnimations()
            }
        }

    // endregion

    init {
        // 默认不可见，等待首次焦点设置
        visibility = INVISIBLE

        // 解析自定义属性
        context.obtainStyledAttributes(attrs, R.styleable.FocusCursorView).apply {
            try {
                cursorPaddingLeft = getDimensionPixelSize(
                    R.styleable.FocusCursorView_cursorPaddingLeft, 0
                )
                cursorPaddingTop = getDimensionPixelSize(
                    R.styleable.FocusCursorView_cursorPaddingTop, 0
                )
                cursorPaddingRight = getDimensionPixelSize(
                    R.styleable.FocusCursorView_cursorPaddingRight, 0
                )
                cursorPaddingBottom = getDimensionPixelSize(
                    R.styleable.FocusCursorView_cursorPaddingBottom, 0
                )

                springStiffness = getFloat(
                    R.styleable.FocusCursorView_cursorSpringStiffness,
                    SpringForce.STIFFNESS_MEDIUM
                )
                springDampingRatio = getFloat(
                    R.styleable.FocusCursorView_cursorSpringDampingRatio,
                    SpringForce.DAMPING_RATIO_LOW_BOUNCY
                )
                sizeAnimDuration = getInteger(
                    R.styleable.FocusCursorView_cursorSizeAnimDuration,
                    200
                ).toLong()
            } finally {
                recycle()
            }
        }
    }

    /**
     * 设置统一的 Padding
     */
    fun setCursorPadding(padding: Int) {
        cursorPaddingLeft = padding
        cursorPaddingTop = padding
        cursorPaddingRight = padding
        cursorPaddingBottom = padding
    }

    /**
     * 设置四边 Padding
     */
    fun setCursorPadding(left: Int, top: Int, right: Int, bottom: Int) {
        cursorPaddingLeft = left
        cursorPaddingTop = top
        cursorPaddingRight = right
        cursorPaddingBottom = bottom
    }

    /**
     * 移动到目标位置和尺寸
     *
     * @param x 目标 X 坐标（相对于 focusLayer）
     * @param y 目标 Y 坐标
     * @param width 目标宽度
     * @param height 目标高度
     * @param animate 是否使用动画
     */
    fun moveTo(x: Float, y: Float, width: Int, height: Int, animate: Boolean = true) {
        // 应用 Padding
        val finalX = x - cursorPaddingLeft
        val finalY = y - cursorPaddingTop
        val finalWidth = width + cursorPaddingLeft + cursorPaddingRight
        val finalHeight = height + cursorPaddingTop + cursorPaddingBottom

        android.util.Log.d("FocusCursorView", "moveTo: pos=($finalX, $finalY), size=${finalWidth}x$finalHeight, " +
                "animate=$animate, initialized=$isInitialized, visibility=$visibility")

        targetX = finalX
        targetY = finalY
        targetWidth = finalWidth
        targetHeight = finalHeight

        // 首次初始化或硬跟随模式：直接瞬移
        if (!isInitialized || isHardFollowMode || !animate) {
            cancelAllAnimations()
            applyPositionImmediately(finalX, finalY)
            applySizeImmediately(finalWidth, finalHeight)
            isInitialized = true
            visibility = VISIBLE
            android.util.Log.d("FocusCursorView", "Cursor now VISIBLE at ($finalX, $finalY)")
            return
        }

        // 使用 Spring 动画移动位置
        animatePosition(finalX, finalY)

        // 使用 ValueAnimator 改变尺寸
        animateSize(finalWidth, finalHeight)

        visibility = VISIBLE
    }

    /**
     * 硬跟随模式：仅更新位置（用于滚动时同步）
     */
    fun hardFollowTo(x: Float, y: Float) {
        val finalX = x - cursorPaddingLeft
        val finalY = y - cursorPaddingTop
        applyPositionImmediately(finalX, finalY)
    }

    /**
     * 取消所有进行中的动画
     */
    fun cancelAllAnimations() {
        springX.cancel()
        springY.cancel()
        sizeAnimatorWidth?.cancel()
        sizeAnimatorHeight?.cancel()
    }

    /**
     * 重置状态（用于页面切换等场景）
     */
    fun reset() {
        cancelAllAnimations()
        isInitialized = false
        visibility = INVISIBLE
        clipBounds = null
    }

    // region 动画实现

    private fun animatePosition(toX: Float, toY: Float) {
        springX.animateToFinalPosition(toX)
        springY.animateToFinalPosition(toY)
    }

    private fun animateSize(toWidth: Int, toHeight: Int) {
        val currentWidth = layoutParams?.width ?: toWidth
        val currentHeight = layoutParams?.height ?: toHeight

        if (currentWidth != toWidth) {
            sizeAnimatorWidth?.cancel()
            sizeAnimatorWidth = android.animation.ValueAnimator.ofInt(currentWidth, toWidth).apply {
                duration = sizeAnimDuration
                addUpdateListener { anim ->
                    updateWidth(anim.animatedValue as Int)
                }
                start()
            }
        }

        if (currentHeight != toHeight) {
            sizeAnimatorHeight?.cancel()
            sizeAnimatorHeight = android.animation.ValueAnimator.ofInt(currentHeight, toHeight).apply {
                duration = sizeAnimDuration
                addUpdateListener { anim ->
                    updateHeight(anim.animatedValue as Int)
                }
                start()
            }
        }
    }

    private fun applyPositionImmediately(x: Float, y: Float) {
        translationX = x
        translationY = y
    }

    private fun applySizeImmediately(width: Int, height: Int) {
        updateLayoutParams(width, height)
    }

    private fun updateWidth(width: Int) {
        layoutParams?.let {
            if (it.width != width) {
                it.width = width
                requestLayout()
            }
        }
    }

    private fun updateHeight(height: Int) {
        layoutParams?.let {
            if (it.height != height) {
                it.height = height
                requestLayout()
            }
        }
    }

    private fun updateLayoutParams(width: Int, height: Int) {
        val lp = layoutParams
        if (lp == null) {
            layoutParams = ViewGroup.LayoutParams(width, height)
            requestLayout()
            android.util.Log.d("FocusCursorView", "Created new LayoutParams: ${width}x${height}")
        } else {
            if (lp.width != width || lp.height != height) {
                lp.width = width
                lp.height = height
                layoutParams = lp
                
                // 强制触发父容器重新测量和布局
                requestLayout()
                parent?.requestLayout()
                
                // 立即执行测量和布局
                post {
                    measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                    )
                    layout(left, top, left + width, top + height)
                    android.util.Log.d("FocusCursorView", "Force measured and laid out: ${this.width}x${this.height}")
                }
                
                android.util.Log.d("FocusCursorView", "Updated LayoutParams: ${width}x${height}, requestLayout called")
            }
        }
    }

    // endregion

    // region 裁剪支持

    private val tempClipRect = Rect()

    /**
     * 应用裁剪边界（当目标 View 被父容器部分遮挡时）
     *
     * @param clipRect 相对于指示器自身的裁剪矩形，null 表示取消裁剪
     */
    fun applyClipBounds(clipRect: Rect?) {
        clipBounds = clipRect
    }

    /**
     * 根据目标 View 的可见区域计算并应用裁剪
     *
     * @param targetVisibleRect 目标 View 的可见区域（相对于目标 View）
     * @param targetWidth 目标 View 的完整宽度
     * @param targetHeight 目标 View 的完整高度
     */
    fun calculateAndApplyClip(
        targetVisibleRect: Rect,
        targetWidth: Int,
        targetHeight: Int
    ) {
        // 如果完全可见，取消裁剪
        if (targetVisibleRect.left <= 0 &&
            targetVisibleRect.top <= 0 &&
            targetVisibleRect.right >= targetWidth &&
            targetVisibleRect.bottom >= targetHeight
        ) {
            clipBounds = null
            return
        }

        // 计算裁剪区域（需要考虑 Padding 偏移）
        tempClipRect.set(
            targetVisibleRect.left + cursorPaddingLeft,
            targetVisibleRect.top + cursorPaddingTop,
            targetVisibleRect.right + cursorPaddingLeft,
            targetVisibleRect.bottom + cursorPaddingTop
        )
        clipBounds = tempClipRect
    }

    // endregion

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAllAnimations()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 9-Patch 背景由系统自动绘制
    }
}
