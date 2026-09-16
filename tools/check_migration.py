# -*- coding: utf-8 -*-
"""模拟用户手机上的升级路径：v1 已有数据 -> v2 -> v3，确认数据不丢、结构正确。
   用 Python 的 sqlite3 复刻 DbHelper 里的 SQL，验证语句本身合法。"""
import sqlite3, sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

con = sqlite3.connect(":memory:")
cur = con.cursor()

# ---------- 复刻 v1 结构 ----------
cur.executescript("""
CREATE TABLE txn (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  occurred_at INTEGER NOT NULL, amount_cents INTEGER NOT NULL,
  direction INTEGER NOT NULL, merchant TEXT, category TEXT, account TEXT, note TEXT,
  source INTEGER NOT NULL DEFAULT 0, dedupe_key TEXT,
  status INTEGER NOT NULL DEFAULT 0, refunded_cents INTEGER NOT NULL DEFAULT 0,
  original_txn_id INTEGER NOT NULL DEFAULT 0, raw_id INTEGER NOT NULL DEFAULT 0,
  confidence INTEGER NOT NULL DEFAULT 100, created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL, auto_created INTEGER NOT NULL DEFAULT 0);
CREATE TABLE raw_event (
  id INTEGER PRIMARY KEY AUTOINCREMENT, kind INTEGER NOT NULL, pkg TEXT, sender TEXT,
  title TEXT, body TEXT, received_at INTEGER NOT NULL,
  parsed INTEGER NOT NULL DEFAULT 0, dedupe_key TEXT);
CREATE TABLE refund_link (
  id INTEGER PRIMARY KEY AUTOINCREMENT, refund_txn_id INTEGER NOT NULL,
  original_txn_id INTEGER NOT NULL, amount_cents INTEGER NOT NULL, mode INTEGER NOT NULL,
  score INTEGER NOT NULL DEFAULT 0, reason TEXT, auto_removed INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL);
CREATE TABLE setting (k TEXT PRIMARY KEY, v TEXT);
INSERT INTO setting(k,v) VALUES('category:餐饮','1');
""")

# 用户在 v1 上已经记过的账（包括那条错的 2 元）
cur.execute("""INSERT INTO txn(occurred_at, amount_cents, direction, merchant, category,
               account, source, status, created_at, updated_at)
               VALUES(1756000000000, 200, 0, NULL, '其他', '微信', 2, 0,
                      1756000000000, 1756000000000)""")
cur.execute("""INSERT INTO txn(occurred_at, amount_cents, direction, merchant, category,
               account, source, status, created_at, updated_at)
               VALUES(1756000000000, 3550, 0, '美团外卖', '餐饮', '尾号1234', 2, 0,
                      1756000000000, 1756000000000)""")
con.commit()
before = cur.execute("SELECT COUNT(*), SUM(amount_cents) FROM txn").fetchone()
print("升级前(v1)：%d 条流水，金额合计 %d 分" % before)

# ---------- 复刻 onUpgrade(oldV=1, newV=3) ----------
def has_column(table, col):
    return any(r[1] == col for r in cur.execute("PRAGMA table_info(%s)" % table))

def add_column_if_missing(table, col, sql):
    if not has_column(table, col):
        cur.execute(sql)

add_column_if_missing("txn", "is_transfer",
                      "ALTER TABLE txn ADD COLUMN is_transfer INTEGER NOT NULL DEFAULT 0")
add_column_if_missing("txn", "pair_txn_id",
                      "ALTER TABLE txn ADD COLUMN pair_txn_id INTEGER NOT NULL DEFAULT 0")
cur.execute("CREATE INDEX IF NOT EXISTS idx_txn_pair ON txn(pair_txn_id)")
cur.execute("""CREATE TABLE IF NOT EXISTS transfer_hint (
  id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, channel TEXT, note TEXT,
  consumed INTEGER NOT NULL DEFAULT 0)""")
cur.execute("CREATE INDEX IF NOT EXISTS idx_hint_at ON transfer_hint(at)")
# v4：通知来源表
cur.execute("""CREATE TABLE IF NOT EXISTS notify_src (
  id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, pkg TEXT, title TEXT,
  watched INTEGER NOT NULL DEFAULT 0)""")
cur.execute("CREATE INDEX IF NOT EXISTS idx_src_at ON notify_src(at)")
con.commit()

after = cur.execute("SELECT COUNT(*), SUM(amount_cents) FROM txn").fetchone()
cols = [r[1] for r in cur.execute("PRAGMA table_info(txn)")]
tables = [r[0] for r in cur.execute(
    "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]

print("升级后：%d 条流水，金额合计 %d 分" % after)
print("txn 新列：", [c for c in cols if c in ("is_transfer", "pair_txn_id")])
print("数据表：", tables)

ok = True
if before != after:
    print("  [!] 数据条数/金额发生变化 —— 迁移丢数据了"); ok = False
if "is_transfer" not in cols or "pair_txn_id" not in cols:
    print("  [!] 新列缺失"); ok = False
if "transfer_hint" not in tables:
    print("  [!] transfer_hint 表缺失"); ok = False
if "notify_src" not in tables:
    print("  [!] notify_src 表缺失"); ok = False
# 旧数据默认值应为 0（非转账）
n = cur.execute("SELECT COUNT(*) FROM txn WHERE is_transfer=0 AND pair_txn_id=0").fetchone()[0]
if n != after[0]:
    print("  [!] 老数据的默认值不对"); ok = False

# 幂等：再跑一次迁移不应报错
try:
    add_column_if_missing("txn", "is_transfer", "ALTER TABLE txn ADD COLUMN is_transfer INTEGER NOT NULL DEFAULT 0")
    cur.execute("CREATE TABLE IF NOT EXISTS transfer_hint (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, channel TEXT, note TEXT, consumed INTEGER NOT NULL DEFAULT 0)")
    cur.execute("CREATE TABLE IF NOT EXISTS notify_src (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, pkg TEXT, title TEXT, watched INTEGER NOT NULL DEFAULT 0)")
    print("重复升级：安全（幂等）")
except Exception as e:
    print("  [!] 重复升级报错:", e); ok = False

print()
print("迁移验证：" + ("通过 ✔" if ok else "失败 ✘"))
sys.exit(0 if ok else 1)
