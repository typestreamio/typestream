/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./src/**/*.{html,js}",
  ],
  darkMode: "class",
  important: true,
  theme: {
    screens: {
      xs: "540px",
      sm: "640px",
      md: "768px",
      lg: "1024px",
      xl: "1280px",
      "2xl": "1536px",
    },
    fontFamily: {
      nunito: ['"Nunito", sans-serif'],
      "cursive-alex": ['"Alex Brush", cursive'],
      "cursive-kaushan": ['"Kaushan Script", cursive'],
      "head-ebgaramond": ['"EB Garamond", serif'],
      "para-worksans": ['"Work Sans", sans-serif'],
    },
    container: {
      center: true,
      padding: {
        DEFAULT: "12px",
        sm: "1rem",
        lg: "45px",
        xl: "5rem",
        "2xl": "13rem",
      },
    },
    extend: {
      colors: {
        dark: "#3c4858",
        black: "#161c2d",
        "dark-footer": "#192132",
        "typestream-primary": "#154782",
        "typestream-primary-dark": "#134075",
        "typestream-primary-darker": "#123c6f",
        "typestream-primary-darkest": "#0f325b",
        "typestream-primary-light": "#174e8f",
        "typestream-primary-lighter": "#185295",
        "typestream-primary-lightest": "#1b5ca9",
        "typestream-light-background": "#fcffff",
        "typestream-accent": "#feacc6",
        "typestream-grey": "#e6e6e6",
      },

      boxShadow: {
        sm: "0 2px 4px 0 rgb(60 72 88 / 0.15)",
        DEFAULT: "0 0 3px rgb(60 72 88 / 0.15)",
        md: "0 5px 13px rgb(60 72 88 / 0.20)",
        lg: "0 10px 25px -3px rgb(60 72 88 / 0.15)",
        xl: "0 20px 25px -5px rgb(60 72 88 / 0.1), 0 8px 10px -6px rgb(60 72 88 / 0.1)",
        "2xl": "0 25px 50px -12px rgb(60 72 88 / 0.25)",
        inner: "inset 0 2px 4px 0 rgb(60 72 88 / 0.05)",
        testi: "2px 2px 2px -1px rgb(60 72 88 / 0.15)",
      },

      spacing: {
        0.75: "0.1875rem",
        3.25: "0.8125rem",
      },

      maxWidth: () => ({
        1200: "71.25rem",
        992: "60rem",
        768: "45rem",
      }),

      zIndex: {
        1: "1",
        2: "2",
        3: "3",
        999: "999",
      },
    },
  },

  plugins: [
    require("@tailwindcss/forms")({
      strategy: "class", // only generate classes
    }),
    require("daisyui"),
  ],
};
