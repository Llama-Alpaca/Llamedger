import com.jizhang.assistant.ui.SwipeState;

/** 横滑删除手势的电脑端测试（纯逻辑，不需要手机） */
public class SwipeTest {

    static final int WIDTH = 1000;
    static final int ACTION_W = 200;
    static final int SLOP = 20;

    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        testSwipeLeftOpens();
        testSwipeNotFarEnoughSnapsBack();
        testVerticalScrollNotIntercepted();
        testTapOnContentWhileOpenCloses();
        testTapOnDeleteAreaIsNotIntercepted();
        testDragWhileOpenFollowsFinger();
        testOffsetClamped();
        testDisallowParentFlagOnce();
        testResetClearsState();

        System.out.println();
        System.out.println("=====================================");
        System.out.println("  SwipeTest 通过 " + pass + " 项，失败 " + fail + " 项");
        System.out.println("=====================================");
        if (fail > 0) System.exit(1);
    }

    static SwipeState fresh() {
        SwipeState s = new SwipeState(SLOP);
        s.setActionWidth(ACTION_W);
        return s;
    }

    /** 向左滑足够远 → 展开，露出删除按钮 */
    static void testSwipeLeftOpens() {
        SwipeState s = fresh();
        s.intercept(SwipeState.ACT_DOWN, 800, 500, WIDTH);
        boolean intercepted = s.intercept(SwipeState.ACT_MOVE, 650, 500, WIDTH);
        s.touch(SwipeState.ACT_MOVE, 650, 500);
        s.touch(SwipeState.ACT_UP, 650, 500);
        check("向左滑动足够远-展开露出删除按钮",
                intercepted && s.isOpen() && s.getOffset() == -ACTION_W,
                "intercepted=" + intercepted + " offset=" + s.getOffset());
    }

    /** 滑得太少 → 回弹收起，避免误删 */
    static void testSwipeNotFarEnoughSnapsBack() {
        SwipeState s = fresh();
        s.intercept(SwipeState.ACT_DOWN, 800, 500, WIDTH);
        s.intercept(SwipeState.ACT_MOVE, 750, 500, WIDTH);
        s.touch(SwipeState.ACT_MOVE, 750, 500);
        s.touch(SwipeState.ACT_UP, 750, 500);
        check("滑动距离不足-自动回弹收起",
                !s.isOpen() && s.getOffset() == 0,
                "offset=" + s.getOffset());
    }

    /** 上下滑动必须放行，否则列表没法滚动 */
    static void testVerticalScrollNotIntercepted() {
        SwipeState s = fresh();
        s.intercept(SwipeState.ACT_DOWN, 800, 500, WIDTH);
        boolean intercepted = s.intercept(SwipeState.ACT_MOVE, 790, 300, WIDTH);
        check("纵向滑动-不拦截，交给列表滚动",
                !intercepted && !s.isDragging(),
                "intercepted=" + intercepted + " dragging=" + s.isDragging());
    }

    /** 已展开时点内容区 = 收起 */
    static void testTapOnContentWhileOpenCloses() {
        SwipeState s = fresh();
        s.setOffset(-ACTION_W);
        boolean intercepted = s.intercept(SwipeState.ACT_DOWN, 400, 500, WIDTH);
        s.touch(SwipeState.ACT_DOWN, 400, 500);
        s.touch(SwipeState.ACT_UP, 400, 500);
        check("已展开时点内容区-收起且不触发点击",
                intercepted && !s.isOpen() && s.getOffset() == 0,
                "intercepted=" + intercepted + " offset=" + s.getOffset());
    }

    /**
     * ★ 关键回归：已展开时，手指落在右侧露出的「删除」按钮上必须放行。
     * 之前一版把这一下也当成「点击收起」拦了下来，导致删除按钮永远点不到。
     */
    static void testTapOnDeleteAreaIsNotIntercepted() {
        SwipeState s = fresh();
        s.setOffset(-ACTION_W);
        // 操作区范围是 x ∈ [WIDTH - ACTION_W, WIDTH) = [800, 1000)
        boolean intercepted = s.intercept(SwipeState.ACT_DOWN, 900, 500, WIDTH);
        check("已展开时点删除按钮-必须放行给按钮",
                !intercepted,
                "intercepted=" + intercepted + "（true 就意味着删除按钮点不到）");
    }

    /** 已展开时继续拖动，位移要跟手 */
    static void testDragWhileOpenFollowsFinger() {
        SwipeState s = fresh();
        s.setOffset(-ACTION_W);
        s.intercept(SwipeState.ACT_DOWN, 400, 500, WIDTH);
        s.touch(SwipeState.ACT_DOWN, 400, 500);
        boolean intercepted = s.intercept(SwipeState.ACT_MOVE, 500, 500, WIDTH);
        s.touch(SwipeState.ACT_MOVE, 500, 500);
        float mid = s.getOffset();
        s.touch(SwipeState.ACT_UP, 500, 500);
        check("已展开时拖动-跟手位移",
                intercepted && mid == -100 && s.getOffset() == 0,
                "intercepted=" + intercepted + " 拖动中=" + mid
                        + " 抬手后=" + s.getOffset());
    }

    /** 位移必须被限制在 [-actionWidth, 0]，不能滑过头 */
    static void testOffsetClamped() {
        SwipeState s = fresh();
        s.setOffset(-9999);
        float lo = s.getOffset();
        s.setOffset(9999);
        float hi = s.getOffset();
        check("位移被限制在合法范围", lo == -ACTION_W && hi == 0,
                "左侧=" + lo + " 右侧=" + hi);
    }

    /** 拖动开始时要请求父容器放行，且只请求一次 */
    static void testDisallowParentFlagOnce() {
        SwipeState s = fresh();
        s.intercept(SwipeState.ACT_DOWN, 800, 500, WIDTH);
        boolean before = s.consumeDisallowParent();
        s.intercept(SwipeState.ACT_MOVE, 650, 500, WIDTH);
        boolean first = s.consumeDisallowParent();
        boolean second = s.consumeDisallowParent();
        check("拖动开始时请求父容器放行-且只取一次",
                !before && first && !second,
                "before=" + before + " first=" + first + " second=" + second);
    }

    static void testResetClearsState() {
        SwipeState s = fresh();
        s.setOffset(-ACTION_W);
        s.intercept(SwipeState.ACT_DOWN, 400, 500, WIDTH);
        s.reset();
        check("复用复位-位移与状态清空",
                s.getOffset() == 0 && !s.isDragging() && !s.isTapToClose()
                        && !s.isOpen(),
                "offset=" + s.getOffset() + " dragging=" + s.isDragging()
                        + " tapToClose=" + s.isTapToClose());
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  [PASS] " + name);
        } else {
            fail++;
            System.out.println("  [FAIL] " + name);
            System.out.println("         " + detail);
        }
    }
}
