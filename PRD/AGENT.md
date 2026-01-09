# Role
Android 高级系统架构师 & UI 工程师 (TV 领域专家)

# Task
开发一个高性能、解耦的 Android TV 焦点指示器组件 `NebulaFocus`。该组件需支持物理弹簧动画、9-Patch 资源变形处理，并完美适配 RecyclerView 的锚点滚动交互。

# 核心技术要求 (Technical Constraints)

## 1. 架构模式：隔离渲染层 (Isolated Layer)
*   **Root Layout**: 创建 `FocusIndicatorLayout` (继承 FrameLayout)。
*   **Z-Order**: 包含两个子容器：
    1.  `contentContainer`: 放置业务 App UI。
    2.  `focusLayer`: 透明覆盖层 (`clipChildren=false`)，**仅**放置指示器 View。
*   **性能关键**: 指示器 View (`cursorView`) 必须改变 `LayoutParams` 来调整大小。将其隔离在 `focusLayer` 中是为了确保 Resizing 只触发轻量级重排，**绝对不能**触发底层复杂业务布局的重排。

## 2. 指示器视图 (Cursor Logic)
*   **尺寸控制**: **严禁**使用 `scaleX/scaleY`。必须通过 `layoutParams.width/height` 改变大小。
    *   *原因*: 必须支持 `.9.png` (9-Patch) 背景，保证圆角和边框不失真。
*   **位置控制**: 使用 `translationX` 和 `translationY`。
*   **Padding**: 支持 XML/代码配置 Padding (内缩/外扩)。

## 3. 坐标逻辑 (通用映射)
*   不要使用 `getLeft()`。使用 Window 坐标系解耦。
*   **算法**:
    1. `targetView.getLocationInWindow()` 获取目标绝对坐标。
    2. `focusLayer.getLocationInWindow()` 获取容器绝对坐标。
    3. 相减得到相对坐标。

## 4. 动画与物理引擎 (Hybrid Engine)
*   **库**: `androidx.dynamicanimation:dynamicanimation` (SpringAnimation)。
*   **策略**:
    *   Translation: Spring Physics.
    *   Size: ValueAnimator 或 Spring。
*   **关键优化：硬跟随模式 (Hard Follow)**
    *   监听 RecyclerView `OnScrollListener`。
    *   **IF SCROLLING**: 取消所有动画。在 `OnPreDrawListener` 中，**强制**将指示器 Translation 设置为目标的实时计算位置。
    *   **IF IDLE**: 启用 Spring 动画。
    *   *原因*: 消除滚动时的视觉滞后 (Ghosting) 和抖动。

## 5. 滚动策略：锚点支持 (Keyline Support)
*   提供辅助类 `AnchorLinearSmoothScroller` (继承 `LinearSmoothScroller`)。
*   **逻辑**: 重写 `calculateDtToFit`。
*   **功能**: 支持 `keylineOffset` (如屏幕高度 35%)。计算滚动距离时，将目标 Item 对齐到锚点，而非屏幕边缘。

## 6. 边界处理
*   **首次加载**: View 首次 Attach 时，直接瞬移初始化位置 (无动画)。
*   **遮挡裁剪**: 如果目标被父容器裁剪 (`getLocalVisibleRect`)，计算交集并对 `cursorView` 应用 `setClipBounds`。

# 输出产物 (Deliverables)
使用 Kotlin，原生 View System：
1.  **`FocusIndicatorLayout.kt`**: 核心容器。
2.  **`FocusCursorView.kt`**: 绘制与物理状态 View。
3.  **`FocusManager.kt`**: 逻辑控制器 (GlobalFocus, PreDraw, Scroll)。
4.  **`AnchorLinearSmoothScroller.kt`**: 滚动辅助。
5.  **`UsageActivity.kt`**: 使用示例。