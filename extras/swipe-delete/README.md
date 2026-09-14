# 横滑删除（已停用，仅存档）

这套代码在 v1.2.0 ~ v1.2.1 用过，从 **v1.2.2 起停用**（用户反馈手感有问题，暂时不需要）。

代码保留在这里，不参与编译。想重新启用时：

1. 把 `SwipeItemLayout.java`、`SwipeState.java` 移回 `app/java/com/jizhang/assistant/ui/`
2. 把 `res/layout/item_txn_swipe.xml` 移回 `app/res/layout/`
3. 把 `tests/SwipeTest.java` 移回 `tests/`
4. `run_tests.py` 里把 `EXTRA_SOURCES` 指向 `SwipeState.java`，`TEST_CLASSES` 加上 `"SwipeTest"`
5. `TxnAdapter` 恢复横滑模式（构造参数 `swipeable` + `OnDeleteListener`）
6. `TxnListActivity` 用 `new TxnAdapter(this, true)`，并接上 `setOnDeleteListener`
7. `TxnActions.confirmDelete` 里加回 `SwipeItemLayout.closeOpened()`
8. `tools/check_resources.py` 恢复对应的交互守卫项

## 设计要点（当时踩过的坑，重启用时别再踩）

- **纵向滑动一律不拦截**，否则列表不能滚
- **已展开时点右侧操作区必须放行给删除按钮** —— 早期版本把这一下当成「点击收起」拦掉了，
  按钮永远点不到；`tests/SwipeTest.java` 里有专门的回归用例
- **列表条目复用时必须复位位移**，否则上一行滑开的状态会串到下一行
- 同一时刻只允许一行展开，列表滚动时自动收起
- 手势判定抽成了纯 Java 的 `SwipeState`，可在电脑上直接跑测试
