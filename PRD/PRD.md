# Android TV 灵动焦点组件 (NebulaFocus) - 最终交付文档

## 第一部分：需求规格说明书 (PRD)

### 1. 项目背景

解决当前 Android TV 应用中焦点交互生硬、缺乏连贯感的问题。目标是构建一套**高性能、可扩展、视觉流畅**的全局焦点指示器组件，对标 Apple TV 和 Android Leanback 的高品质交互体验。

### 2. 核心交互需求

#### 2.1 视觉与形态

- 
- **独立浮层**：指示器不属于业务 View 的一部分，而是悬浮在窗口最上层的独立层级。
- **9-Patch 支持**：必须完美支持 .9.png 资源。
  - 
  - **关键约束**：指示器变形时**严禁使用缩放 (Scale)**，必须改变实际宽高 (Width/Height)，以保证圆角、边框、阴影不拉伸失真。
- **尺寸自适应**：指示器尺寸 = 目标 View 尺寸 + Padding (支持内缩/外扩)。

#### 2.2 运动物理学 (Physics Motion)

- 
- **弹簧阻尼**：焦点移动 (TranslationX/Y) 和尺寸变化均采用 **Spring Animation**，模拟真实物理惯性。
- **连贯性 (Retargeting)**：在动画未结束时触发新的焦点变化，必须基于当前位置平滑变轨，杜绝瞬移或位置重置。
- **冷启动策略**：页面初始化或从后台返回时，指示器**不执行动画**，直接瞬移至当前焦点位置（避免从 (0,0) 飞入的视觉 Bug）。

#### 2.3 滚动交互模型 (Keyline System)

- 
- 采用 **“锚点对齐 (Keyline Alignment)”** 策略，摒弃原生的“贴边滚动”。
  - 
  - **头部自由区**：列表顶部，焦点自由下移。
  - **锚点锁定区**：当焦点试图越过屏幕特定比例（如 35%~50%）时，焦点位置保持相对静止，列表内容向上滚动填充，保持视线处于屏幕舒适区。
  - **尾部自由区**：列表底部，焦点自由下移。

#### 2.4 性能与边界

- 
- **硬跟随模式 (Hard Follow)**：当列表处于滚动状态 (Scrolling/Flinging) 时，指示器必须**禁用物理动画**，在每一帧 (OnPreDraw) 强制同步坐标。确保指示器像“贴纸”一样死死粘在 Item 上，无抖动、无滞后。
- **边缘裁剪**：当 Item 被移出可视区域（如只显示一半）时，指示器必须通过 clipBounds 自动裁剪，呈现“随 Item 滑出屏幕”的效果。



## 第二部分：技术架构方案

### 1. 架构模式：隔离渲染层 (Isolated Layer Pattern)

为了解决“改变 View 尺寸导致全屏重排 (Global Layout)”的性能瓶颈，采用以下层级：

codeXml



```
<FocusIndicatorLayout> (Root, extends FrameLayout)
    
    <!-- 业务层：复杂的布局结构 -->
    <ConstraintLayout android:id="@+id/content_container">
        <RecyclerView ... />
    </ConstraintLayout>
    
    <!-- 焦点层：透明、全屏、clipChildren=false -->
    <FrameLayout 
        android:id="@+id/focus_layer"
        android:layout_width="match_parent" 
        android:layout_height="match_parent">
        
        <!-- 唯一的可移动指示器 -->
        <View android:id="@+id/cursor_view" />
        
    </FrameLayout>
</FocusIndicatorLayout>
```

- 
- **原理**：cursor_view 的 Layout 请求会被 focus_layer 拦截，只会导致这个轻量级 FrameLayout 的重排，不会波及底层的业务 UI，确保低端设备 60fps。

### 2. 坐标系统：通用映射 (Universal Mapping)

摒弃 View 层级依赖，采用 **Window 绝对坐标系**：

```latex
P(cursor) = P(target_window) − P(focus_layer_window) − Padding   
```



这使得组件可以支持 RecyclerView、NestedScrollView、ConstraintLayout 中的任意 View，实现全页面覆盖。



### 3. 滚动适配器

提供 AnchorLinearSmoothScroller，重写 calculateDtToFit，引入 alignmentOffset 参数，实现基于 Keyline 的滚动偏移计算。



## 第三部分：给 AI 编程助手 (Coding Agent) 的 Prompt

**请复制以下内容发送给 Cursor / WindSurf：**

```markdown
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
```

## 第四部分：交互验证 Demo (HTML/JS)

保存为`demo.html` 并在浏览器打开。支持配置 Item 数量和锚点偏移量，完美演示**策略  (Keyline 锚点模式)**。

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>NebulaFocus 最终交互演示</title>
    <style>
        :root { --accent: #00E5FF; --bg: #1a1a1a; --panel: #2d2d2d; }
        body { background: var(--bg); color: #eee; font-family: 'Segoe UI', sans-serif; display: flex; gap: 40px; justify-content: center; padding: 40px; height: 100vh; box-sizing: border-box; }
        
        /* 电视屏幕容器 */
        .device-wrapper { position: relative; }
        .tv-screen {
            width: 280px; height: 420px;
            background: #000; border: 12px solid #444; border-radius: 16px;
            position: relative; overflow: hidden;
            box-shadow: 0 30px 80px rgba(0,0,0,0.6);
        }

        /* 锚点辅助线 */
        .keyline-guide {
            position: absolute; left: 0; width: 100%; height: 0;
            border-top: 2px dashed rgba(255, 50, 50, 0.8);
            z-index: 20; pointer-events: none;
            transition: top 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
        }
        .keyline-tag {
            position: absolute; right: 0; top: -22px;
            font-size: 11px; color: #ff5555; background: rgba(0,0,0,0.8); padding: 2px 6px; border-radius: 4px;
        }

        /* 列表层 */
        .rv-container {
            position: absolute; top: 0; left: 0; width: 100%;
            /* 模拟 Android Scroller 的平滑滚动 */
            transition: transform 0.5s cubic-bezier(0.2, 0.8, 0.2, 1);
        }
        .item {
            height: 70px; margin: 0 12px;
            display: flex; align-items: center; justify-content: center;
            border-bottom: 1px solid #333; color: #666; font-size: 15px; font-weight: 500;
            box-sizing: border-box;
        }

        /* 焦点层 (独立层级) */
        .focus-cursor {
            position: absolute; left: 12px; width: 256px; height: 70px;
            border: 4px solid var(--accent); border-radius: 10px;
            background: rgba(0, 229, 255, 0.1);
            box-shadow: 0 0 25px rgba(0, 229, 255, 0.25);
            pointer-events: none; z-index: 10;
            /* 模拟 Spring 动画 */
            transition: transform 0.45s cubic-bezier(0.34, 1.56, 0.64, 1);
        }

        /* 控制面板 */
        .config-panel { width: 320px; background: var(--panel); padding: 25px; border-radius: 16px; height: fit-content; box-shadow: 0 10px 30px rgba(0,0,0,0.3); }
        h3 { margin-top: 0; color: var(--accent); border-bottom: 1px solid #444; padding-bottom: 10px;}
        .control-group { margin-bottom: 20px; }
        label { display: block; font-size: 13px; margin-bottom: 8px; color: #aaa; font-weight: bold;}
        input[type="range"] { width: 100%; cursor: pointer; }
        .val-display { float: right; font-weight: bold; color: var(--accent); }
        
        .btn-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 25px; }
        button {
            padding: 14px; border: none; border-radius: 8px; cursor: pointer;
            background: #444; color: white; font-weight: bold; font-size: 14px; transition: 0.2s;
        }
        button.primary { background: var(--accent); color: #000; }
        button:active { transform: scale(0.96); }
        button:hover { filter: brightness(1.1); }

        .status-log { margin-top: 20px; font-family: 'Consolas', monospace; font-size: 12px; color: #888; border-top: 1px solid #444; padding-top: 15px; line-height: 1.6;}
        .tag-scroll { color: #ffab00; }
        .tag-head { color: #00e676; }
        .tag-tail { color: #b388ff; }
    </style>
</head>
<body>

    <div class="device-wrapper">
        <div class="tv-screen">
            <div class="keyline-guide" id="guideLine">
                <div class="keyline-tag">KEYLINE (锚点)</div>
            </div>
            <div class="rv-container" id="rvList"></div>
            <div class="focus-cursor" id="cursor"></div>
        </div>
    </div>

    <div class="config-panel">
        <h3>NebulaFocus 模拟器</h3>
        
        <div class="control-group">
            <label>Item 总数 <span class="val-display" id="valTotal">20</span></label>
            <input type="range" min="6" max="50" value="20" id="inTotal">
        </div>

        <div class="control-group">
            <label>锚点位置 (屏幕比例) <span class="val-display" id="valOffset">35%</span></label>
            <input type="range" min="0" max="90" value="35" id="inOffset">
            <div style="font-size:11px; color:#666; margin-top:6px;">
                0%=贴顶 (方案1/2) | 35%=黄金位 | 50%=居中
            </div>
        </div>

        <div class="control-group">
            <label>当前焦点 Index: <span class="val-display" id="valIndex">0</span></label>
            <!-- 进度条可视化 -->
            <div style="width:100%; height:4px; background:#444; margin-top:5px; border-radius:2px; overflow:hidden;">
                <div id="progressIndex" style="width:0%; height:100%; background:var(--accent); transition: width 0.3s;"></div>
            </div>
        </div>

        <div class="btn-grid">
            <button onclick="move(-1)">▲ 上移</button>
            <button class="primary" onclick="move(1)">▼ 下移</button>
            <button onclick="reset()" style="grid-column: span 2; background:#333;">⟲ 重置状态</button>
        </div>

        <div class="status-log" id="log">System Ready.</div>
    </div>

<script>
    // === 常量配置 ===
    const ITEM_HEIGHT = 70;
    const SCREEN_HEIGHT = 420;
    
    // === 状态机 ===
    let state = {
        totalItems: 20,
        keylinePercent: 0.35, // 0.0 ~ 1.0
        focusIndex: 0,
        scrollInfo: 0 
    };

    const ui = {
        list: document.getElementById('rvList'),
        cursor: document.getElementById('cursor'),
        guide: document.getElementById('guideLine'),
        log: document.getElementById('log'),
        inTotal: document.getElementById('inTotal'),
        inOffset: document.getElementById('inOffset')
    };

    function init() {
        renderList();
        updateKeylineVisual();
        updateSystem();
        
        ui.inTotal.oninput = (e) => { 
            state.totalItems = parseInt(e.target.value); 
            document.getElementById('valTotal').innerText = state.totalItems;
            renderList(); reset();
        };
        ui.inOffset.oninput = (e) => {
            state.keylinePercent = parseInt(e.target.value) / 100;
            document.getElementById('valOffset').innerText = e.target.value + "%";
            updateKeylineVisual(); updateSystem();
        };
        
        // 键盘支持
        document.addEventListener('keydown', (e) => {
            if(e.key === "ArrowDown") move(1);
            if(e.key === "ArrowUp") move(-1);
        });
    }

    function renderList() {
        let html = '';
        for(let i=0; i<state.totalItems; i++) {
            html += `<div class="item" style="background:${i%2===0?'rgba(255,255,255,0.02)':'transparent'}">Content Item ${i}</div>`;
        }
        ui.list.innerHTML = html;
        ui.list.style.height = (state.totalItems * ITEM_HEIGHT) + "px";
    }

    function updateKeylineVisual() {
        const px = SCREEN_HEIGHT * state.keylinePercent;
        ui.guide.style.top = px + "px";
    }

    function reset() {
        state.focusIndex = 0;
        state.scrollInfo = 0;
        updateSystem();
    }

    function move(dir) {
        const next = state.focusIndex + dir;
        if(next < 0 || next >= state.totalItems) return;
        state.focusIndex = next;
        updateSystem();
    }

    // === 核心：锚点滚动算法 (The Anchor Strategy) ===
    function updateSystem() {
        // 1. 获取目标 Item 在整个列表中的绝对 Y 坐标
        const targetItemY = state.focusIndex * ITEM_HEIGHT;
        
        // 2. 获取屏幕上的锚点 Y 坐标
        const keylineScreenY = SCREEN_HEIGHT * state.keylinePercent;

        // 3. 计算为了对齐锚点，列表需要滚动的理想距离
        // 逻辑：Scroll = ItemAbsY - AnchorScreenY
        let idealScroll = targetItemY - keylineScreenY;

        // 4. 边界修正 (Clamping)
        // 列表最大滚动距离 = 内容总高 - 屏幕高度
        const maxScroll = (state.totalItems * ITEM_HEIGHT) - SCREEN_HEIGHT;
        
        // 实际滚动值必须在 [0, maxScroll] 之间
        let finalScroll = Math.max(0, Math.min(idealScroll, maxScroll));
        state.scrollInfo = finalScroll;

        // 5. 计算指示器在屏幕上的最终位置
        // CursorY = ItemAbsY - Scroll
        let cursorScreenY = targetItemY - finalScroll;

        // === 渲染 ===
        ui.list.style.transform = `translateY(-${finalScroll}px)`;
        ui.cursor.style.transform = `translateY(${cursorScreenY}px)`;
        
        // 更新 UI 状态
        document.getElementById('valIndex').innerText = state.focusIndex;
        document.getElementById('progressIndex').style.width = ((state.focusIndex / (state.totalItems-1))*100) + "%";
        
        let statusText = "";
        let statusClass = "";
        if (finalScroll <= 0) { statusText = "HEAD (自由移动)"; statusClass = "tag-head"; }
        else if (finalScroll >= maxScroll) { statusText = "TAIL (自由移动)"; statusClass = "tag-tail"; }
        else { statusText = "ANCHORED (锚点锁定)"; statusClass = "tag-scroll"; }
        
        ui.log.innerHTML = `
        Strategy: <span class="${statusClass}">${statusText}</span><br>
        Target Abs Y: ${targetItemY}px<br>
        Scroll Offset: ${parseInt(finalScroll)}px<br>
        Cursor Screen Y: ${parseInt(cursorScreenY)}px`;
    }

    init();
</script>
</body>
</html>
```