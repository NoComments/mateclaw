import { defineConfig } from 'vite'
import { resolve } from 'path'

export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'QingwenClawsWebChat',
      formats: ['es', 'umd'],
      fileName: (format) => `qingwenclaws-webchat.${format}.js`,
    },
    rollupOptions: {
      output: {
        assetFileNames: 'qingwenclaws-webchat.[ext]',
      },
    },
    cssCodeSplit: false,
    minify: 'esbuild',
  },
})
