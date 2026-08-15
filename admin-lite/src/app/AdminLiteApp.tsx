import { HashRouter, Navigate, Route, Routes } from "react-router-dom";
import { LiteApp } from "../lite/app/LiteApp";

export function AdminLiteApp() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/lite/*" element={<LiteApp />} />
        <Route path="*" element={<Navigate to="/lite" replace />} />
      </Routes>
    </HashRouter>
  );
}
