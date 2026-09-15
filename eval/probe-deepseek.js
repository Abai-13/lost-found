/**
 * DeepSeek API 探针 —— 在写代码之前先验证接口能力，不靠记忆。
 *
 * 要验证两件事：
 *   1. response_format: json_object 到底支不支持（不验证就写进代码，跑起来才发现报错）
 *   2. 返回体里的 usage 字段长什么样（P2 要拿 prompt_tokens 证明成本下降，得先知道字段名）
 *
 * 用法：node eval/probe-deepseek.js
 */

const KEY = process.env.DEEPSEEK_API_KEY;
if (!KEY) {
  console.error('未设置 DEEPSEEK_API_KEY');
  process.exit(1);
}

const payload = {
  model: 'deepseek-chat',
  messages: [
    {
      role: 'user',
      content:
        '从候选物品里挑出最像「我丢了个黑色的苹果手机」的，最多挑 2 个。\n' +
        '只返回 JSON，格式：{"matches":[{"itemId":数字,"score":0到100的整数,"reason":"简短理由"}]}\n' +
        '候选：[{"id":1,"title":"iPhone 15 深空黑"},{"id":2,"title":"红色雨伞"},{"id":3,"title":"黑色小米14"}]',
    },
  ],
  response_format: { type: 'json_object' },
  temperature: 0,
};

const t0 = Date.now();
const res = await fetch('https://api.deepseek.com/v1/chat/completions', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${KEY}`,
  },
  body: JSON.stringify(payload),
});
const elapsed = Date.now() - t0;

console.log('HTTP 状态:', res.status, `(${elapsed}ms)`);
const raw = await res.text();
console.log('--- 原始返回（截断 1500 字符）---');
console.log(raw.slice(0, 1500));

if (res.ok) {
  const json = JSON.parse(raw);
  console.log('\n--- 解析 ---');
  console.log('usage 字段:', JSON.stringify(json.usage));
  const content = json.choices?.[0]?.message?.content;
  console.log('content 原文:', content);
  try {
    const parsed = JSON.parse(content);
    console.log('✅ JSON 解析成功:', JSON.stringify(parsed));
    console.log('   matches 是不是数组:', Array.isArray(parsed.matches));
  } catch (e) {
    console.log('❌ content 不是合法 JSON:', e.message);
  }
}
