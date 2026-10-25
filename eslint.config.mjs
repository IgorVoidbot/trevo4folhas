import js from '@eslint/js';
import globals from 'globals';

export default [
  {
    ignores: [
      'target/**',
      'node_modules/**',
      // Compiled by `npm run css:build`; edit src/frontend/styles/ instead.
      'src/main/resources/static/css/**',
    ],
  },
  js.configs.recommended,
  {
    // Browser ES modules served from the application (no inline handlers, no eval).
    files: ['src/main/resources/static/js/**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
      },
    },
  },
  {
    // Build tooling and frontend sources run under Node.js during development.
    files: ['**/*.mjs', 'src/frontend/**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.node,
      },
    },
  },
];
