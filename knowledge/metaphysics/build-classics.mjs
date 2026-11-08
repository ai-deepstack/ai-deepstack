import fs from "node:fs";
import path from "node:path";
import OpenCC from "opencc-js";

const converter = OpenCC.Converter({ from: "tw", to: "cn" });

const RAW = path.resolve("raw");
const OUT = path.resolve("classics");
fs.mkdirSync(OUT, { recursive: true });

const BOOKS = [
  {
    file: "梅花易數.txt",
    out: "10_梅花易数_序.md",
    title: "梅花易数",
    category: "meihua",
    dynaysty: "宋（旧题邵雍）",
    author: "旧题邵雍撰，黄宗羲序",
    priority: "P0",
  },
  {
    file: "梅花易數_卷一.txt",
    out: "11_梅花易数_卷一_象数易理.md",
    title: "梅花易数·卷一·象数易理",
    category: "meihua",
    dynaysty: "宋（旧题邵雍）",
    author: "旧题邵雍撰",
    priority: "P0",
  },
  {
    file: "梅花易數_卷二.txt",
    out: "12_梅花易数_卷二_断占总诀.md",
    title: "梅花易数·卷二·断占总诀",
    category: "meihua",
    dynaysty: "宋（旧题邵雍）",
    author: "旧题邵雍撰",
    priority: "P0",
  },
  {
    file: "梅花易數_卷三.txt",
    out: "13_梅花易数_卷三_占例八卦类象.md",
    title: "梅花易数·卷三·占例与八卦类象",
    category: "meihua",
    dynaysty: "宋（旧题邵雍）",
    author: "旧题邵雍撰",
    priority: "P0",
  },
  {
    file: "滴天髓.txt",
    out: "20_滴天髓.md",
    title: "滴天髓",
    category: "bazi",
    dynaysty: "明",
    author: "旧题京图撰，刘基注",
    priority: "P0",
  },
  {
    file: "增刪卜易.txt",
    out: "21_增删卜易.md",
    title: "增删卜易",
    category: "liuyao",
    dynaysty: "清",
    author: "野鹤老人",
    priority: "P0",
  },
  {
    file: "淵海子平.txt",
    out: "22_渊海子平.md",
    title: "渊海子平",
    category: "bazi",
    dynaysty: "宋",
    author: "徐大升",
    priority: "P1",
  },
  {
    file: "三命通會_卷一.txt",
    out: "30_三命通会_卷一.md",
    title: "三命通会·卷一",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷二.txt",
    out: "31_三命通会_卷二.md",
    title: "三命通会·卷二",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷三.txt",
    out: "32_三命通会_卷三.md",
    title: "三命通会·卷三",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷四.txt",
    out: "33_三命通会_卷四.md",
    title: "三命通会·卷四",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷五.txt",
    out: "34_三命通会_卷五.md",
    title: "三命通会·卷五",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷六.txt",
    out: "35_三命通会_卷六.md",
    title: "三命通会·卷六",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷七.txt",
    out: "36_三命通会_卷七.md",
    title: "三命通会·卷七",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷八.txt",
    out: "37_三命通会_卷八.md",
    title: "三命通会·卷八",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "三命通會_卷九.txt",
    out: "38_三命通会_卷九.md",
    title: "三命通会·卷九",
    category: "bazi",
    dynaysty: "明",
    author: "万民英",
    priority: "P1",
  },
  {
    file: "易傳_繫辭上.txt",
    out: "01_易经_系辞上.md",
    title: "系辞上",
    category: "yijing",
    dynaysty: "先秦",
    author: "佚名（易传）",
    priority: "P0",
  },
  {
    file: "易傳_繫辭下.txt",
    out: "02_易经_系辞下.md",
    title: "系辞下",
    category: "yijing",
    dynaysty: "先秦",
    author: "佚名（易传）",
    priority: "P0",
  },
  {
    file: "易傳_說卦.txt",
    out: "03_易经_说卦.md",
    title: "说卦",
    category: "yijing",
    dynaysty: "先秦",
    author: "佚名（易传）",
    priority: "P0",
  },
  {
    file: "易傳_序卦.txt",
    out: "04_易经_序卦.md",
    title: "序卦",
    category: "yijing",
    dynaysty: "先秦",
    author: "佚名（易传）",
    priority: "P1",
  },
  {
    file: "易傳_雜卦.txt",
    out: "05_易经_杂卦.md",
    title: "杂卦",
    category: "yijing",
    dynaysty: "先秦",
    author: "佚名（易传）",
    priority: "P1",
  },
  {
    file: "煙波釣叟歌.txt",
    out: "40_奇门遁甲_烟波钓叟歌.md",
    title: "烟波钓叟歌",
    category: "qimen",
    dynaysty: "北宋传，明罗通增删",
    author: "旧题罗通增删",
    priority: "P0",
  },
  {
    file: "奇門遁甲元靈經.txt",
    out: "41_奇门遁甲_元灵经.md",
    title: "奇门遁甲元灵经",
    category: "qimen",
    dynaysty: "不详",
    author: "佚名",
    priority: "P1",
  },
  {
    file: "奇門遁甲統宗.txt",
    out: "42_奇门遁甲_统宗.md",
    title: "奇门遁甲统宗",
    category: "qimen",
    dynaysty: "明",
    author: "不详",
    priority: "P1",
  },
  {
    file: "遁甲演義.txt",
    out: "43_奇门遁甲_演义.md",
    title: "遁甲演义",
    category: "qimen",
    dynaysty: "明",
    author: "程道生",
    priority: "P1",
  },
];

/** 易学专有用字的简体归一：正文统一写作「干」，书名/标题保留原样 */
function normalizeYiXue(text) {
  return text.replace(/乾/g, "干");
}

/** 去掉维基标记，繁转简，清理首行全角空格和空行 */
function stripWikitext(text) {
  let out = text;
  out = out.replace(/<!--[\s\S]*?-->/g, "");
  out = out.replace(/\[\[[^\]|]*\|([^\]]*)\]\]/g, "$1");
  out = out.replace(/\[\[([^\]]*)\]\]/g, "$1");
  out = out.replace(/\{\{[^{}]*\}\}/g, "");
  out = out.replace(/\{\{[^{}]*/g, "");
  out = out.replace(/\}\}/g, "");
  out = out.replace(/<[^<>]+>/g, "");
  out = out.replace(/^'+|'+$/gm, "");
  out = out.replace(/\r\n?/g, "\n");
  out = out.replace(/[ \t　]{2,}/g, " ");
  out = out
    .split("\n")
    .map((line) => line.replace(/^[　\s]+|[　\s]+$/g, ""))
    .join("\n");
  out = out.replace(/\n{3,}/g, "\n\n");
  return normalizeYiXue(converter(out.trim()));
}

const PREFIX_LINES = new Set([
  "序",
  "序言",
  "自序",
  "凡例",
  "目录",
  "题解",
]);

let totalSections = 0;
let totalChars = 0;

for (const book of BOOKS) {
  const rawPath = path.join(RAW, book.file);
  if (!fs.existsSync(rawPath)) {
    console.log("SKIP (missing)", book.file);
    continue;
  }
  const raw = fs.readFileSync(rawPath, "utf8");
  const cleaned = stripWikitext(raw);

  const lines = cleaned.split("\n");
  const sections = [];
  let current = { level: 1, title: book.title, lines: [] };

  const push = () => {
    const body = current.lines.join("\n").trim();
    if (body) sections.push({ level: current.level, title: current.title, body });
  };

  // 预扫描：没有 `=` 标记的文件，用「短行 + 前后空行」启发式补标题
  const hasWikiHeading = lines.some((line) => /^(=+)\s*.+?\s*=+\s*$/.test(line));
  if (!hasWikiHeading) {
    const rebuilt = [];
    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index];
      const trimmed = line.trim();
      const previous = lines[index - 1]?.trim() ?? "";
      const next = lines[index + 1]?.trim() ?? "";
      const looksLikeHeading =
        trimmed.length > 0
        && trimmed.length <= 20
        && trimmed !== book.title
        && !/[，。；：、]$/.test(trimmed)
        && previous === ""
        && next === "";
      rebuilt.push(looksLikeHeading ? `=== ${trimmed} ===` : line);
    }
    lines.splice(0, lines.length, ...rebuilt);
  }

  for (const line of lines) {
    const heading = line.match(/^(=+)\s*(.+?)\s*=+\s*$/);
    if (heading) {
      push();
      current = {
        level: Math.min(4, 7 - heading[1].length),
        title: normalizeYiXue(converter(heading[2].trim())),
        lines: [],
      };
      continue;
    }
    // 回退规则：维基文库有些书用粗体标题，而非 `=` 标记
    const bold = line.match(/^'''(.+?)'''$/);
    if (bold && bold[1].trim().length <= 20) {
      push();
      current = { level: 3, title: normalizeYiXue(bold[1].trim()), lines: [] };
      continue;
    }
    current.lines.push(line);
  }
  push();

/** 元数据字段统一转简体 */
function meta(value) {
  return normalizeYiXue(converter(value));
}

  const metaLines = [
    `书名：${meta(book.title)}`,
    `类别：${book.category}`,
    `作者：${meta(book.author)}`,
    `朝/代：${meta(book.dynaysty)}`,
    `来源：维基文库（公有领域），已转简体、清洗维基标记`,
    `优先级：${book.priority}`,
  ];

  const outLines = [
    `# ${meta(book.title)}`,
    "",
    ...metaLines,
    "",
    "> 性质：公版古籍原文的数字化整理。标点、分章和字形可能与现代整理本有差异，重要引用请与权威整理本对照。",
    "",
    "---",
    "",
  ];

  for (const section of sections) {
    const headingText = section.title;
    const heading = section.level >= 2 ? "#".repeat(2) : "##";
    outLines.push(`${heading} ${headingText}`);
    outLines.push("");
    outLines.push(section.body);
    outLines.push("");
    totalSections += 1;
  }

  const output = outLines.join("\n").replace(/\n{4,}/g, "\n\n\n") + "\n";
  fs.writeFileSync(path.join(OUT, book.out), output, "utf8");
  totalChars += output.length;
  console.log(
    "OK",
    String(output.length).padStart(8),
    String(sections.length).padStart(4),
    "节",
    book.out,
  );
}

console.log(`\ntotal: ${BOOKS.length} files, ${totalSections} sections, ${totalChars} chars`);
