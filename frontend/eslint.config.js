import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import prettier from 'eslint-config-prettier';

export default [
  { ignores: ['dist', 'node_modules'] },
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...react.configs.recommended.rules,
      ...react.configs['jsx-runtime'].rules,
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // JavaScript 프로젝트이며 컨벤션에 PropTypes 규정이 없어 사용하지 않는다.
      'react/prop-types': 'off',
      'no-var': 'error',
      'prefer-const': 'error',
      // console.log 는 커밋 전 제거. 개발용 로그는 utils/logger.js 를 사용한다.
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },
  {
    // 개발 모드 전용 로거는 console 사용을 허용한다.
    files: ['src/utils/logger.js'],
    rules: { 'no-console': 'off' },
  },
  {
    files: ['vite.config.js', 'eslint.config.js'],
    languageOptions: { globals: globals.node },
  },
  prettier,
];
