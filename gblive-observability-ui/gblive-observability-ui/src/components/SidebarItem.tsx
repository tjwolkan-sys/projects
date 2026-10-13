import { useLocation, useNavigate } from "react-router-dom";
import type { ReactNode } from "react";

export function SidebarItem({
                                label,
                                icon,
                                path,
                                badge
                            }: {
    label: string;
    icon?: ReactNode;
    path?: string;
    badge?: number;
}) {
    const location = useLocation();
    const navigate = useNavigate();

    const isActive = path && location.pathname === path;

    const handleClick = () => {
        if (path) {
            navigate(path);
        }
    };

    return (
        <div
            onClick={handleClick}
            className={`
                group relative flex items-center gap-3 px-3 py-2.5 rounded-lg
                cursor-pointer transition-all duration-200
                ${isActive
                ? 'bg-sky-500/10 text-sky-400 font-medium'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }
            `}
        >
            {/* Active indicator */}
            {isActive && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-sky-500 rounded-r-full" />
            )}

            {/* Icon */}
            {icon && (
                <div className={`
                    transition-transform duration-200
                    ${isActive ? 'scale-110' : 'group-hover:scale-105'}
                `}>
                    {icon}
                </div>
            )}

            {/* Label */}
            <span className="flex-1">
                {label}
            </span>

            {/* Badge */}
            {badge && badge > 0 && (
                <span className="
                    h-5 min-w-[20px] px-1.5 
                    bg-red-500/20 text-red-400 
                    border border-red-500/30
                    rounded-full 
                    text-[10px] font-bold 
                    flex items-center justify-center
                ">
                    {badge}
                </span>
            )}

            {/* Hover glow effect */}
            {!isActive && (
                <div className="
                    absolute inset-0 rounded-lg
                    opacity-0 group-hover:opacity-100
                    bg-gradient-to-r from-transparent via-sky-500/5 to-transparent
                    transition-opacity duration-200
                " />
            )}
        </div>
    );
}