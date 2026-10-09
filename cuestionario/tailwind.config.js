// Tokens de diseño de Prestaya, extraídos de los exports de Google Stitch.
// Las tres pantallas en designs/stitch/ traen esta misma configuración inline;
// esta es la fuente única para cuando se arme el frontend.
module.exports = {
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        // Marca
        "primary": "#006b5e",
        "on-primary": "#ffffff",
        "primary-container": "#00d5be",
        "on-primary-container": "#00574d",
        "primary-fixed": "#54fbe3",
        "primary-fixed-dim": "#23dec7",
        "on-primary-fixed": "#00201c",
        "on-primary-fixed-variant": "#005047",
        "inverse-primary": "#23dec7",
        "surface-tint": "#006b5e",

        // Acento positivo (éxito, ahorro, ventajas)
        "tertiary": "#006c4a",
        "on-tertiary": "#ffffff",
        "tertiary-container": "#37d69b",
        "on-tertiary-container": "#00583c",
        "tertiary-fixed": "#67fcbe",
        "tertiary-fixed-dim": "#44dfa3",
        "on-tertiary-fixed": "#002114",
        "on-tertiary-fixed-variant": "#005237",

        // Alerta (mora, rechazos)
        "secondary": "#b60f3b",
        "on-secondary": "#ffffff",
        "secondary-container": "#da3051",
        "on-secondary-container": "#fffbff",
        "secondary-fixed": "#ffdadb",
        "secondary-fixed-dim": "#ffb2b7",
        "on-secondary-fixed": "#40000e",
        "on-secondary-fixed-variant": "#91002b",

        // Error
        "error": "#ba1a1a",
        "on-error": "#ffffff",
        "error-container": "#ffdad6",
        "on-error-container": "#93000a",

        // Superficies y texto
        "background": "#f7f9ff",
        "on-background": "#171c21",
        "surface": "#f7f9ff",
        "surface-bright": "#f7f9ff",
        "surface-dim": "#d6dae1",
        "surface-variant": "#dee3ea",
        "surface-container-lowest": "#ffffff",
        "surface-container-low": "#f0f4fb",
        "surface-container": "#eaeef5",
        "surface-container-high": "#e4e8f0",
        "surface-container-highest": "#dee3ea",
        "on-surface": "#171c21",
        "on-surface-variant": "#3b4a46",
        "inverse-surface": "#2c3136",
        "inverse-on-surface": "#edf1f8",
        "outline": "#6b7a76",
        "outline-variant": "#bacac5",
      },
      borderRadius: {
        DEFAULT: "0.25rem",
        lg: "0.5rem",
        xl: "0.75rem",
        full: "9999px",
      },
      spacing: {
        "space-xs": "0.25rem",
        "space-sm": "0.5rem",
        "space-md": "0.75rem",
        "space-lg": "1rem",
        "space-xl": "1.5rem",
        "space-2xl": "2rem",
        "gutter-sm": "0.75rem",
        "gutter": "1rem",
        "margin": "1rem",
        "margin-tablet": "1.5rem",
        "margin-desktop": "2rem",
      },
      fontFamily: {
        // Stitch declara una familia por cada estilo; todas son la misma.
        sans: ["Plus Jakarta Sans", "system-ui", "sans-serif"],
      },
      fontSize: {
        "display-lg": ["36px", { lineHeight: "44px", letterSpacing: "-0.02em", fontWeight: "700" }],
        "headline-lg": ["28px", { lineHeight: "36px", letterSpacing: "-0.015em", fontWeight: "700" }],
        "headline-lg-mobile": ["24px", { lineHeight: "32px", letterSpacing: "-0.01em", fontWeight: "700" }],
        "headline-md": ["20px", { lineHeight: "28px", letterSpacing: "-0.01em", fontWeight: "600" }],
        "title-lg": ["17px", { lineHeight: "24px", fontWeight: "600" }],
        "title-md": ["15px", { lineHeight: "20px", fontWeight: "600" }],
        "body-lg": ["15px", { lineHeight: "22px", fontWeight: "400" }],
        "body-md": ["13px", { lineHeight: "18px", fontWeight: "400" }],
        "label-lg": ["13px", { lineHeight: "18px", letterSpacing: "0.01em", fontWeight: "600" }],
        "label-md": ["12px", { lineHeight: "16px", fontWeight: "500" }],
        "label-sm": ["11px", { lineHeight: "14px", fontWeight: "500" }],
        "numeric-ticker": ["16px", { lineHeight: "20px", letterSpacing: "-0.01em", fontWeight: "700" }],
        "numeric-badge": ["12px", { lineHeight: "16px", fontWeight: "600" }],
      },
    },
  },
};
