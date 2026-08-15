import React from "react";
import ReactDOM from "react-dom/client";
import { AdminLiteApp } from "./app/AdminLiteApp";
import "./styles/global.css";

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <AdminLiteApp />
  </React.StrictMode>
);
