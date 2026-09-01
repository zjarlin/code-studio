import { defineConfig } from 'orval'

const contract = '../modules/development-host/src/main/resources/site/addzero/studio/development/openapi.json'

export default defineConfig({
  client: {
    input: contract,
    output: {
      clean: ['./dist/generated/openapi'],
      client: 'react-query',
      formatter: 'prettier',
      httpClient: 'fetch',
      mode: 'split',
      schemas: './dist/generated/openapi/models',
      target: './dist/generated/openapi/client.ts',
      override: {
        header: () => ['此目录由后端 OpenAPI 生成，请勿手工修改。'],
        mutator: {
          path: './src/lib/http.ts',
          name: 'requestJson',
        },
        operations: {
          fillSpreadsheetTemplate: {
            mutator: {
              path: './src/lib/http.ts',
              name: 'requestBlob',
            },
          },
        },
        fetch: {
          forceSuccessResponse: true,
          includeHttpResponseReturnType: false,
        },
        query: {
          signal: true,
          version: 5,
        },
      },
    },
  },
})
