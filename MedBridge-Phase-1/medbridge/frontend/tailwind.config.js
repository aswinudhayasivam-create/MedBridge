/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        background: '#0A0A0A',
        surface: '#111111',
        surfaceRaised: '#161616',
        border: '#1F2A24',
        primary: '#22C55E',
        primaryHover: '#16A34A',
        mint: '#A7F3D0',
        muted: '#A3A3A3',
      },
      fontFamily: { heading: ['Poppins', 'sans-serif'], body: ['Inter', 'sans-serif'] },
      borderRadius: { card: '16px' },
    },
  },
  plugins: [],
};
