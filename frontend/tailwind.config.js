/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#17211b",
        paper: "#f8faf7",
        line: "#dfe7df"
      },
      boxShadow: {
        soft: "0 10px 30px rgba(23, 33, 27, 0.08)"
      }
    }
  },
  plugins: []
};
