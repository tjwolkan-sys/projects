import { Search, Bell, Settings, Activity } from "lucide-react";

export function Header() {
    return (
        <header className="h-16 glass border-b border-slate-800/80 flex items-center justify-between px-6 sticky top-0 z-50">
            {/* Left - System Status */}
            <div className="flex items-center gap-6">
                <div className="flex items-center gap-2">
                    <div className="relative">
                        <Activity className="h-5 w-5 text-sky-400" />
                        <div className="absolute -top-1 -right-1 h-2 w-2 bg-emerald-400 rounded-full animate-pulse" />
                    </div>
                    <div>
                        <div className="text-xs text-slate-400">System Status</div>
                        <div className="text-sm font-semibold text-emerald-400">Operational</div>
                    </div>
                </div>

                <div className="h-8 w-px bg-slate-700/50" />

                <div className="flex items-center gap-4 text-xs">
                    <div className="flex items-center gap-1.5">
                        <div className="h-2 w-2 rounded-full bg-emerald-400" />
                        <span className="text-slate-400">Active Calls:</span>
                        <span className="font-mono font-semibold text-slate-200">342</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                        <div className="h-2 w-2 rounded-full bg-amber-400" />
                        <span className="text-slate-400">Warnings:</span>
                        <span className="font-mono font-semibold text-slate-200">3</span>
                    </div>
                </div>
            </div>

            {/* Right - Actions */}
            <div className="flex items-center gap-3">
                {/* Search */}
                <button className="glass-hover glass rounded-lg p-2 text-slate-400 hover:text-slate-200">
                    <Search className="h-4 w-4" />
                </button>

                {/* Notifications with badge */}
                <button className="glass-hover glass rounded-lg p-2 text-slate-400 hover:text-slate-200 relative">
                    <Bell className="h-4 w-4" />
                    <span className="absolute -top-1 -right-1 h-4 w-4 bg-red-500 rounded-full text-[10px] font-bold text-white flex items-center justify-center">
                        2
                    </span>
                </button>

                {/* Settings */}
                <button className="glass-hover glass rounded-lg p-2 text-slate-400 hover:text-slate-200">
                    <Settings className="h-4 w-4" />
                </button>

                <div className="h-8 w-px bg-slate-700/50 mx-2" />

                {/* User */}
                <div className="flex items-center gap-3">
                    <div className="text-right">
                        <div className="text-sm font-medium text-slate-200">Admin User</div>
                        <div className="text-xs text-slate-500">System Administrator</div>
                    </div>
                    <div className="h-9 w-9 rounded-lg glass flex items-center justify-center font-semibold text-sky-400">
                        A
                    </div>
                </div>
            </div>
        </header>
    );
}