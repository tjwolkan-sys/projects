import { SidebarItem } from "../SidebarItem.tsx";
import { LayoutDashboard, Activity, Clock, Sparkles, BarChart3, Settings } from "lucide-react";

export function Sidebar() {
    return (
        <aside className="w-64 glass border-r border-slate-800/80 flex flex-col">
            {/* Logo */}
            <div className="h-16 flex items-center px-6 border-b border-slate-800/80">
                <div className="flex items-center gap-2.5">
                    <div className="h-8 w-8 rounded-lg bg-gradient-to-br from-sky-500 to-blue-600 flex items-center justify-center shadow-lg shadow-sky-500/20">
                        <Activity className="h-4 w-4 text-white" />
                    </div>
                    <div>
                        <div className="text-base font-bold text-slate-100 tracking-tight">
                            GBLive
                        </div>
                        <div className="text-[10px] text-slate-500 uppercase tracking-wider">
                            Observability
                        </div>
                    </div>
                </div>
            </div>

            {/* Nav */}
            <nav className="flex-1 px-3 py-6 space-y-1.5 text-sm">
                <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider px-3 mb-3">
                    Monitoring
                </div>

                <SidebarItem
                    label="Dashboard"
                    icon={<LayoutDashboard className="h-4 w-4" />}
                    path="/dashboard"
                />
                <SidebarItem
                    label="Failure Bursts"
                    icon={<Activity className="h-4 w-4" />}
                    path="/bursts"
                    badge={3}
                />
                <SidebarItem
                    label="Call Timelines"
                    icon={<Clock className="h-4 w-4" />}
                    path="/timelines"
                />

                <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider px-3 mb-3 mt-6">
                    Intelligence
                </div>

                <SidebarItem
                    label="AI Insights"
                    icon={<Sparkles className="h-4 w-4" />}
                    path="/insights"
                />
                <SidebarItem
                    label="Analytics"
                    icon={<BarChart3 className="h-4 w-4" />}
                    path="/analytics"
                />

                <div className="pt-6" />

                <SidebarItem
                    label="Settings"
                    icon={<Settings className="h-4 w-4" />}
                    path="/settings"
                />
            </nav>

            {/* Bottom Card - System Health */}
            <div className="m-3 mb-4 glass rounded-xl p-4 border border-emerald-500/20 bg-emerald-500/5">
                <div className="flex items-center gap-2 mb-2">
                    <div className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                    <span className="text-xs font-semibold text-emerald-400">System Healthy</span>
                </div>
                <div className="text-xs text-slate-400">
                    All services operational
                </div>
                <div className="mt-3 flex items-center justify-between text-xs">
                    <span className="text-slate-500">Uptime</span>
                    <span className="font-mono font-semibold text-slate-300">99.98%</span>
                </div>
            </div>
        </aside>
    );
}