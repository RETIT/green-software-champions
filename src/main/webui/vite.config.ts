import { defineConfig } from 'vite';

export default defineConfig({
  // No index.html: Quarkus renders the pages, so Vite only builds and serves
  // their scripts and styles
  appType: 'custom',
  build: {
    outDir: 'dist',
    // Tells Quarkus the names of the built files, see Assets.java
    manifest: true,
    rollupOptions: {
      input: ['src/index.ts', 'src/styles/index.css']
    }
  },
  server: {
    port: 5173
  }
});
