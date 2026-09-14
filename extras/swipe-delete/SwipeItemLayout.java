package com.jizhang.assistant.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

/**
 * 可横向滑动的列表项：向左滑动，右侧露出「删除」按钮。
 *
 * 结构（顺序很重要）：
 *   子 View 0 = 右侧操作区（在底层，平时被内容盖住）
 *   子 View 1 = 内容区（在上层，滑动时向左平移露出操作区）
 *
 * 与 ListView 的配合：
 *   只有判定为「横向滑动」时才拦截事件，纵向滚动和普通点击/长按都交还给 ListView，
 *   所以滑动删除不会把原来的点按编辑、长按菜单弄坏。
 */
public class SwipeItemLayout extends ViewGroup {

    /** 同一时刻只允许一项处于展开状态 */
    private static SwipeItemLayout opened;

    private View actions;    // 底层：删除按钮
    private View content;    // 上层：流水内容

    private int actionWidth;
    private SwipeState state;

    public SwipeItemLayout(Context c) {
        super(c);
        init(c);
    }

    public SwipeItemLayout(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    private void init(Context c) {
        // 手势判定逻辑全部放在 SwipeState 里，便于电脑端测试
        state = new SwipeState(ViewConfiguration.get(c).getScaledTouchSlop());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() >= 2) {
            actions = getChildAt(0);
            content = getChildAt(1);
        }
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);

        if (content != null) {
            content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
        int h = content == null ? 0 : content.getMeasuredHeight();

        if (actions != null) {
            actions.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            actionWidth = actions.getMeasuredWidth();
            state.setActionWidth(actionWidth);
        }
        setMeasuredDimension(width, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = getWidth();
        int h = getHeight();
        if (actions != null) {
            actions.layout(w - actionWidth, 0, w, h);
        }
        if (content != null) {
            content.layout(0, 0, w, h);
            content.setTranslationX(state.getOffset());
        }
    }

    private void apply() {
        if (content != null) content.setTranslationX(state.getOffset());
    }

    public boolean isOpen() {
        return state.isOpen();
    }

    public void open() {
        if (opened != null && opened != this) opened.closeInternal();
        state.setOffset(-actionWidth);
        apply();
        opened = this;
    }

    public void close() {
        closeInternal();
        if (opened == this) opened = null;
    }

    private void closeInternal() {
        state.setOffset(0);
        apply();
    }

    /** 列表复用条目时必须复位，否则上一行滑开的状态会带到下一行 */
    public void resetOffset() {
        state.reset();
        apply();
        if (opened == this) opened = null;
    }

    /** 当前是否有条目处于展开状态 */
    public static boolean isAnyOpen() {
        return opened != null;
    }

    /** 供外部（如列表滚动、删除后）统一收起 */
    public static void closeOpened() {
        if (opened != null) {
            opened.closeInternal();
            opened = null;
        }
    }

    // ------------------------------------------------------------ 触摸

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = state.intercept(action(ev), ev.getX(), ev.getY(), getWidth());
        if (state.consumeDisallowParent() && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int a = action(ev);
        boolean consumed = state.touch(a, ev.getX(), ev.getY());
        apply();
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && state.isOpen()) {
            opened = this;
        } else if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                && !state.isOpen() && opened == this) {
            opened = null;
        }
        return consumed;
    }

    private static int action(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: return SwipeState.ACT_DOWN;
            case MotionEvent.ACTION_MOVE: return SwipeState.ACT_MOVE;
            case MotionEvent.ACTION_UP: return SwipeState.ACT_UP;
            case MotionEvent.ACTION_CANCEL: return SwipeState.ACT_CANCEL;
            default: return -1;
        }
    }

    /** 小工具：避免把这个类的 import 写得到处都是 */
    private static final class ViewParentCompat {
        static void disallow(android.view.ViewParent p) {
            if (p != null) p.requestDisallowInterceptTouchEvent(true);
        }

        static void allow(android.view.ViewParent p) {
            if (p != null) p.requestDisallowInterceptTouchEvent(false);
        }
    }
}
