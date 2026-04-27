module.exports = {
  root: true,
  ignorePatterns: ['**/*.js'],
  overrides: [
    {
      files: ['angular-fe/**/*.ts'],
      excludedFiles: ['angular-fe/**/*.spec.ts'],
      parser: '@typescript-eslint/parser',
      parserOptions: {
        project: ['./angular-fe/tsconfig.json'],
        tsconfigRootDir: __dirname,
        sourceType: 'module',
      },
      plugins: ['@typescript-eslint', 'import', 'sonarjs'],
      extends: [
        'eslint:recommended',
        'plugin:@typescript-eslint/recommended',
        'plugin:@typescript-eslint/recommended-requiring-type-checking',
        'plugin:import/recommended',
        'plugin:import/typescript',
        'plugin:sonarjs/recommended',
        'prettier',
      ],
      settings: {
        'import/parsers': {
          '@typescript-eslint/parser': ['.ts'],
        },
        'import/resolver': {
          node: {
            extensions: ['.js', '.ts'],
          },
        },
      },
      rules: {
        'curly': ['error', 'all'],
        'eqeqeq': ['error', 'smart'],
        'import/first': 'error',
        'import/newline-after-import': ['error', { count: 1 }],
        'import/no-cycle': 'error',
        'import/no-duplicates': 'error',
        'import/no-self-import': 'error',
        'import/order': [
          'error',
          {
            alphabetize: { order: 'asc', caseInsensitive: true },
            'newlines-between': 'always',
            groups: ['builtin', 'external', 'internal', 'parent', 'sibling', 'index', 'type'],
            pathGroups: [
              {
                pattern: '@angular/**',
                group: 'external',
                position: 'before',
              },
              {
                pattern: 'rxjs',
                group: 'external',
                position: 'before',
              },
              {
                pattern: 'rxjs/**',
                group: 'external',
                position: 'before',
              },
            ],
            pathGroupsExcludedImportTypes: ['builtin'],
          },
        ],
        'no-console': ['warn', { allow: ['warn', 'error'] }],
        'no-debugger': 'error',
        'no-duplicate-imports': 'error',
        'no-return-await': 'error',
        'object-shorthand': ['error', 'always'],
        'prefer-const': 'error',
        'sonarjs/cognitive-complexity': ['warn', 15],
        'sonarjs/no-duplicate-string': ['warn', 5],
        '@typescript-eslint/array-type': ['error', { default: 'array-simple' }],
        '@typescript-eslint/consistent-type-definitions': ['error', 'interface'],
        '@typescript-eslint/consistent-type-imports': [
          'error',
          {
            prefer: 'type-imports',
            fixStyle: 'inline-type-imports',
          },
        ],
        '@typescript-eslint/explicit-function-return-type': [
          'warn',
          {
            allowExpressions: true,
            allowTypedFunctionExpressions: true,
          },
        ],
        '@typescript-eslint/no-explicit-any': 'warn',
        '@typescript-eslint/no-floating-promises': 'error',
        '@typescript-eslint/no-inferrable-types': 'error',
        '@typescript-eslint/no-misused-promises': [
          'error',
          {
            checksVoidReturn: {
              arguments: false,
              attributes: false,
            },
          },
        ],
        '@typescript-eslint/no-unused-vars': [
          'error',
          {
            argsIgnorePattern: '^_',
            caughtErrorsIgnorePattern: '^_',
            varsIgnorePattern: '^_',
          },
        ],
        '@typescript-eslint/prefer-nullish-coalescing': 'error',
        '@typescript-eslint/prefer-optional-chain': 'error',
        '@typescript-eslint/require-await': 'error',
      },
    },
    {
      files: ['angular-fe/**/*.spec.ts'],
      parser: '@typescript-eslint/parser',
      parserOptions: {
        project: ['./angular-fe/tsconfig.spec.json'],
        tsconfigRootDir: __dirname,
        sourceType: 'module',
      },
      plugins: ['@typescript-eslint', 'import', 'sonarjs'],
      extends: [
        'eslint:recommended',
        'plugin:@typescript-eslint/recommended',
        'plugin:import/recommended',
        'plugin:import/typescript',
        'plugin:sonarjs/recommended',
        'prettier',
      ],
      rules: {
        'import/order': [
          'error',
          {
            alphabetize: { order: 'asc', caseInsensitive: true },
            'newlines-between': 'always',
            groups: ['builtin', 'external', 'internal', 'parent', 'sibling', 'index', 'type'],
          },
        ],
        '@typescript-eslint/no-explicit-any': 'off',
        '@typescript-eslint/no-unused-vars': [
          'error',
          {
            argsIgnorePattern: '^_',
            varsIgnorePattern: '^_',
          },
        ],
      },
    },
    {
      files: ['angular-fe/**/*.html'],
      rules: {},
    },
  ],
};
