/**
 * 模型探针 —— 换模型/换供应商前先验证这家的接口支不支持我们要用的参数。
 *
 * 起因：把 deepseek-chat 改成 deepseek-flash 之后，匹配接口返回了空结果 ——
 * JSON 解析成功了，但里面既没有 answer 也没有 matches。
 * 怀疑是 response_format 的支持情况不同。不猜，直接打。
 *
 * 用法：node eval/probe-model.js [模型名] [api-url]
 */

const KEY = process.env.DEEPSEEK_API_KEY;
const MODEL = process.argv[2] || 'deepseek-flash';
const URL = process.argv[3] || 'https://api.deepseek.com/v1/chat/completions';
const MAX_TOKENS = Number(process.argv[4] || 500); // 和 application.yml 的 llm.max-tokens 保持一致

const payload = {
  model: MODEL,
  messages: [
    {
      role: 'user',
      content:
        '从候选里挑出最像「黑色苹果手机」的，最多 2 个。\n' +
        '只返回 JSON，格式：{"answer":"一句话总结","matches":[{"itemId":数字,"score":0到100的整数,"reason":"简短理由"}]}\n' +
        '候选（格式：id|标题|类别|地点|描述）：\n' +
        '1|iPhone 15 深空黑|电子产品|图书馆三楼|在图书馆三楼捡到深空黑的iPhone 15\n' +
        '2|红色雨伞|其他|二食堂|捡到一把红色雨伞\n' +
        '3|黑色小米14|电子产品|操场|黑色小米14手机一部',
    },
  ],
  response_format: { type: 'json_object' },
  max_tokens: MAX_TOKENS,
  temperature: 0,
};

console.log(`模型: ${MODEL}\n地址: ${URL}\n`);

const res = await fetch(URL, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${KEY}` },
  body: JSON.stringify(payload),
});

console.log('HTTP:', res.status);
const raw = await res.text();
if (!res.ok) {
  console.log('错误返回:', raw.slice(0, 600));
  process.exit(1);
}

const json = JSON.parse(raw);
console.log('返回的 model 字段:', json.model);
console.log('usage:', JSON.stringify(json.usage));
const content = json.choices?.[0]?.message?.content;
console.log('\ncontent 原文:');
console.log(content);

try {
  const parsed = JSON.parse(content);
  console.log('\n解析后 keys:', Object.keys(parsed));
  console.log('matches 是数组吗:', Array.isArray(parsed.matches), '长度:', parsed.matches?.length);
} catch (e) {
  console.log('\n❌ content 不是合法 JSON:', e.message);
}
