/**
 * 测试数据生成器 —— 生成校园失物招领的模拟物品数据。
 *
 * 为什么不用「INSERT ... SELECT + RAND()」直接灌：
 *   那种写法生成的描述是「物品1」「物品2」，语义上没有任何信息量，
 *   拿它做 AI 匹配评测等于用随机数测随机数。
 *   评测要测的是「模糊描述 → 精确物品」的匹配能力，
 *   所以每条数据必须带真实的 品牌/型号/颜色/特征 信息。
 *
 * 为什么用固定种子：
 *   评测集必须可复现。跑两遍生成出不同的数据，前后的 Recall 就没法比了。
 *
 * 用法：node eval/gen-items.js > eval/seed-items.sql
 */

const fs = require('fs');

// ───────────────────────── 可复现随机 ─────────────────────────
// 用固定种子的伪随机数而不是 Math.random()：
// 同一个种子永远产出同一批数据，评测前后的 Recall 才可比。
//
// ⚠️ 这里踩过一个坑，记下来：
// 最初写的是经典 LCG —— `s = (s * 1103515245 + 12345) & 0x7fffffff`。
// 看着没问题，但 JS 的 * 是浮点乘法：2^31 * 1103515245 ≈ 2.37e18，
// 远超双精度能精确表示的 Number.MAX_SAFE_INTEGER ≈ 9.0e15，
// 低位被静默舍入。前 2000 次抽样看不出异常，抽到 3 万次时
// 只剩 11836 个不同值（60% 退化），生成的 2000 条数据里
// 时间戳只落在 928 个不同的秒上。
//
// 教训：JS 里做 32 位整数运算要用 Math.imul，或者干脆选一个
// 全程不溢出的算法。这里换 xorshift32 —— 异或和移位都是 32 位安全的。
let _seed = 20260915;
function rndUnit() {
  _seed ^= _seed << 13; _seed >>>= 0;
  _seed ^= _seed >>> 17;
  _seed ^= _seed << 5;  _seed >>>= 0;
  return _seed;
}
// 除以 2^32 而不是 (2^32 - 1)，保证结果严格 < 1，
// 否则 Math.floor(rnd() * len) 在极少数情况下会越界取到 undefined
function rnd() {
  return rndUnit() / 4294967296;
}
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const int = (min, max) => min + Math.floor(rnd() * (max - min + 1));
// 按权重挑：把「常见的多、少见的少」这种真实分布造出来
function weighted(pairs) {
  const total = pairs.reduce((s, p) => s + p[1], 0);
  let r = rnd() * total;
  for (const [val, w] of pairs) {
    if ((r -= w) <= 0) return val;
  }
  return pairs[pairs.length - 1][0];
}

// ───────────────────────── 词表 ─────────────────────────

const LOCATIONS = [
  '图书馆三楼', '图书馆一楼自习室', '第一教学楼 302', '第二教学楼阶梯教室',
  '一号食堂二楼', '二号食堂', '体育馆羽毛球馆', '操场看台',
  '三号宿舍楼下', '五号宿舍楼门口', '实验楼 B 座', '南门快递站',
  '大学生活动中心', '报告厅', '校医院门口', '计算机学院机房',
  '北区篮球场', '校车站台',
];

const COLORS = ['黑色', '白色', '银色', '深空灰', '深空黑', '蓝色', '红色',
  '粉色', '绿色', '金色', '透明', '迷彩', '藏青色', '米色'];

// 电子产品：品牌 + 型号 + 特征。型号要够具体，才能测出「模糊描述能不能找回精确物品」
const ELECTRONICS = [
  { name: '手机', brands: [['Apple', ['iPhone 15 Pro', 'iPhone 15', 'iPhone 14', 'iPhone 13']],
                          ['华为', ['Mate 60 Pro', 'P60', 'nova 12']],
                          ['小米', ['小米 14', 'Redmi K70', '小米 13']],
                          ['荣耀', ['Magic6', '荣耀 100']],
                          ['OPPO', ['Find X7', 'Reno11']],
                          ['vivo', ['X100', 'iQOO 12']]] },
  { name: '笔记本电脑', brands: [['联想', ['小新 Pro 16', 'ThinkPad X1 Carbon', '拯救者 Y9000P']],
                                ['戴尔', ['XPS 13', '灵越 15']],
                                ['Apple', ['MacBook Air M2', 'MacBook Pro 14']],
                                ['华为', ['MateBook D14', 'MateBook X Pro']],
                                ['华硕', ['天选 4', '无畏 Pro 15']]] },
  { name: '平板电脑', brands: [['Apple', ['iPad Air 5', 'iPad Pro 11', 'iPad 9']],
                              ['华为', ['MatePad 11', 'MatePad Pro']],
                              ['小米', ['小米平板 6']]] },
  { name: '蓝牙耳机', brands: [['Apple', ['AirPods Pro 2', 'AirPods 3']],
                              ['华为', ['FreeBuds Pro 3']],
                              ['小米', ['Redmi Buds 5']],
                              ['索尼', ['WF-1000XM5']],
                              ['漫步者', ['LolliPods']]] },
  { name: '充电宝', brands: [['小米', ['20000mAh']], ['罗马仕', ['10000mAh']],
                            ['Anker', ['PowerCore']], ['华为', ['12000mAh']]] },
  { name: '智能手表', brands: [['Apple', ['Apple Watch S9', 'Apple Watch SE']],
                              ['华为', ['Watch GT4']], ['小米', ['手环 8']]] },
  { name: 'U盘', brands: [['金士顿', ['64G']], ['闪迪', ['128G']], ['三星', ['32G']]] },
  { name: '鼠标', brands: [['罗技', ['MX Master 3', 'G502']], ['雷蛇', ['炼狱蝰蛇']]] },
  { name: '键盘', brands: [['罗技', ['K380']], ['Keychron', ['K8']], ['雷蛇', ['黑寡妇']]] },
  { name: '相机', brands: [['索尼', ['A7M4', 'ZV-E10']], ['佳能', ['EOS R50']]] },
  { name: '数据线', brands: [['Apple', ['Type-C 编织线']], ['小米', ['Type-C 快充线']]] },
  { name: '电子书阅读器', brands: [['Kindle', ['Paperwhite 5']], ['掌阅', ['Smart X']]] },
];

const DOCUMENTS = [
  { name: '校园卡', brands: [['', ['', '']]] },
  { name: '学生证', brands: [['', ['', '']]] },
  { name: '身份证', brands: [['', ['', '']]] },
  { name: '银行卡', brands: [['工商银行', ['储蓄卡']], ['建设银行', ['储蓄卡']], ['招商银行', ['储蓄卡']]] },
  { name: '图书借阅证', brands: [['', ['', '']]] },
  { name: '英语四级准考证', brands: [['', ['', '']]] },
];

const CLOTHES = [
  { name: '外套', brands: [['优衣库', ['摇粒绒']], ['耐克', ['运动夹克']], ['The North Face', ['冲锋衣']]] },
  { name: '卫衣', brands: [['Champion', ['连帽']], ['耐克', ['套头']], ['阿迪达斯', ['']]] },
  { name: '羽绒服', brands: [['波司登', ['短款']], ['优衣库', ['轻型']]] },
  { name: '帽子', brands: [['MLB', ['棒球帽']], ['耐克', ['鸭舌帽']]] },
  { name: '围巾', brands: [['', ['羊毛', '针织']]] },
  { name: '手套', brands: [['', ['触屏', '毛线']]] },
  { name: '运动鞋', brands: [['耐克', ['Air Force 1', 'Dunk']], ['阿迪达斯', ['Ultraboost']], ['安踏', ['KT']]] },
];

const BOOKS = [
  { name: '《高等数学》', brands: [['同济大学', ['第七版上册', '第七版下册']]] },
  { name: '《线性代数》', brands: [['同济大学', ['第六版']]] },
  { name: '《数据结构》', brands: [['严蔚敏', ['C语言版']]] },
  { name: '《计算机网络》', brands: [['谢希仁', ['第八版']]] },
  { name: '《操作系统》', brands: [['汤小丹', ['第四版']]] },
  { name: '《Java 编程思想》', brands: [['机械工业', ['第四版']]] },
  { name: '《考研数学复习全书》', brands: [['李永乐', ['2025版']]] },
  { name: '《大学英语综合教程》', brands: [['外研社', ['第三册']]] },
  { name: '笔记本', brands: [['', ['活页', '方格', '横线']]] },
];

const OTHERS = [
  { name: '钥匙', brands: [['', ['宿舍钥匙', '车钥匙', '一串钥匙']]] },
  { name: '雨伞', brands: [['天堂', ['折叠伞', '长柄伞']], ['蕉下', ['防晒伞']]] },
  { name: '水杯', brands: [['膳魔师', ['保温杯']], ['希乐', ['吸管杯']], ['', ['玻璃杯']]] },
  { name: '眼镜', brands: [['', ['黑框近视镜', '金属细框', '太阳镜']]] },
  { name: '书包', brands: [['耐克', ['双肩包']], ['JanSport', ['双肩包']], ['小米', ['学院包']]] },
  { name: '钱包', brands: [['', ['短款', '长款']]] },
  { name: '篮球', brands: [['斯伯丁', ['7号']], ['耐克', ['7号']]] },
  { name: '充电器', brands: [['Apple', ['20W']], ['小米', ['67W 氮化镓']], ['联想', ['65W']]] },
  { name: '计算器', brands: [['卡西欧', ['fx-991CN']]] },
  { name: '台灯', brands: [['小米', ['充电式']], ['飞利浦', ['护眼']]] },
];

// 分类 → 词表 + 该分类被抽中的权重（电子产品最常见，符合校园真实分布）
const CATEGORIES = [
  { key: '电子产品', pool: ELECTRONICS, weight: 35 },
  { key: '证件', pool: DOCUMENTS, weight: 15 },
  { key: '衣物', pool: CLOTHES, weight: 15 },
  { key: '书籍', pool: BOOKS, weight: 20 },
  { key: '其他', pool: OTHERS, weight: 15 },
];

// ───────────────────────── 描述模板 ─────────────────────────
// 十几套句式轮着用，避免 2000 条数据读起来像同一条。
// 关键：描述里要同时出现「能唯一确定物品的细节」和「噪音细节」，
// 因为真实的模糊描述就是这样，评测才有意义。

// 描述模板分「近期」和「通用」两组。
// 一条 5 个月前发布的记录，描述里却写「今天下午丢的」——这是自相矛盾的数据，
// 喂进评测会得出错误结论。所以带时间词的模板只给 3 天内的数据用。
const DESC_LOST_RECENT = [
  (c, l, x) => `今天下午在${l}自习，走的时候忘记拿了，${c}的${x}，应该是放在桌子上了。`,
  (c, l, x) => `大概是昨天傍晚，从${l}出来以后就发现不见了，${c}的${x}。`,
  (c, l, x) => `上午在${l}还用过，中午就找不到了，${c}的${x}。`,
];

const DESC_LOST = [
  (c, l, x) => `在${l}丢了，${c}${x}，对我挺重要的，有捡到的同学麻烦联系我。`,
  (c, l, x) => `在${l}上完课发现${c}${x}没了，可能落在教室后排。`,
  (c, l, x) => `可能是在${l}附近掉的，${c}${x}，上面有我的联系方式。`,
  (c, l, x) => `${l}，${c}${x}，外壳有点磨损，捡到的同学请还给我。`,
  (c, l, x) => `应该是运动的时候放在旁边忘了拿，${l}，${c}的${x}。`,
  (c, l, x) => `前几天在${l}丢的，${c}${x}，一直没找到，再发一次。`,
];

const DESC_FOUND_RECENT = [
  (c, l, x) => `今天在${l}捡到${c}${x}，看着挺急用的，失主联系我。`,
  (c, l, x) => `今天在${l}捡到${c}的${x}，失主请描述特征后来认领。`,
];

const DESC_FOUND = [
  (c, l, x) => `在${l}捡到${c}的${x}一个，失主请描述特征后来认领。`,
  (c, l, x) => `${l}发现${c}${x}，放在值班室了，速来认领。`,
  (c, l, x) => `${l}桌子上有个${c}的${x}，一直没人拿，我交到管理员那了。`,
  (c, l, x) => `捡到${c}${x}，在${l}，有丢失的同学吗？`,
  (c, l, x) => `在${l}拾到${c}${x}，已经交给楼管，凭特征来领。`,
];

// 标题模板：真实场景里用户起标题很随意，长短不一。
// ⚠️ 必须按 type 分开 —— 否则会出现「丢了一个X」配 type=FOUND 的矛盾数据。
// 证件/书籍类没有颜色（c 为空字符串），所以带括号的模板要判空 ——
// 否则会生成「汤小丹 第四版 《操作系统》（）」这种空括号标题。
// 实测不判空的话 2000 条里有 170 条（8.5%）带着空括号。
const TITLES_LOST = [
  (c, x) => (c ? `${c}${x}` : x),
  (c, x) => `丢了一个${c}${x}`,
  (c, x) => `[急] 找${c}${x}`,
  (c, x) => (c ? `${x}（${c}）` : x),
];
const TITLES_FOUND = [
  (c, x) => (c ? `${c}${x}` : x),
  (c, x) => `捡到${c}${x}`,
  (c, x) => `拾到${c}的${x}`,
  (c, x) => (c ? `${x}（${c}）` : x),
];

// ───────────────────────── 生成 ─────────────────────────

function makeItem(id, userId, createdAt, ageMs) {
  const cat = weighted(CATEGORIES.map((c) => [c, c.weight]));
  const tmpl = pick(cat.pool);
  const [brand, models] = pick(tmpl.brands);
  const model = pick(models);

  // 组装「是什么」。注意去重：
  // OTHERS 里「钥匙」的 model 就是「宿舍钥匙」，再拼上 name 会变成「宿舍钥匙 钥匙」。
  const head = [brand, model].filter(Boolean).join(' ');
  const full = head.includes(tmpl.name) ? head : [head, tmpl.name].filter(Boolean).join(' ');

  const x = rnd() < 0.75 ? full : tmpl.name; // 有时用户写得潦草，只写物品名

  // 证件和书籍没有「颜色」这个有效特征，
  // 造出「迷彩的《高等数学》」「黑色的身份证」会污染评测结果。
  const c = cat.key === '证件' || cat.key === '书籍' ? '' : pick(COLORS);

  const type = weighted([['FOUND', 60], ['LOST', 40]]);
  // 状态分布：大部分还没认领，少部分已认领 —— 真实系统的样子
  const status = weighted([['UNCLAIMED', 85], ['CLAIMED', 15]]);

  // 地点只抽一次，描述里和 location 字段用同一个值。
  // 否则会出现「字段写校医院、描述写活动中心」的数据，评测时不知道以哪个为准。
  const location = pick(LOCATIONS);

  // 「今天/昨天」这类措辞只给 3 天内的数据用，否则时间上自相矛盾
  const isRecent = ageMs < 3 * 24 * 3600 * 1000;
  const useRecentTmpl = isRecent && rnd() < 0.5;
  const tmplFn =
    type === 'FOUND'
      ? (useRecentTmpl ? pick(DESC_FOUND_RECENT) : pick(DESC_FOUND))
      : (useRecentTmpl ? pick(DESC_LOST_RECENT) : pick(DESC_LOST));

  return {
    id,
    user_id: userId,
    title: pick(type === 'FOUND' ? TITLES_FOUND : TITLES_LOST)(c, x)
      .replace(/\s+/g, ' ')
      .trim(),
    type,
    category: cat.key,
    location,
    description: tmplFn(c, location, x),
    status,
    created_at: createdAt,
  };
}

function sqlEscape(s) {
  return String(s).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function main() {
  const TOTAL = 2000;
  const NOW = new Date('2026-09-15T10:00:00+08:00');
  const SIX_MONTHS_MS = 180 * 24 * 3600 * 1000;

  // 关键：created_at 要真的散开。
  // 上次演练踩过的坑 —— 一批数据用同一条 INSERT 灌进去，
  // CURRENT_TIMESTAMP 取的是语句执行时刻，结果 10 万条时间戳全一样，
  // 排序演练直接失真。所以这里逐条随机算时间，造完还要 COUNT(DISTINCT) 验。
  // 另外：最近 7 天的数据要占足够比例，评测集要分「近期/早期」两层。
  const items = [];
  for (let i = 1; i <= TOTAL; i++) {
    let offset;
    if (rnd() < 0.3) {
      offset = rnd() * 7 * 24 * 3600 * 1000; // 30% 落在最近 7 天
    } else {
      offset = rnd() * SIX_MONTHS_MS;        // 其余散在过去 180 天
    }
    const ts = new Date(NOW.getTime() - offset);
    const fmt = ts.toISOString().slice(0, 19).replace('T', ' ');
    // id 从 1000 起，避开库里已有的那条测试数据，不删别人的东西
    items.push(makeItem(999 + i, int(1, 5), fmt, offset));
  }

  // 分批 INSERT：单条语句太长会撞 max_allowed_packet
  const BATCH = 200;
  const out = [];
  out.push('-- 由 eval/gen-items.js 生成，请勿手工修改');
  out.push(`-- 共 ${TOTAL} 条，固定种子（可复现，同一份数据每次生成完全一致）`);
  out.push('SET NAMES utf8mb4;');
  out.push('');
  // 让脚本可重复执行。只删 id >= 1000 的生成数据，
  // 不碰手工创建的行（库里原有的测试数据是 id=1）。
  out.push('-- 清掉上一批生成数据（只删 id >= 1000，不动手工数据）');
  out.push('DELETE FROM item WHERE id >= 1000;');
  out.push('');

  const cols = '(id,user_id,title,type,category,location,description,image_url,contact,status,version,created_at,updated_at)';
  for (let i = 0; i < items.length; i += BATCH) {
    const chunk = items.slice(i, i + BATCH);
    const values = chunk.map((it) =>
      `(${it.id},${it.user_id},'${sqlEscape(it.title)}','${it.type}','${it.category}',` +
      `'${sqlEscape(it.location)}','${sqlEscape(it.description)}',NULL,NULL,` +
      `'${it.status}',0,'${it.created_at}','${it.created_at}')`
    );
    out.push(`INSERT INTO item ${cols} VALUES\n${values.join(',\n')};`);
    out.push('');
  }

  process.stdout.write(out.join('\n'));

  // 统计走 stderr，这样重定向到 .sql 文件时不会污染 SQL
  const byType = {};
  const byCat = {};
  const byStatus = {};
  const days = new Set();
  items.forEach((it) => {
    byType[it.type] = (byType[it.type] || 0) + 1;
    byCat[it.category] = (byCat[it.category] || 0) + 1;
    byStatus[it.status] = (byStatus[it.status] || 0) + 1;
    days.add(it.created_at.slice(0, 10));
  });
  const recent7 = items.filter(
    (it) => (NOW - new Date(it.created_at.replace(' ', 'T') + '+08:00')) < 7 * 24 * 3600 * 1000
  ).length;

  console.error(`共生成 ${TOTAL} 条`);
  console.error(`type:   ${JSON.stringify(byType)}`);
  console.error(`status: ${JSON.stringify(byStatus)}`);
  console.error(`category: ${JSON.stringify(byCat)}`);
  console.error(`不同日期数: ${days.size} 天  ← 必须远大于 1，否则时间戳又扎堆了`);
  console.error(`最近 7 天内发布: ${recent7} 条 (${(recent7 / TOTAL * 100).toFixed(1)}%)`);
}

main();
