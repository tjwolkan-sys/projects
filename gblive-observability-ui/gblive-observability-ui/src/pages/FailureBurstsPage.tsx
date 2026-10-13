import { useMemo, useState } from "react";
import { Search, Filter, AlertCircle, RefreshCw, Download, Calendar } from "lucide-react";
import { FailureBurstCard } from "../components/burst/FailureBurstCard";

/**
 * Mock data – backend bağlanınca replace edeceğiz
 */
const MOCK_BURSTS = [
    {
        id: "b1",
        title: "Asterisk AMI Connection Refused",
        severity: "CRITICAL" as const,
        failureCount: 42,
        affectedCalls: 31,
        windowLabel: "Last 5 minutes",
        aiConfidence: 91,
        aiExplanation:
            "Multiple calls failed due to Asterisk AMI port being unreachable. All failures originated from node GAVXBSNTPDP01. Network connectivity or service crash detected. Recommend immediate investigation of Asterisk service status and network configuration.",
    },
    {
        id: "b2",
        title: "Redis Pub/Sub Timeout Spike",
        severity: "WARNING" as const,
        failureCount: 18,
        affectedCalls: 18,
        windowLabel: "Last 10 minutes",
        aiConfidence: 73,
        aiExplanation:
            "Redis response latency increased from 5ms to 850ms average, causing missed pub/sub events. Campaign execution was delayed but eventually recovered. Probable cause: memory pressure or network congestion. Monitor Redis memory usage.",
    },
    {
        id: "b3",
        title: "Database Connection Pool Exhaustion",
        severity: "WARNING" as const,
        failureCount: 24,
        affectedCalls: 19,
        windowLabel: "Last 15 minutes",
        aiConfidence: 85,
        aiExplanation:
            "Connection pool reached maximum capacity (100 connections). New queries waited in queue causing cascade delays. This pattern suggests either connection leak or sudden load spike from batch operations.",
    },
    {
        id: "b4",
        title: "Callback Queue Processing Delay",
        severity: "INFO" as const,
        failureCount: 12,
        affectedCalls: 12,
        windowLabel: "Last 20 minutes",
        aiConfidence: 68,
        aiExplanation:
            "Callback processing experienced delays due to worker pool saturation. No data loss occurred. Processing resumed normal speed after queue drained. Consider increasing worker count during peak hours.",
    },
];

export default function FailureBurstsPage() {
    const [search, setSearch] = useState("");
    const [severityFilter, setSeverityFilter] = useState<
        "ALL" | "CRITICAL" | "WARNING" | "INFO"
    >("ALL");

    const filteredBursts = useMemo(() => {
        return MOCK_BURSTS.filter(burst => {
            const matchesSearch =
                search === "" ||
                burst.title.toLowerCase().includes(search.toLowerCase());

            const matchesSeverity =
                severityFilter === "ALL" ||
                burst.severity === severityFilter;

            return matchesSearch && matchesSeverity;
        });
    }, [search, severityFilter]);

    // Stats
    const stats = {
        total: filteredBursts.length,
        critical: filteredBursts.filter(b => b.severity === "CRITICAL").length,
        warning: filteredBursts.filter(b => b.severity === "WARNING").length,
        info: filteredBursts.filter(b => b.severity === "INFO").length,
    };

    return (
        <div className="space-y-6">
            {/* Page Header */}
            <div className="flex items-start justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-slate-100 mb-1">
                        Failure Bursts
                    </h1>
                    <p className="text-sm text-slate-400">
                        AI-detected correlated failures across system components
                    </p>
                </div>

                <div className="flex items-center gap-3">
                    <button className="glass-hover glass rounded-xl px-4 py-2 text-sm font-medium text-slate-300 flex items-center gap-2">
                        <Calendar className="h-4 w-4" />
                        Last 24h
                    </button>
                    <button className="glass-hover glass rounded-xl px-4 py-2 text-sm font-medium text-slate-300 flex items-center gap-2">
                        <Download className="h-4 w-4" />
                        Export
                    </button>
                    <button className="glass-hover glass rounded-xl px-4 py-2 text-sm font-medium text-sky-400 flex items-center gap-2 border-sky-500/30">
                        <RefreshCw className="h-4 w-4" />
                        Refresh
                    </button>
                </div>
            </div>

            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="glass rounded-2xl p-5 border border-slate-700/50">
                    <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
                        Total Bursts
                    </div>
                    <div className="text-3xl font-bold text-slate-100">
                        {stats.total}
                    </div>
                </div>

                <div className="glass rounded-2xl p-5 border border-red-500/30 bg-red-500/5">
                    <div className="text-xs font-semibold text-red-400 uppercase tracking-wider mb-2">
                        Critical
                    </div>
                    <div className="text-3xl font-bold text-red-400">
                        {stats.critical}
                    </div>
                </div>

                <div className="glass rounded-2xl p-5 border border-amber-500/30 bg-amber-500/5">
                    <div className="text-xs font-semibold text-amber-400 uppercase tracking-wider mb-2">
                        Warning
                    </div>
                    <div className="text-3xl font-bold text-amber-400">
                        {stats.warning}
                    </div>
                </div>

                <div className="glass rounded-2xl p-5 border border-sky-500/30 bg-sky-500/5">
                    <div className="text-xs font-semibold text-sky-400 uppercase tracking-wider mb-2">
                        Info
                    </div>
                    <div className="text-3xl font-bold text-sky-400">
                        {stats.info}
                    </div>
                </div>
            </div>

            {/* Filters */}
            <div className="glass rounded-2xl border border-slate-700/50 p-5">
                <div className="flex flex-wrap items-center gap-4">
                    {/* Search */}
                    <div className="relative flex-1 min-w-[280px]">
                        <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                        <input
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            placeholder="Search by root cause, service, or error pattern..."
                            className="
                                w-full rounded-xl
                                bg-slate-800/50
                                border border-slate-700/50
                                pl-11 pr-4 py-3 text-sm
                                text-slate-100 placeholder:text-slate-500
                                focus:outline-none focus:ring-2 focus:ring-sky-500/40 focus:border-sky-500/40
                                transition-all
                            "
                        />
                    </div>

                    {/* Severity filter */}
                    <div className="flex items-center gap-3">
                        <Filter className="h-4 w-4 text-slate-400" />

                        <div className="flex gap-2">
                            {(["ALL", "CRITICAL", "WARNING", "INFO"] as const).map(sev => (
                                <button
                                    key={sev}
                                    onClick={() => setSeverityFilter(sev)}
                                    className={`
                                        px-4 py-2 rounded-lg text-xs font-semibold uppercase tracking-wider
                                        transition-all duration-200
                                        ${severityFilter === sev
                                        ? sev === "CRITICAL"
                                            ? "bg-red-500/20 text-red-400 border border-red-500/40"
                                            : sev === "WARNING"
                                                ? "bg-amber-500/20 text-amber-400 border border-amber-500/40"
                                                : sev === "INFO"
                                                    ? "bg-sky-500/20 text-sky-400 border border-sky-500/40"
                                                    : "bg-slate-700/50 text-slate-200 border border-slate-600"
                                        : "glass-hover glass text-slate-400 border-transparent"
                                    }
                                    `}
                                >
                                    {sev}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {/* Content */}
            {filteredBursts.length === 0 ? (
                /* Empty state */
                <div className="mt-20 flex flex-col items-center justify-center text-center">
                    <div className="
                        flex h-20 w-20 items-center justify-center
                        rounded-2xl glass border border-slate-700/50
                        mb-6
                    ">
                        <AlertCircle className="h-10 w-10 text-slate-400" />
                    </div>

                    <h3 className="text-xl font-bold text-slate-200 mb-2">
                        No failure bursts detected
                    </h3>
                    <p className="max-w-md text-sm text-slate-400 leading-relaxed">
                        {search || severityFilter !== "ALL"
                            ? "No failures match your current filters. Try adjusting your search criteria."
                            : "All systems are operating normally. The AI monitoring system has not detected any correlated failure patterns."
                        }
                    </p>

                    {(search || severityFilter !== "ALL") && (
                        <button
                            onClick={() => {
                                setSearch("");
                                setSeverityFilter("ALL");
                            }}
                            className="mt-6 glass-hover glass rounded-xl px-6 py-2.5 text-sm font-medium text-slate-300"
                        >
                            Clear Filters
                        </button>
                    )}
                </div>
            ) : (
                /* Grid */
                <div className="grid gap-6 grid-cols-1 lg:grid-cols-2 2xl:grid-cols-3">
                    {filteredBursts.map(burst => (
                        <FailureBurstCard
                            key={burst.id}
                            title={burst.title}
                            severity={burst.severity}
                            failureCount={burst.failureCount}
                            affectedCalls={burst.affectedCalls}
                            windowLabel={burst.windowLabel}
                            aiConfidence={burst.aiConfidence}
                            aiExplanation={burst.aiExplanation}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}