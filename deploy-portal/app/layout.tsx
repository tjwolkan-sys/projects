import type { Metadata } from 'next';
import Link from 'next/link';
import {
  LayoutGrid,
  PlusCircle,
  History,
  Folder,
  UserCheck,
  BarChart3,
  Settings,
  Bell,
  ChevronDown,
  Rocket,
} from 'lucide-react';
import './globals.css';

export const metadata: Metadata = { title: 'Deploy Portal' };

const NAV = [
  { label: 'Dashboard', href: '/', icon: LayoutGrid },
  { label: 'Yeni Request', href: '/', icon: PlusCircle, active: true },
  { label: 'Requestlerim', href: '/requests', icon: History },
  { label: 'Tümü', href: '/requests', icon: Folder },
  { label: 'Onay Bekleyenler', href: '/requests', icon: UserCheck },
];

const NAV_SOON = [
  { label: 'Raporlar', icon: BarChart3 },
  { label: 'Ayarlar', icon: Settings },
];

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="tr">
      <body className="antialiased">
        <div className="flex min-h-screen">
          {/* ============ SIDEBAR ============ */}
          <aside className="relative flex w-60 shrink-0 flex-col border-r border-black/5 bg-white">
            <Link href="/" className="flex items-center gap-2.5 px-5 py-5">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#EEEDFB] text-[#5850C7]">
                <Rocket size={18} />
              </span>
              <span className="text-[16px] font-semibold text-[#1e2030]">Deploy Portal</span>
            </Link>

            <nav className="mt-2 flex flex-col gap-1 px-3">
              {NAV.map((item) => {
                const Icon = item.icon;
                return (
                  <Link
                    key={item.label}
                    href={item.href}
                    className={`flex items-center gap-3 rounded-xl px-4 py-2.5 text-[13.5px] transition-colors ${
                      item.active
                        ? 'bg-[#5850C7] font-medium text-white shadow-sm shadow-[#5850C7]/30'
                        : 'text-[#4a4d5e] hover:bg-black/[0.04]'
                    }`}
                  >
                    <Icon size={17} strokeWidth={item.active ? 2.2 : 1.8} />
                    {item.label}
                  </Link>
                );
              })}
              {NAV_SOON.map((item) => {
                const Icon = item.icon;
                return (
                  <span
                    key={item.label}
                    title="Yakında"
                    className="flex cursor-not-allowed items-center gap-3 rounded-xl px-4 py-2.5 text-[13.5px] text-[#b3b6c4]"
                  >
                    <Icon size={17} strokeWidth={1.8} />
                    {item.label}
                  </span>
                );
              })}
            </nav>

            {/* roket illüstrasyonu */}
            <div className="pointer-events-none mt-auto select-none px-4 pb-0">
              <svg viewBox="0 0 220 260" fill="none" className="w-full">
                {/* bulutlar */}
                <ellipse cx="45" cy="70" rx="38" ry="14" fill="#EEF0F9" />
                <ellipse cx="175" cy="110" rx="42" ry="15" fill="#EEF0F9" />
                <ellipse cx="30" cy="235" rx="55" ry="20" fill="#E4E6F5" />
                <ellipse cx="120" cy="250" rx="70" ry="24" fill="#EAECF8" />
                <ellipse cx="200" cy="238" rx="48" ry="19" fill="#E4E6F5" />
                {/* kule */}
                <rect x="138" y="95" width="10" height="130" fill="#C9CCE3" />
                <rect x="138" y="105" width="34" height="5" fill="#C9CCE3" />
                <rect x="138" y="130" width="34" height="5" fill="#C9CCE3" />
                <rect x="138" y="155" width="34" height="5" fill="#C9CCE3" />
                <rect x="138" y="180" width="34" height="5" fill="#C9CCE3" />
                {/* gövde */}
                <path d="M100 60 C100 38 110 25 110 25 C110 25 120 38 120 60 L120 150 L100 150 Z" fill="#fff" stroke="#B9BDDD" strokeWidth="2.5" />
                {/* burun */}
                <path d="M100 60 C100 38 110 25 110 25 C110 25 120 38 120 60 L120 68 L100 68 Z" fill="#8B84E0" />
                {/* pencere */}
                <circle cx="110" cy="88" r="7" fill="#EEEDFB" stroke="#8B84E0" strokeWidth="2.5" />
                {/* kanatlar */}
                <path d="M100 118 L82 150 L100 150 Z" fill="#6C63D2" />
                <path d="M120 118 L138 150 L120 150 Z" fill="#6C63D2" />
                {/* yakıt tankları */}
                <rect x="88" y="100" width="10" height="50" rx="5" fill="#D6D8EE" />
                <rect x="122" y="100" width="10" height="50" rx="5" fill="#D6D8EE" />
                {/* alev */}
                <path d="M104 150 C104 162 110 170 110 170 C110 170 116 162 116 150 Z" fill="#F5B458" />
                {/* platform */}
                <rect x="70" y="168" width="80" height="7" rx="3" fill="#C9CCE3" />
                <rect x="78" y="175" width="8" height="45" fill="#D6D8EE" />
                <rect x="134" y="175" width="8" height="45" fill="#D6D8EE" />
              </svg>
            </div>
          </aside>

          {/* ============ SAĞ ============ */}
          <div className="flex min-w-0 flex-1 flex-col">
            <header className="flex items-center justify-end gap-4 px-8 pt-5">
              <button
                type="button"
                title="Bildirimler (yakında)"
                className="relative text-[#8a8ea0] transition-colors hover:text-[#5850C7]"
              >
                <Bell size={19} />
                <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full border-2 border-[#f5f6fb] bg-[#5850C7]" />
              </button>
              <button type="button" className="flex items-center gap-2">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#5850C7] text-xs font-medium text-white">
                  V
                </span>
                <span className="text-[13px] font-medium text-[#1e2030]">Volkan</span>
                <ChevronDown size={15} className="text-[#8a8ea0]" />
              </button>
            </header>

            <main className="min-w-0 flex-1 px-8 pb-10 pt-2">{children}</main>
          </div>
        </div>
      </body>
    </html>
  );
}