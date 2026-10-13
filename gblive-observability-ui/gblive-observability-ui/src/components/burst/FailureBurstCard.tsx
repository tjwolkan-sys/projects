import { useState } from "react";
import {
    AlertTriangle,
    Zap,
    ChevronDown,
    ChevronUp,
    Phone,
    Clock,
    TrendingUp,
    Sparkles,
} from "lucide-react";

export type FailureSeverity = "CRITICAL" | "WARNING" | "INFO";

export type FailureBurstCardProps = {
    title: string;
    severity: FailureSeverity;
    failureCount: number;
    affectedCalls: number;
    windowLabel: string;
    aiConfidence: number; // 0–100
    aiExplanation?: string;
};

const SEVERITY_CONFIG: Record<FailureSeverity, {
    borderColor: string;
    bgColor: string;
    textColor: string;
    glowColor: string;
    icon: React.ReactNode;
}> = {
    CRITICAL: {
        borderColor: "border-red-500/40",
        bgColor: "bg-red-500/5",
        textColor: "text-red-400",
        glowColor: "shadow-red-500/20",
        icon: <AlertTriangle className="h-5 w-5" />,
    },
    WARNING: {
        borderColor: "border-amber-500/40",
        bgColor: "bg-amber-500/5",
        textColor: "text-amber-400",
        glowColor: "shadow-amber-500/20",
        icon: <Zap className="h-5 w-5" />,
    },
    INFO: {
        borderColor: "border-sky-500/40",
        bgColor: "bg-sky-500/5",
        textColor: "text-sky-400",
        glowColor: "shadow-sky-500/20",
        icon: <TrendingUp className="h-5 w-5" />,
    },
};

export function FailureBurstCard(props: FailureBurstCardProps) {
    const {
        title,
        severity,
        failureCount,
        affectedCalls,
        windowLabel,
        aiConfidence,
        aiExplanation,
    } = props;

    const [expanded, setExpanded] = useState(false);
    const config = SEVERITY_CONFIG[severity];

    return (
        <div
            className={`
                relative rounded-2xl border backdrop-blur-xl
                bg-slate-900/40
                ${config.borderColor}
                shadow-xl ${config.glowColor}
                transition-all duration-300
                hover:shadow-2xl hover:scale-[1.02]
                hover:border-opacity-60
                group
            `}
        >
            {/* Glow effect on hover */}
            <div className={`
                absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100
                bg-gradient-to-br ${config.bgColor}
                transition-opacity duration-300
                pointer-events-none
            `} />

            {/* Content */}
            <div className="relative">
                {/* Header */}
                <div className="p-6">
                    <div className="flex items-start justify-between gap-4">
                        {/* Left: Icon + Title */}
                        <div className="flex gap-4 flex-1 min-w-0">
                            <div className={`
                                flex-shrink-0 h-12 w-12 rounded-xl
                                ${config.bgColor} ${config.borderColor} border
                                flex items-center justify-center
                                ${config.textColor}
                                transition-transform duration-200
                                group-hover:scale-110
                            `}>
                                {config.icon}
                            </div>

                            <div className="flex-1 min-w-0">
                                <h3 className="text-base font-bold text-slate-100 mb-2 leading-tight">
                                    {title}
                                </h3>

                                <div className="flex flex-wrap items-center gap-4 text-xs text-slate-400">
                                    <div className="flex items-center gap-1.5">
                                        <AlertTriangle className="h-3.5 w-3.5" />
                                        <span className="font-semibold text-slate-300">{failureCount}</span>
                                        <span>failures</span>
                                    </div>

                                    <div className="flex items-center gap-1.5">
                                        <Phone className="h-3.5 w-3.5" />
                                        <span className="font-semibold text-slate-300">{affectedCalls}</span>
                                        <span>calls</span>
                                    </div>

                                    <div className="flex items-center gap-1.5">
                                        <Clock className="h-3.5 w-3.5" />
                                        <span>{windowLabel}</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Right: Severity badge */}
                        <div className="flex flex-col items-end gap-3">
                            <span className={`
                                px-3 py-1.5 rounded-lg text-xs font-bold uppercase tracking-wider
                                ${config.bgColor} ${config.textColor} ${config.borderColor} border
                                backdrop-blur-sm
                            `}>
                                {severity}
                            </span>
                        </div>
                    </div>

                    {/* AI Confidence Bar */}
                    <div className="mt-6 glass rounded-xl p-4 border border-slate-700/50">
                        <div className="flex items-center justify-between mb-2">
                            <div className="flex items-center gap-2 text-xs font-medium text-slate-300">
                                <Sparkles className="h-3.5 w-3.5 text-purple-400" />
                                AI Root Cause Confidence
                            </div>
                            <span className="text-sm font-bold text-slate-100">
                                {aiConfidence}%
                            </span>
                        </div>

                        {/* Progress bar */}
                        <div className="relative h-2 bg-slate-800/50 rounded-full overflow-hidden">
                            <div
                                className={`
                                    absolute inset-y-0 left-0 rounded-full
                                    transition-all duration-500
                                    ${aiConfidence >= 80
                                    ? 'bg-gradient-to-r from-emerald-500 to-emerald-400'
                                    : aiConfidence >= 60
                                        ? 'bg-gradient-to-r from-amber-500 to-amber-400'
                                        : 'bg-gradient-to-r from-red-500 to-red-400'
                                }
                                `}
                                style={{ width: `${aiConfidence}%` }}
                            >
                                <div className="absolute inset-0 animate-shimmer" />
                            </div>
                        </div>
                    </div>
                </div>

                {/* Expand button */}
                {aiExplanation && (
                    <button
                        onClick={() => setExpanded(v => !v)}
                        className={`
                            w-full flex items-center justify-between
                            px-6 py-3
                            border-t ${config.borderColor} border-opacity-30
                            text-sm font-medium text-slate-300
                            hover:bg-slate-800/30
                            transition-all duration-200
                        `}
                    >
                        <span className="flex items-center gap-2">
                            <Sparkles className="h-4 w-4 text-purple-400" />
                            {expanded ? 'Hide' : 'View'} AI Analysis
                        </span>

                        {expanded ? (
                            <ChevronUp className="h-4 w-4" />
                        ) : (
                            <ChevronDown className="h-4 w-4" />
                        )}
                    </button>
                )}

                {/* Expanded AI section */}
                {expanded && aiExplanation && (
                    <div className="px-6 py-5 glass border-t border-slate-700/30">
                        <div className="flex items-start gap-3">
                            <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-purple-500/10 border border-purple-500/30 flex items-center justify-center">
                                <Sparkles className="h-4 w-4 text-purple-400" />
                            </div>

                            <div className="flex-1">
                                <div className="text-xs font-bold uppercase tracking-wider text-purple-400 mb-2">
                                    Root Cause Analysis
                                </div>

                                <p className="text-sm text-slate-300 leading-relaxed">
                                    {aiExplanation}
                                </p>

                                {/* Action suggestions (future) */}
                                <div className="mt-4 flex gap-2">
                                    <button className="text-xs px-3 py-1.5 rounded-lg glass-hover glass text-slate-300 hover:text-slate-100">
                                        View Details →
                                    </button>
                                    <button className="text-xs px-3 py-1.5 rounded-lg glass-hover glass text-slate-300 hover:text-slate-100">
                                        Similar Incidents
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}