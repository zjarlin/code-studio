# Lib

与界面框架无关的 HTTP、SSE 与 WebSocket 传输辅助函数。业务端点只由 `dist/generated` 导出；请求只访问当前站点，并从宿主 `adminHostBridge` 注入登录令牌与租户上下文。
