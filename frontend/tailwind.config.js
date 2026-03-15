/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
        },
        node: {
          start: '#10b981',
          end: '#ef4444',
          llm: '#8b5cf6',
          agent: '#f59e0b',
          condition: '#ec4899',
          knowledge: '#06b6d4',
          code: '#6366f1',
          http: '#84cc16',
          template: '#f97316',
          variable: '#14b8a6',
        }
      },
    },
  },
  plugins: [],
}
