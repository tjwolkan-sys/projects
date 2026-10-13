import { Routes, Route, Navigate } from "react-router-dom";
import { AppShell } from "./app/AppShell";
import FailureBurstsPage from "./pages/FailureBurstsPage.tsx";

function Placeholder({ title }: { title: string }) {
    return (
        <div className="text-2xl font-semibold text-slate-800">
            {title}
        </div>
    );
}

export default function App() {
    return (
        <AppShell>
            <Routes>
                <Route path="/" element={<Navigate to="/bursts" replace />} />
                <Route path="/incidents" element={<Placeholder title="Incidents" />} />
                <Route path="/bursts" element={<FailureBurstsPage />} />
                <Route path="/timelines" element={<Placeholder title="Call Timelines" />} />
                <Route path="/insights" element={<Placeholder title="LLM Insights" />} />
            </Routes>
        </AppShell>
    );
}
