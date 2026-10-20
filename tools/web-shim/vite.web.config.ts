// Copied into the client directory by start-client.sh.
// Reuses the client's own Vite config, proxies /api (HTTP + WebSocket) to tio-chat-server
// and injects the browser shim before any module script runs.
import type { ConfigEnv, Plugin, UserConfig } from 'vite'
import base from './vite.config'

const shimPlugin: Plugin = {
  name: 'tauri-web-shim',
  transformIndexHtml: {
    order: 'pre',
    handler: (html: string) => html.replace('<head>', '<head>\n    <script src="/__shim/tauri-shim.js"></script>')
  }
}

export default async (env: ConfigEnv): Promise<UserConfig> => {
  const resolved = (typeof base === 'function' ? await base(env) : await base) as UserConfig
  const port = Number(process.env.WEB_PORT || 6130)
  resolved.server = {
    ...(resolved.server || {}),
    host: '0.0.0.0',
    port,
    strictPort: true,
    hmr: { protocol: 'ws', port },
    proxy: {
      '/api': {
        target: process.env.API_TARGET || 'http://127.0.0.1:18760',
        changeOrigin: true,
        ws: true
      }
    }
  }
  resolved.plugins = [...(resolved.plugins || []), shimPlugin]
  return resolved
}
