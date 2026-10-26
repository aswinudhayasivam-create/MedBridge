/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: { background: '#f6faf8', primary: '#1ab58b' },
      fontFamily: { body: ['DM Sans', 'sans-serif'], heading: ['Manrope', 'sans-serif'] },
    },
  },
  plugins: [],
};
