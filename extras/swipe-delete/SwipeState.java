package com.jizhang.assistant.ui;

/**
 * 横滑删除的手势状态机。
 *
 * 刻意写成纯 Java（不 import 任何 android.*），这样能在电脑上直接跑测试 ——
 * 触摸交互是最容易出 bug 又最难在真机上排查的部分。
 *
 * 规则：
 *   · 纵向为主的移动一律不拦截，交给 ListView 滚动
 *   · 横向移动超过阈值才进入拖动，跟手位移并限制在 [−actionWidth, 0]
 *   · 抬手时位移超过一半则展开，否则收起
 *   · 已展开时：点内容区 = 收起；点露出的操作区 = 放行给删除按钮（不能拦！）
 */
public final class SwipeState {

    public static final int ACT_DOWN = 0;
    public static final int ACT_MOVE = 1;
    public static final int ACT_UP = 2;
    public static final int ACT_CANCEL = 3;

    private final int touchSlop;

    private float downX, downY, startOffset;
    private boolean dragging, tapToClose, disallowParent;
    private float offset;
    private int actionWidth;

    public SwipeState(int touchSlop) {
        this.touchSlop = touchSlop;
    }

    public void setActionWidth(int w) {
        this.actionWidth = w;
    }

    public int getActionWidth() {
        return actionWidth;
    }

    public float getOffset() {
        return offset;
    }

    public void setOffset(float v) {
        this.offset = clamp(v);
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isTapToClose() {
        return tapToClose;
    }

    public boolean isOpen() {
        return offset < 0;
    }

    /** 取出「是否要请求父容器放行」并复位，调用方据此调用 requestDisallowInterceptTouchEvent */
    public boolean consumeDisallowParent() {
        boolean v = disallowParent;
        disallowParent = false;
        return v;
    }

    /**
     * @return 是否需要拦截该事件
     */
    public boolean intercept(int action, float x, float y, int width) {
        switch (action) {
            case ACT_DOWN: {
                downX = x;
                downY = y;
                startOffset = offset;
                dragging = false;
                // 手指落在露出的操作区上时必须放行，否则删除按钮永远点不到
                boolean onActionArea = startOffset != 0 && x >= width - actionWidth;
                tapToClose = startOffset != 0 && !onActionArea;
                return tapToClose;
            }
            case ACT_MOVE: {
                if (dragging) return true;
                float dx = x - downX;
                float dy = y - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    tapToClose = false;
                    disallowParent = true;
                    return true;
                }
                return false;
            }
            case ACT_UP:
            case ACT_CANCEL:
                dragging = false;
                return false;
            default:
                return false;
        }
    }

    /**
     * @return 是否消费该事件
     */
    public boolean touch(int action, float x, float y) {
        switch (action) {
            case ACT_DOWN:
                return tapToClose || dragging;

            case ACT_MOVE:
                if (tapToClose) return true;     // 轻点后微动：仍按点击处理
                if (!dragging) return false;
                offset = clamp(startOffset + (x - downX));
                return true;

            case ACT_UP:
            case ACT_CANCEL:
                if (dragging) {
                    dragging = false;
                    offset = (offset < -actionWidth / 2f) ? -actionWidth : 0;
                    return true;
                }
                if (tapToClose) {
                    tapToClose = false;
                    offset = 0;
                    return true;
                }
                return false;

            default:
                return false;
        }
    }

    public void reset() {
        offset = 0;
        dragging = false;
        tapToClose = false;
        disallowParent = false;
    }

    private float clamp(float v) {
        if (v > 0) return 0;
        if (v < -actionWidth) return -actionWidth;
        return v;
    }
}
