/**
 * Embedding 探针 —— 接向量召回（P3）之前，先验证供应商的 embedding 接口。
 *
 * 起因：P2c 的数据显示剩余失败 70% 死在召回层，典型是「本子」↔「笔记本」
 * 这种字面不重合的同义词（2-gram 一个字都对不上）。要用向量救，先得确认
 * 这家供应商的向量接口到底长什么样 —— 不靠记忆写代码。
 *
 * 要回答的 5 个问题：
 *   1. 接口通不通、模型名对不对、在不在免费档
 *   2. 向量维度是多少（文档说 1024，实际数一遍）
 *   3. 返回结构（是 data[0].embedding 吗？有 usage 吗？）
 *   4. 能不能批量（一条条调 vs 一次 64 条，差的是一倍的调用数和几十倍耗时）
 *   5. 「本子」↔「笔记本」向量真能救回来吗 —— P3 的立论基础
 *
 * 用法：
 *   SILICONFLOW_API_KEY=sk-xxx node eval/probe-embedding.js
 *   SILICONFLOW_API_KEY=sk-xxx node eval/probe-embedding.js BAAI/bge-large-zh-v1.5
 */

const KEY = process.env.SILICONFLOW_API_KEY;
const MODEL = process.argv[2] || 'BAAI/bge-m3';
const URL = 'https://api.siliconflow.cn/v1/embeddings';

if (!KEY) {
  console.error('❌ 缺少 SILICONFLOW_API_KEY 环境变量');
  process.exit(1);
}

async function embed(input) {
  const t0 = process.hrtime.bigint();
  const res = await fetch(URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${KEY}` },
    body: JSON.stringify({ model: MODEL, input, encoding_format: 'float' }),
  });
  const ms = Number(process.hrtime.bigint() - t0) / 1e6;
  const raw = await res.text();
  if (!res.ok) {
    console.log('HTTP:', res.status, '耗时', ms.toFixed(0), 'ms');
    console.log('错误返回:', raw.slice(0, 600));
    process.exit(1);
  }
  return { json: JSON.parse(raw), ms };
}

function cosine(a, b) {
  let dot = 0, na = 0, nb = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    na += a[i] * a[i];
    nb += b[i] * b[i];
  }
  return dot / (Math.sqrt(na) * Math.sqrt(nb));
}

console.log(`模型: ${MODEL}\n地址: ${URL}\n`);

// ── 问题 1~3：单条调用，看结构和维度 ────────────────────────────────
console.log('【1】单条调用 —— 验证接口 + 维度 + 返回结构');
const single = await embed('在图书馆三楼捡到一个黑色钱包');
const v = single.json.data?.[0]?.embedding;
console.log('  HTTP: 200，耗时', single.ms.toFixed(0), 'ms');
console.log('  返回顶层 keys:', Object.keys(single.json));
console.log('  usage:', JSON.stringify(single.json.usage));
console.log('  data 长度:', single.json.data?.length, '| data[0] keys:', Object.keys(single.json.data?.[0] || {}));
console.log('  ⭐ 维度:', v?.length);
console.log('  前 3 个值:', v?.slice(0, 3).map((x) => x.toFixed(4)).join(', '));

// ── 问题 5：语义验证（最重要的一个）────────────────────────────────
// 设计要点：既放「同义不同词」（本子/笔记本），也放「同词不同义」
// （笔记本=本子 vs 笔记本电脑）—— 后者是词面匹配的另一个坑，
// 向量如果只对前者有效、对后者翻车，也是必须提前知道的事实。
console.log('\n【2】语义验证 —— 字面不重合时向量救不救得回来');
const QUERY = '有没有人看到我的本子啊，蓝色的那种';
const CANDS = [
  { text: '捡到一本蓝色笔记本，在教三 302', hope: '✅ 目标（同义不同词）' },
  { text: '捡到一台蓝色笔记本电脑，带充电器', hope: '⚠️ 陷阱（同词不同义）' },
  { text: '捡到一把蓝色雨伞', hope: '❌ 不相关' },
  { text: '黑色钱包一个，内有校园卡', hope: '❌ 不相关' },
];
const { json: batch } = await embed([QUERY, ...CANDS.map((c) => c.text)]);
const qv = batch.data[0].embedding;
console.log(`  查询：「${QUERY}」`);
console.log(`  返回条数: ${batch.data.length}（传了 ${1 + CANDS.length} 条）\n`);

const scored = CANDS.map((c, i) => ({
  score: cosine(qv, batch.data[i + 1].embedding),
  text: c.text,
  hope: c.hope,
}));
scored.sort((a, b) => b.score - a.score);
for (const s of scored) {
  console.log(`  ${s.score.toFixed(4)}  ${s.hope.padEnd(22)} ${s.text}`);
}
const top = scored[0];
console.log(
  top.hope.startsWith('✅')
    ? '\n  ✅ 向量把「本子」和「笔记本」连上了 —— P3 立论成立'
    : `\n  ⚠️ 第一名不是目标：${top.text} —— 立论需要重新考虑（可能要用更大的模型或加指令前缀）`
);
const gap = scored[0].score - scored[1].score;
console.log(`  与第二名的分差: ${gap.toFixed(4)}（越小说明越容易被精排层救；排序稳不稳看这个）`);

// ── 问题 4：批量上限 ────────────────────────────────────────────────
console.log('\n【3】批量测试 —— 回填存量数据要用');
for (const n of [32, 64]) {
  const texts = Array.from({ length: n }, (_, i) => `测试文本 ${i}：在操场捡到一个水杯`);
  try {
    const { json, ms } = await embed(texts);
    console.log(`  一次传 ${n} 条 → HTTP 200，返回 ${json.data.length} 条向量，耗时 ${ms.toFixed(0)} ms（${(ms / n).toFixed(1)} ms/条）`);
  } catch (e) {
    console.log(`  一次传 ${n} 条 → ❌ ${e.message}`);
  }
}
