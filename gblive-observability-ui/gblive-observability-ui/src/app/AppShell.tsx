import type { ReactNode } from "react";
import { Sidebar } from "../components/layout/Sidebar";
import { Header } from "../components/layout/Header";

export function AppShell({ children }: { children: ReactNode }) {
    return (
        <div className="flex h-screen bg-slate-950 overflow-hidden">
            <Sidebar />

            <div className="flex-1 flex flex-col overflow-hidden">
                <Header />

                <main className="flex-1 overflow-y-auto p-8 bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
                    <div className="max-w-[1600px] mx-auto">
                        {children}
                    </div>
                </main>
            </div>
        </div>
    );
}