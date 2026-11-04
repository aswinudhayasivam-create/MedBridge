/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        forest: {
          50: "#f2f7f2", 100: "#dcecdc", 200: "#b9d9bd", 300: "#8dbf94",
          400: "#5ea169", 500: "#3d8449", 600: "#2c6838", 700: "#24532e",
          800: "#1e4327", 900: "#193721", 950: "#0c1e12",
        },
        marigold: { 400: "#f7b731", 500: "#f39f0a", 600: "#d98206" },
        cream: "#faf7f0",
      },
      fontFamily: {
        display: ['Fraunces', 'Georgia', 'serif'],
        body: ['"Source Sans 3"', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
