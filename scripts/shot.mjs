/**
 * 用 Chrome DevTools Protocol 驱动无头 Chrome 截图。
 * 认证页面需要先把 token 写进 localStorage，所以不能只用
 * `chrome --screenshot` 一把梭 —— 那样每次都会被路由守卫弹回登录页。
 *
 * 用法: node shot.mjs <outDir> <path1> [path2 ...]
 */
import { spawn } from 'node:child_process'
import { mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const ORIGIN = 'http://localhost:5173'
const PORT = 9222
const [outDir, ...paths] = process.argv.slice(2)
const TOKEN = process.env.HK_TOKEN
const USER = process.env.HK_USER
const THEME = process.env.HK_THEME || 'light'
const W = Number(process.env.HK_W || 1440)
const H = Number(process.env.HK_H || 900)

mkdirSync(outDir, { recursive: true })
const profile = join(process.env.TEMP || '/tmp', 'hk-shot-profile')
rmSync(profile, { recursive: true, force: true })

const chrome = spawn(CHROME, [
  '--headless=new',
  `--remote-debugging-port=${PORT}`,
  `--user-data-dir=${profile}`,
  `--window-size=${W},${H}`,
  '--hide-scrollbars',
  '--no-first-run',
  '--no-default-browser-check',
  '--disable-gpu',
  'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function cdpTarget() {
  for (let i = 0; i < 40; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/list`)
      const list = await r.json()
      const page = list.find((t) => t.type === 'page')
      if (page) return page.webSocketDebuggerUrl
    } catch {}
    await sleep(250)
  }
  throw new Error('Chrome CDP 没起来')
}

class Cdp {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.waiters = new Map()
    ws.onmessage = (e) => {
      const msg = JSON.parse(e.data)
      if (msg.id && this.waiters.has(msg.id)) {
        const { resolve, reject } = this.waiters.get(msg.id)
        this.waiters.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      }
    }
  }
  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.waiters.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }
}

const wsUrl = await cdpTarget()
const ws = new WebSocket(wsUrl)
await new Promise((r) => (ws.onopen = r))
const cdp = new Cdp(ws)

await cdp.send('Page.enable')
await cdp.send('Runtime.enable')
await cdp.send('Emulation.setDeviceMetricsOverride', {
  width: W, height: H, deviceScaleFactor: 2, mobile: W < 500,
})

// 先落到同源页面，才能写 localStorage
await cdp.send('Page.navigate', { url: `${ORIGIN}/login` })
await sleep(1800)

if (TOKEN) {
  await cdp.send('Runtime.evaluate', {
    expression: `
      localStorage.setItem('token', ${JSON.stringify(TOKEN)});
      localStorage.setItem('user', ${JSON.stringify(USER)});
      localStorage.setItem('theme.v2', ${JSON.stringify(THEME)});
    `,
  })
}

const errors = []
for (const p of paths) {
  await cdp.send('Page.navigate', { url: ORIGIN + p })
  await sleep(2200)

  // 先滚到底再回顶：入场动效挂在 IntersectionObserver 上，
  // 不滚过一遍，首屏以下的元素会停在 opacity:0，截出来是一片空白
  await cdp.send('Runtime.evaluate', {
    expression: `(async () => {
      const H = document.body.scrollHeight;
      for (let y = 0; y < H; y += 400) { window.scrollTo(0, y); await new Promise(r => setTimeout(r, 60)); }
      window.scrollTo(0, 0);
      await new Promise(r => setTimeout(r, 500));
    })()`,
    awaitPromise: true,
  })
  await sleep(700)

  // 抓页面上的 JS 报错与"没有样式"的信号
  const probe = await cdp.send('Runtime.evaluate', {
    expression: `JSON.stringify({
      url: location.pathname,
      bodyBg: getComputedStyle(document.body).backgroundColor,
      font: getComputedStyle(document.body).fontFamily.slice(0, 40),
      h1: document.querySelector('h1')?.textContent?.trim()?.slice(0,30) || null,
      nav: !!document.querySelector('.app-nav'),
      panels: document.querySelectorAll('.panel, .tile').length,
      labStrips: document.querySelectorAll('.lab').length,
      text: document.body.innerText.slice(0, 120).replace(/\\s+/g,' ')
    })`,
    returnByValue: true,
  })
  console.log(p, '->', probe.result.value)

  const shot = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const name = (p.replace(/[^\w]/g, '_') || 'root') + `_${THEME}_${W}.png`
  writeFileSync(join(outDir, name), Buffer.from(shot.data, 'base64'))
  console.log('  saved', name)
}

ws.close()
chrome.kill()
if (errors.length) console.log('ERRORS', errors)
