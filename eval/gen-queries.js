/**
 * 评测用例生成器 —— 把库里的物品反向改写成「失主视角的模糊描述」。
 *
 * 思路：不用人工标 150 条（太慢），而是拿已有的物品当标准答案，
 * 让大模型扮演丢失它的同学，写一句求助的话。标准答案就是那条物品的 id。
 *
 * 分层设计（这是整个评测的关键）：
 *   现在的系统只把「最近发布的 30 条」当成候选池。库里有 1000+ 条未认领招领物品，
 *   也就是说绝大多数物品**压根没机会**被匹配到 —— 这不是匹配算法的问题，是召回的问题。
 *   所以用例分两层，报告才能把两个问题拆开说：
 *     - in_pool : 落在最近 30 条里的物品，测「匹配质量」
 *     - out_pool: 不在候选池里的物品，测「召回能力」
 *
 * 用法：node eval/gen-queries.js
 * 输出：eval/dataset.jsonl
 */

import { writeFileSync } from 'node:fs';

const API = 'http://localhost:8080';
const KEY = process.env.DEEPSEEK_API_KEY;
const OUT = 'eval/dataset.jsonl';

if (!KEY) {
  console.error('未设置 DEEPSEEK_API_KEY');
  process.exit(1);
}

// ───────────────────────── HTTP ─────────────────────────

async function login() {
  const user = { username: 'evalbot', password: 'eval123456' };
  let res = await fetch(`${API}/api/user/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(user),
  });
  let json = await res.json();
  if (!json.data?.token) {
    // 账号不存在就先注册（脚本要能在一台干净的机器上直接跑）
    await fetch(`${API}/api/user/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...user, nickname: 'eval' }),
    });
    res = await fetch(`${API}/api/user/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(user),
    });
    json = await res.json();
  }
  if (!json.data?.token) {
    throw new Error('登录失败: ' + JSON.stringify(json));
  }
  return json.data.token;
}

async function listItems(token, page, size) {
  const url = `${API}/api/item?page=${page}&size=${size}&type=FOUND&status=UNCLAIMED&upordown=DESC`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  const json = await res.json();
  return json.data?.records ?? json.data?.list ?? [];
}

// ───────────────────────── 难度定义 ─────────────────────────

const DIFFICULTIES = [
  {
    key: 'easy',
    weight: 40,
    desc: '换个说法描述同样的特征，保留品牌或型号。比如原文是「iPhone 15 深空黑」，你可以说「黑色的苹果手机」',
  },
  {
    key: 'medium',
    weight: 35,
    desc: '只提一部分特征，故意漏掉型号，让对方靠颜色+品类+地点去猜。比如「一个黑色的小东西，能打电话」',
  },
  {
    key: 'hard',
    weight: 25,
    desc: '用同学之间口语化的说法或别称，不说标准品名。比如把手机说成「我的机子」，把苹果说成「果子牌」',
  },
];

function pickDifficulty(i) {
  // 按固定顺序轮转而不是随机，保证三档难度的数量可控、报告好解释
  const bag = [];
  DIFFICULTIES.forEach((d) => {
    for (let k = 0; k < d.weight; k++) bag.push(d);
  });
  return bag[i % bag.length];
}

// ───────────────────────── 改写 ─────────────────────────

async function rewrite(item, difficulty) {
  const prompt = `这是一条校园失物招领平台上「好心人捡到东西」的记录：

标题：${item.title}
分类：${item.category}
地点：${item.location}
描述：${item.description}

现在请你扮演**丢了这个东西的同学**，写一句话发在平台上求助。

必须满足：
1. 第一人称、口语化，像真的在着急找东西，不要像机器人在描述参数
2. **绝对不能照抄原标题**，也不要写「标题」两个字 —— 照抄就测不出匹配能力了
3. 难度要求：${difficulty.desc}
4. 可以提到大致地点，但不要求和记录里的完全一致（真实的人记性没那么准）
5. 只输出这一句话本身，不要引号、不要解释、不要换行`;

  const res = await fetch('https://api.deepseek.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${KEY}`,
    },
    body: JSON.stringify({
      model: 'deepseek-chat',
      messages: [{ role: 'user', content: prompt }],
      temperature: 0.8, // 改写要多样，别每条都一个腔调
      max_tokens: 200,
    }),
  });

  if (!res.ok) {
    throw new Error(`DeepSeek ${res.status}: ${(await res.text()).slice(0, 200)}`);
  }
  const json = await res.json();
  return json.choices[0].message.content.trim().replace(/^["「『]|["」』]$/g, '');
}

/** 简单并发池：控制同时在飞的请求数，避免打爆限流 */
async function pool(items, limit, fn) {
  const results = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (true) {
      const i = cursor++;
      if (i >= items.length) return;
      try {
        results[i] = await fn(items[i], i);
      } catch (e) {
        console.error(`  [${i}] 失败: ${e.message}`);
        results[i] = null;
      }
    }
  });
  await Promise.all(workers);
  return results;
}

// ───────────────────────── 主流程 ─────────────────────────

const POOL_SIZE = 30; // 与 AiServiceImpl.getCandidates() 里的 size 保持一致

async function main() {
  const token = await login();

  // 取 8 页共 400 条，足够抽出 150 条标准答案，也能覆盖到很靠后的「旧物品」
  const all = [];
  for (let p = 1; p <= 8; p++) {
    const page = await listItems(token, p, 50);
    if (!page.length) break;
    all.push(...page);
  }
  console.log(`拉到 ${all.length} 条 FOUND+UNCLAIMED 物品`);

  // 接口本身就是按 created_at DESC 返回的，所以前 30 条就是系统的候选池
  const inPool = all.slice(0, POOL_SIZE);
  const outPool = all.slice(POOL_SIZE);
  console.log(`候选池内 ${inPool.length} 条，池外 ${outPool.length} 条`);

  // 池内全部用上（只有 30 条，本来就少），池外抽 120 条
  const targets = [
    ...inPool.map((it) => ({ item: it, stratum: 'in_pool' })),
    ...outPool.filter((_, i) => i % Math.max(1, Math.floor(outPool.length / 120)) === 0)
      .slice(0, 120)
      .map((it) => ({ item: it, stratum: 'out_pool' })),
  ];
  console.log(`共生成 ${targets.length} 条用例，开始改写...`);

  let done = 0;
  const rows = await pool(targets, 4, async (t, i) => {
    const difficulty = pickDifficulty(i);
    const query = await rewrite(t.item, difficulty);
    done++;
    if (done % 20 === 0) console.error(`  进度 ${done}/${targets.length}`);
    return {
      id: `q${String(i + 1).padStart(4, '0')}`,
      query,
      groundTruthId: t.item.id,
      groundTruthTitle: t.item.title,
      difficulty: difficulty.key,
      stratum: t.stratum,
    };
  });

  const valid = rows.filter(Boolean);

  // 质量检查：改写后的句子不该原样包含标题，否则这条用例是白送的
  const leaky = valid.filter((r) => r.query.includes(r.groundTruthTitle));
  if (leaky.length) {
    console.error(`⚠️  ${leaky.length} 条用例的 query 原样包含了标题，这些会被剔除`);
  }
  const clean = valid.filter((r) => !r.query.includes(r.groundTruthTitle));

  writeFileSync(OUT, clean.map((r) => JSON.stringify(r)).join('\n') + '\n');

  const byStratum = {};
  const byDiff = {};
  clean.forEach((r) => {
    byStratum[r.stratum] = (byStratum[r.stratum] || 0) + 1;
    byDiff[r.difficulty] = (byDiff[r.difficulty] || 0) + 1;
  });
  console.log(`\n写入 ${OUT}：${clean.length} 条`);
  console.log('分层:', JSON.stringify(byStratum));
  console.log('难度:', JSON.stringify(byDiff));
  console.log('\n--- 抽样 6 条看看 ---');
  clean.slice(0, 6).forEach((r) => {
    console.log(`[${r.stratum}/${r.difficulty}] 「${r.query}」`);
    console.log(`    标准答案 #${r.groundTruthId} ${r.groundTruthTitle}`);
  });
}

main();
