#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

const DEFAULT_SDK_DIR = '/Users/fang/.npm/_npx/74dfe5d932228314/node_modules/@modelcontextprotocol/sdk/dist/esm/client';
const DEFAULT_TOOLS = [
  'docx.v1.document.create',
  'docx.v1.documentBlockChildren.create',
  'docx.v1.documentBlock.batchUpdate',
  'docx.v1.documentBlock.list',
  'bitable.v1.appTableRecord.update',
  'bitable.v1.appTableRecord.search',
  'drive.v1.file.list',
  'drive.v1.file.delete',
].join(',');

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (!arg.startsWith('--')) continue;
    const key = arg.slice(2);
    if (key === 'update-table' || key === 'delete-after' || key === 'skip-widths' || key === 'help') {
      args[key] = true;
    } else {
      args[key] = argv[++i];
    }
  }
  return args;
}

function usage() {
  return `Usage:
  markdown_to_lark_docx.mjs --source <file.md> --folder-token <folder>
  markdown_to_lark_docx.mjs --git-ref <ref> --git-path <path.md> --folder-token <folder>

Optional:
  --title <title>
  --app-token <bitable app token>
  --table-id <table id>
  --record-id <record id>
  --field-name <field name>
  --update-table
  --delete-after
  --skip-widths
  --lark-tools <tool list>
  --mcp-sdk-dir <path to @modelcontextprotocol/sdk/dist/esm/client>
`;
}

function readConfigArgs() {
  const configPath = resolve(homedir(), '.codex/config.toml');
  if (!existsSync(configPath)) return null;
  const text = readFileSync(configPath, 'utf8');
  const section = text.match(/\[mcp_servers\.lark-user\][\s\S]*?(?=\n\[|$)/);
  if (!section) return null;
  const argsMatch = section[0].match(/args\s*=\s*\[([\s\S]*?)\]/);
  if (!argsMatch) return null;
  const args = [];
  const re = /"((?:\\"|[^"])*)"/g;
  let match;
  while ((match = re.exec(argsMatch[1]))) args.push(match[1].replace(/\\"/g, '"'));
  return args.length ? args : null;
}

async function loadMcpSdk(sdkDir) {
  const require = createRequire(import.meta.url);
  try {
    const clientPath = require.resolve('@modelcontextprotocol/sdk/dist/esm/client/index.js');
    const stdioPath = require.resolve('@modelcontextprotocol/sdk/dist/esm/client/stdio.js');
    return {
      Client: (await import(pathToFileURL(clientPath))).Client,
      StdioClientTransport: (await import(pathToFileURL(stdioPath))).StdioClientTransport,
    };
  } catch {
    return {
      Client: (await import(pathToFileURL(resolve(sdkDir, 'index.js')))).Client,
      StdioClientTransport: (await import(pathToFileURL(resolve(sdkDir, 'stdio.js')))).StdioClientTransport,
    };
  }
}

function buildMcpArgs(options) {
  const configArgs = readConfigArgs();
  const args = configArgs ?? [
    '-y',
    '@larksuiteoapi/lark-mcp',
    'mcp',
    '-a',
    process.env.LARK_APP_ID ?? '',
    '-s',
    process.env.LARK_APP_SECRET ?? '',
    '--oauth',
    '--token-mode',
    'user_access_token',
    '-p',
    '3333',
  ];
  const tools = options['lark-tools'] ?? DEFAULT_TOOLS;
  const next = [...args];
  const toolIndex = next.indexOf('-t');
  if (toolIndex >= 0) {
    next[toolIndex + 1] = tools;
  } else {
    next.push('-t', tools);
  }
  return next;
}

async function createClient(options) {
  const sdkDir = options['mcp-sdk-dir'] ?? process.env.MCP_SDK_DIR ?? DEFAULT_SDK_DIR;
  const { Client, StdioClientTransport } = await loadMcpSdk(sdkDir);
  const client = new Client({ name: 'lark-markdown-docx-sync', version: '0.1.0' });
  await client.connect(new StdioClientTransport({ command: 'npx', args: buildMcpArgs(options) }));
  return client;
}

async function callTool(client, name, args) {
  const result = await client.callTool({ name, arguments: args });
  const text = result.content?.map((item) => item.text || JSON.stringify(item)).join('\n') ?? JSON.stringify(result);
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`${name} returned non-JSON: ${text.slice(0, 500)}`);
  }
  if (json?.code && json.code !== 0) {
    throw new Error(`${name} failed: ${JSON.stringify(json).slice(0, 1000)}`);
  }
  return json;
}

function markdownFromInput(options) {
  if (options.source) return readFileSync(resolve(options.source), 'utf8');
  if (options['git-ref'] && options['git-path']) {
    return execFileSync('git', ['show', `${options['git-ref']}:${options['git-path']}`], { encoding: 'utf8' });
  }
  throw new Error('Missing --source or --git-ref + --git-path');
}

function parseTableRow(line) {
  let value = line.trim();
  if (value.startsWith('|')) value = value.slice(1);
  if (value.endsWith('|')) value = value.slice(0, -1);
  return value.split(/(?<!\\)\|/).map((cell) => cell.trim().replace(/\\\|/g, '|'));
}

function isTableAlign(line) {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
}

function stripMarkdown(value) {
  return value.replace(/`([^`]+)`/g, '$1').replace(/\*\*([^*]+)\*\*/g, '$1').trim();
}

function visualLength(value) {
  let size = 0;
  for (const char of stripMarkdown(value ?? '')) size += char.charCodeAt(0) > 127 ? 2 : 1;
  return size;
}

function quantile(values, ratio) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * ratio))];
}

function tableWidths(rows) {
  const cols = Math.max(...rows.map((row) => row.length));
  const maxWidth = cols <= 2 ? 420 : cols <= 4 ? 340 : cols <= 6 ? 280 : 220;
  const minWidth = cols >= 7 ? 88 : 96;
  const widths = [];
  for (let col = 0; col < cols; col++) {
    const lengths = rows.map((row) => visualLength(row[col] ?? ''));
    const header = lengths[0] ?? 0;
    const body = lengths.slice(1);
    const contentScore = Math.max(
      header * 1.2,
      quantile(body, 0.85),
      quantile(lengths, 0.65),
    );
    widths.push(Math.max(minWidth, Math.min(maxWidth, Math.round(60 + contentScore * 7))));
  }
  return widths;
}

function codeLanguage(language) {
  const key = (language ?? '').trim().toLowerCase();
  const languages = {
    '': 1,
    text: 1,
    plaintext: 1,
    bash: 7,
    sh: 60,
    shell: 60,
    csharp: 8,
    cs: 8,
    cpp: 9,
    c: 10,
    css: 12,
    dart: 15,
    dockerfile: 18,
    go: 22,
    html: 24,
    json: 28,
    java: 29,
    javascript: 30,
    js: 30,
    kotlin: 32,
    markdown: 39,
    md: 39,
    mermaid: 39,
    php: 43,
    powershell: 46,
    python: 49,
    py: 49,
    ruby: 52,
    rust: 53,
    sql: 56,
    swift: 61,
    typescript: 63,
    ts: 63,
    xml: 66,
    yaml: 67,
    yml: 67,
    toml: 75,
  };
  return languages[key] ?? 1;
}

function parseMarkdown(markdown) {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const firstTitle = lines.find((line) => /^#\s+/.test(line));
  const title = firstTitle ? firstTitle.replace(/^#\s+/, '').trim() : 'Markdown document';
  const tokens = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (i === 0 && /^#\s+/.test(line)) continue;
    if (!line.trim()) continue;
    if (/^\s*---+\s*$/.test(line)) {
      tokens.push({ type: 'divider' });
      continue;
    }

    const fence = line.match(/^```([\w-]*)\s*$/);
    if (fence) {
      const code = [];
      while (i + 1 < lines.length && !/^```\s*$/.test(lines[i + 1])) code.push(lines[++i]);
      if (i + 1 < lines.length) i++;
      tokens.push({ type: 'code', language: fence[1] || '', text: code.join('\n') });
      continue;
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/);
    if (heading) {
      tokens.push({ type: 'heading', level: heading[1].length, text: heading[2].trim() });
      continue;
    }

    if (/^\s*\|/.test(line) && i + 1 < lines.length && isTableAlign(lines[i + 1])) {
      const sourceLine = i + 1;
      const rows = [parseTableRow(line)];
      i += 2;
      while (i < lines.length && /^\s*\|/.test(lines[i])) {
        rows.push(parseTableRow(lines[i]));
        i++;
      }
      i--;
      const cols = Math.max(...rows.map((row) => row.length));
      rows.forEach((row) => {
        while (row.length < cols) row.push('');
      });
      tokens.push({ type: 'table', rows, widths: tableWidths(rows), sourceLine });
      continue;
    }

    const quote = line.match(/^>\s?(.*)$/);
    if (quote) {
      tokens.push({ type: 'quote', text: quote[1].trim() });
      continue;
    }

    const bullet = line.match(/^\s*-\s+(.+)$/);
    if (bullet) {
      tokens.push({ type: 'bullet', text: bullet[1].trim() });
      continue;
    }

    const ordered = line.match(/^\s*\d+\.\s+(.+)$/);
    if (ordered) {
      tokens.push({ type: 'ordered', text: ordered[1].trim() });
      continue;
    }

    const paragraph = [line.trim()];
    while (
      i + 1 < lines.length &&
      lines[i + 1].trim() &&
      !/^(#{1,6})\s+/.test(lines[i + 1]) &&
      !/^\s*---+\s*$/.test(lines[i + 1]) &&
      !/^```[\w-]*\s*$/.test(lines[i + 1]) &&
      !/^>\s?/.test(lines[i + 1]) &&
      !/^\s*-\s+/.test(lines[i + 1]) &&
      !/^\s*\d+\.\s+/.test(lines[i + 1]) &&
      !(/^\s*\|/.test(lines[i + 1]) && i + 2 < lines.length && isTableAlign(lines[i + 2]))
    ) {
      paragraph.push(lines[++i].trim());
    }
    tokens.push({ type: 'paragraph', text: paragraph.join('\n') });
  }

  return { title, tokens };
}

function inlineElements(text, style = {}) {
  const elements = [];
  const marker = /(`[^`]+`|\*\*[^*]+\*\*)/g;
  let last = 0;
  for (const match of text.matchAll(marker)) {
    if (match.index > last) {
      elements.push({ text_run: { content: text.slice(last, match.index), ...(Object.keys(style).length ? { text_element_style: style } : {}) } });
    }
    const raw = match[0];
    if (raw.startsWith('`')) {
      elements.push({ text_run: { content: raw.slice(1, -1), text_element_style: { ...style, inline_code: true } } });
    } else {
      elements.push({ text_run: { content: raw.slice(2, -2), text_element_style: { ...style, bold: true } } });
    }
    last = match.index + raw.length;
  }
  if (last < text.length) {
    elements.push({ text_run: { content: text.slice(last), ...(Object.keys(style).length ? { text_element_style: style } : {}) } });
  }
  return elements.length ? elements : [{ text_run: { content: '' } }];
}

function textPayload(text, style = {}) {
  return { elements: inlineElements(text, style), style: { align: 1 } };
}

function blockFor(token) {
  if (token.type === 'heading') {
    const level = Math.min(Math.max(token.level, 1), 9);
    return { block_type: level + 2, [`heading${level}`]: textPayload(token.text) };
  }
  if (token.type === 'bullet') return { block_type: 12, bullet: textPayload(token.text) };
  if (token.type === 'ordered') return { block_type: 13, ordered: textPayload(token.text) };
  if (token.type === 'quote') return { block_type: 15, quote: textPayload(token.text) };
  if (token.type === 'divider') return { block_type: 22, divider: {} };
  if (token.type === 'paragraph') return { block_type: 2, text: textPayload(token.text) };
  if (token.type === 'code') {
    return {
      block_type: 14,
      code: {
        elements: [{ text_run: { content: token.text } }],
        style: { language: codeLanguage(token.language), wrap: true },
      },
    };
  }
  throw new Error(`Unsupported token type: ${token.type}`);
}

async function listBlocks(client, documentId) {
  const blocks = [];
  let pageToken;
  do {
    const response = await callTool(client, 'docx_v1_documentBlock_list', {
      useUAT: true,
      path: { document_id: documentId },
      params: { page_size: 500, ...(pageToken ? { page_token: pageToken } : {}) },
    });
    blocks.push(...(response.items ?? response.data?.items ?? []));
    pageToken = response.page_token ?? response.data?.page_token;
  } while (pageToken);
  return blocks;
}

async function batchTextUpdates(client, documentId, requests, batchSize = 20) {
  for (let i = 0; i < requests.length; i += batchSize) {
    await callTool(client, 'docx_v1_documentBlock_batchUpdate', {
      useUAT: true,
      path: { document_id: documentId },
      params: { document_revision_id: -1 },
      data: { requests: requests.slice(i, i + batchSize) },
    });
  }
}

async function createChildren(client, documentId, index, children) {
  return callTool(client, 'docx_v1_documentBlockChildren_create', {
    useUAT: true,
    path: { document_id: documentId, block_id: documentId },
    params: { document_revision_id: -1 },
    // Omit index and append in processing order. Lark's document tree includes
    // generated table-cell descendants, so manual top-level indexes can drift.
    data: { children },
  });
}

async function updateTableWidths(client, documentId, tableBlock, widths) {
  let updated = 0;
  for (let col = 0; col < widths.length; col++) {
    try {
      await callTool(client, 'docx_v1_documentBlock_batchUpdate', {
        useUAT: true,
        path: { document_id: documentId },
        params: { document_revision_id: -1 },
        data: { requests: [{ block_id: tableBlock.block_id, update_table_property: { column_index: col, column_width: widths[col] } }] },
      });
      updated++;
    } catch {
      // Width is cosmetic. Keep the native table and continue.
    }
  }
  return updated;
}

async function insertTableRows(client, documentId, tableBlock, extraRows) {
  for (let i = 0; i < extraRows; i++) {
    await callTool(client, 'docx_v1_documentBlock_batchUpdate', {
      useUAT: true,
      path: { document_id: documentId },
      params: { document_revision_id: -1 },
      data: { requests: [{ block_id: tableBlock.block_id, insert_table_row: { row_index: -1 } }] },
    });
  }
  const blocks = await listBlocks(client, documentId);
  return blocks.find((block) => block.block_id === tableBlock.block_id) ?? tableBlock;
}

async function findCreatedTable(client, documentId, seenTableBlocks, token, cols) {
  for (let attempt = 0; attempt < 10; attempt++) {
    if (attempt > 0) await new Promise((resolveDelay) => setTimeout(resolveDelay, 1000));
    const blocks = await listBlocks(client, documentId);
    const candidates = blocks.filter((block) => block.block_type === 31 && !seenTableBlocks.has(block.block_id));
    const exact = candidates.find((block) => (
      block.table?.property?.row_size === Math.min(token.rows.length, 9) &&
      block.table?.property?.column_size === cols
    ));
    if (exact) return exact;
    if (candidates.length) return candidates[candidates.length - 1];
  }
  return null;
}

async function fillTable(client, documentId, tableBlock, token) {
  const blocks = await listBlocks(client, documentId);
  const byId = new Map(blocks.map((block) => [block.block_id, block]));
  const cols = token.rows[0].length;
  const requests = [];

  for (let row = 0; row < token.rows.length; row++) {
    for (let col = 0; col < cols; col++) {
      const cellId = tableBlock.table.cells[row * cols + col];
      const cell = byId.get(cellId);
      const textId = cell?.children?.[0];
      if (!textId) continue;
      requests.push({
        block_id: textId,
        update_text_elements: {
          elements: inlineElements(token.rows[row][col], row === 0 ? { bold: true } : {}),
          style: { align: 1 },
          fields: [1],
        },
      });
    }
  }

  await batchTextUpdates(client, documentId, requests);
}

async function syncMarkdown(client, markdown, options) {
  const parsed = parseMarkdown(markdown);
  const title = options.title ?? parsed.title;
  const created = await callTool(client, 'docx_v1_document_create', {
    useUAT: true,
    data: { folder_token: options['folder-token'], title },
  });
  const documentId = created.document.document_id;
  let index = 0;
  let createdBlocks = 0;
  let nativeTables = 0;
  let codeBlocks = 0;
  let widthUpdates = 0;
  const seenTableBlocks = new Set();

  for (let i = 0; i < parsed.tokens.length; ) {
    if (parsed.tokens[i].type !== 'table') {
      const children = [];
      while (i < parsed.tokens.length && parsed.tokens[i].type !== 'table' && children.length < 50) {
        if (parsed.tokens[i].type === 'code') codeBlocks++;
        children.push(blockFor(parsed.tokens[i++]));
      }
      await createChildren(client, documentId, index, children);
      index += children.length;
      createdBlocks += children.length;
      continue;
    }

    const token = parsed.tokens[i++];
    const cols = token.rows[0].length;
    let table;
    const initialRows = Math.min(token.rows.length, 9);
    try {
      table = await createChildren(client, documentId, index, [{
        block_type: 31,
        table: { property: { row_size: initialRows, column_size: cols } },
      }]);
    } catch (error) {
      throw new Error(`Failed to create table at markdown line ${token.sourceLine ?? 'unknown'} (${token.rows.length} rows x ${cols} cols): ${error.message}`);
    }
    let tableBlock = table.children?.[0] ?? table.data?.children?.[0];
    if (!tableBlock) {
      tableBlock = await findCreatedTable(client, documentId, seenTableBlocks, token, cols);
    }
    if (!tableBlock) {
      throw new Error(`Created table at markdown line ${token.sourceLine ?? 'unknown'}, but MCP did not return or list the table block`);
    }
    seenTableBlocks.add(tableBlock.block_id);
    if (token.rows.length > initialRows) {
      tableBlock = await insertTableRows(client, documentId, tableBlock, token.rows.length - initialRows);
    }
    index++;
    nativeTables++;
    createdBlocks++;
    await fillTable(client, documentId, tableBlock, token);
    if (!options['skip-widths']) {
      widthUpdates += await updateTableWidths(client, documentId, tableBlock, token.widths);
    }
  }

  return {
    documentId,
    title,
    url: `https://wcnnpvbxd7li.feishu.cn/docx/${documentId}`,
    tokens: parsed.tokens.length,
    createdBlocks,
    nativeTables,
    codeBlocks,
    widthUpdates,
  };
}

async function updateBitable(client, result, options) {
  if (!options['update-table']) return null;
  const required = ['app-token', 'table-id', 'record-id', 'field-name'];
  const missing = required.filter((key) => !options[key]);
  if (missing.length) throw new Error(`Missing table options: ${missing.map((key) => `--${key}`).join(', ')}`);
  const request = {
    useUAT: true,
    path: {
      app_token: options['app-token'],
      table_id: options['table-id'],
      record_id: options['record-id'],
    },
    data: { fields: { [options['field-name']]: { text: result.title, link: result.url } } },
  };
  try {
    await callTool(client, 'bitable_v1_appTableRecord_update', request);
    return { recordId: options['record-id'], fieldName: options['field-name'], format: 'url' };
  } catch (error) {
    if (!String(error.message).includes('TextFieldConvFail')) throw error;
    request.data.fields[options['field-name']] = result.url;
    await callTool(client, 'bitable_v1_appTableRecord_update', request);
    return { recordId: options['record-id'], fieldName: options['field-name'], format: 'multiline' };
  }
}

async function deleteDocument(client, documentId) {
  await callTool(client, 'drive_v1_file_delete', {
    useUAT: true,
    path: { file_token: documentId },
    params: { type: 'docx' },
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(usage());
    return;
  }
  if (!options['folder-token']) throw new Error('Missing --folder-token');
  const markdown = markdownFromInput(options);
  const client = await createClient(options);
  const started = Date.now();
  try {
    const result = await syncMarkdown(client, markdown, options);
    const tableUpdate = await updateBitable(client, result, options);
    if (options['delete-after']) await deleteDocument(client, result.documentId);
    console.log(JSON.stringify({
      ...result,
      elapsedMs: Date.now() - started,
      tableUpdate,
      deletedAfter: Boolean(options['delete-after']),
    }, null, 2));
  } finally {
    await client.close();
  }
}

main().catch((error) => {
  console.error(error.stack || error.message);
  console.error(usage());
  process.exit(1);
});
