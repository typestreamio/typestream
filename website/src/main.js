// Main entry point for Vite
// Import all CSS dependencies

// Third-party CSS from node_modules
import "@iconscout/unicons/css/line.css";
import "@mdi/font/css/materialdesignicons.min.css";
import "tobii/dist/css/tobii.css";
import "tiny-slider/dist/tiny-slider.css";

// Main Tailwind CSS (compiled from SCSS)
import "./assets/scss/tailwind.scss";

// Import JS libraries and make them globally available
import feather from "feather-icons";
import { tns } from "tiny-slider";
import Tobii from "tobii";

// Make libraries available globally for traditional scripts
window.feather = feather;
window.tns = tns;
window.Tobii = Tobii;
