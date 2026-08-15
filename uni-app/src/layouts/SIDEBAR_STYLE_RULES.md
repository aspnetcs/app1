# 侧边栏样式规则（强制使用 CSS 变量）

## 设计令牌（Design Tokens）

所有侧边栏可交互项目 **必须** 使用以下 CSS 变量，定义在 `.app-shell` 中：

```css
--sidebar-item-h: 40px;        /* 项目高度 */
--sidebar-item-gap: 8px;       /* 项目间距 */
--sidebar-item-pl: 8px;        /* 左内边距（控制图标居中位置） */
--sidebar-item-radius: 14px;   /* 圆角 */
--sidebar-icon-size: 20px;     /* 图标尺寸 */
--sidebar-font-size: 14px;     /* 字体大小 */
--sidebar-text-color: #334155; /* 文字颜色 */
--sidebar-icon-color: #4b5563; /* 图标颜色 */
```

## 禁止

- **禁止** 在侧边栏项目中写硬编码数值（如 `height: 40px`）
- **禁止** 在 collapsed 状态覆盖间距为不同值
- **禁止** 为不同项目（new-chat / entry-link / footer）写不同的尺寸

## 新增侧边栏项目模板

### HTML 结构
```html
<view class="sidebar-entry-link" @tap="handleTap">
  <svg class="sidebar-entry-icon" width="20" height="20" ...>
    <!-- icon content -->
  </svg>
  <text class="sidebar-entry-link-label">项目名称</text>
</view>
```

### CSS（已有共享样式，无需额外写）
新增项目放入 `.sidebar-entry-links` 容器即可，自动继承：
- 高度 = `var(--sidebar-item-h)`
- 间距 = `var(--sidebar-item-gap)`
- 左边距 = `var(--sidebar-item-pl)`
- 圆角 = `var(--sidebar-item-radius)`
- 图标 = `var(--sidebar-icon-size)`
- 字体 = `var(--sidebar-font-size)` + `var(--sidebar-text-color)`
- hover = `rgba(0,0,0,0.06)`
- 收起时图标居中 = 由 `overflow: hidden` + 固定 padding 自动处理

### SVG 图标规范
```
width="20" height="20"
viewBox="0 0 24 24"
fill="none"
stroke="#4b5563"       ← 使用 --sidebar-icon-color 对应色值
stroke-width="2"
stroke-linecap="round"
stroke-linejoin="round"
```

## 修改尺寸

只需改 `.app-shell` 中的变量值，**所有项目自动同步**：
- 想调大图标？改 `--sidebar-icon-size`
- 想加大间距？改 `--sidebar-item-gap`
- 想换字号？改 `--sidebar-font-size`
