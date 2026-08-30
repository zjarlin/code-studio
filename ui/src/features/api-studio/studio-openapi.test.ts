import { describe, expect, it, vi } from 'vitest'

import { loadStudioOpenApi, resolveStudioApiBaseUrl } from './studio-openapi'

describe('Studio OpenAPI loader', () => {
  it('maps loopback gateway metadata to the LAN host used by Studio', () => {
    expect(resolveStudioApiBaseUrl(
      'http://localhost:48080/example',
      'http://192.0.2.15:5175',
      'http://127.0.0.1:48080',
    )).toBe('http://192.0.2.15:48080/example')
  })

  it('keeps an explicitly configured external application address', () => {
    expect(resolveStudioApiBaseUrl(
      'https://application.example.test/admin-api/',
      'https://studio.example.test',
      '',
    )).toBe('https://application.example.test/admin-api')
  })

  it('keeps an embedded application root as the browser origin', () => {
    expect(resolveStudioApiBaseUrl(
      '/',
      'http://127.0.0.1:48183',
      '',
    )).toBe('http://127.0.0.1:48183')
  })

  it('uses the Studio origin for loopback metadata in a production build', () => {
    expect(resolveStudioApiBaseUrl(
      'http://localhost:48080/example',
      'https://studio.example.test',
      '',
    )).toBe('https://studio.example.test/example')
  })

  it('does not retry a business route-missing response', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValue(new Response(JSON.stringify({ code: 404, msg: '请求地址不存在' }), { status: 200 }))
    const wait = vi.fn().mockResolvedValue(undefined)
    const onRetry = vi.fn()

    await expect(loadStudioOpenApi(
      { displayName: '示例应用', apiBaseUrl: 'http://localhost:48080/example', openApiPath: '/v3/api-docs' },
      { attempts: 2, fetcher, wait, onRetry },
    )).rejects.toThrow('读取 示例应用 OpenAPI 失败：请求地址不存在')

    expect(fetcher).toHaveBeenCalledOnce()
    expect(wait).not.toHaveBeenCalled()
    expect(onRetry).not.toHaveBeenCalled()
  })

  it('retries a temporary HTTP failure until OpenAPI is ready', async () => {
    const document = { openapi: '3.1.0', paths: { '/api/public/banners': { get: {} } } }
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response('', { status: 503 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(document), { status: 200 }))
    const wait = vi.fn().mockResolvedValue(undefined)
    const onRetry = vi.fn()

    const loaded = await loadStudioOpenApi(
      { displayName: '示例应用', apiBaseUrl: 'http://localhost:48080/example', openApiPath: '/v3/api-docs' },
      { attempts: 2, fetcher, wait, onRetry },
    )

    expect(loaded).toEqual(document)
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(wait).toHaveBeenCalledOnce()
    expect(onRetry).toHaveBeenCalledWith('读取 示例应用 OpenAPI 失败：HTTP 503，正在重试')
  })

  it.each([401, 403, 404])('does not retry permanent HTTP %i failures', async (status) => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValue(new Response('', { status }))
    const wait = vi.fn().mockResolvedValue(undefined)

    await expect(loadStudioOpenApi(
      { displayName: '应用', apiBaseUrl: 'https://application.example.test', openApiPath: '/v3/api-docs' },
      { attempts: 2, fetcher, wait },
    )).rejects.toThrow(`读取 应用 OpenAPI 失败：HTTP ${status}`)
    expect(fetcher).toHaveBeenCalledOnce()
    expect(wait).not.toHaveBeenCalled()
  })
})
