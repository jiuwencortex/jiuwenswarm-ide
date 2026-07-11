const esbuild = require('esbuild');
const watch = process.argv.includes('--watch');

const ctx = esbuild.context({
  entryPoints: ['src/extension.ts'],
  bundle: true,
  outfile: 'out/extension.js',
  external: ['vscode'],
  format: 'cjs',
  platform: 'node',
  target: 'node18',
  sourcemap: true,
  minify: !watch,
  logLevel: 'info',
}).then(ctx => {
  if (watch) {
    ctx.watch().then(() => console.log('[esbuild] watching…'));
  } else {
    ctx.rebuild().then(() => {
      console.log('[esbuild] build complete');
      ctx.dispose();
    });
  }
}).catch(() => process.exit(1));
