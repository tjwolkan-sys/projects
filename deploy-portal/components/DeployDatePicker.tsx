'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { DEPLOY_DAYS } from '@/lib/deploy-rules';

const WEEKDAYS = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt', 'Paz'];

function toKey(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function DeployDatePicker({
  value,
  onChange,
  restrictDays,
}: {
  value: string; // 'yyyy-MM-dd' | ''
  onChange: (v: string) => void;
  restrictDays: boolean;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const today = useMemo(() => {
    const t = new Date();
    t.setHours(0, 0, 0, 0);
    return t;
  }, []);

  const selected = value ? new Date(value + 'T00:00:00') : undefined;
  const [viewYear, setViewYear] = useState((selected ?? today).getFullYear());
  const [viewMonth, setViewMonth] = useState((selected ?? today).getMonth());

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, []);

  // popup açılınca seçili/bugünkü aya git
  useEffect(() => {
    if (open) {
      const base = selected ?? today;
      setViewYear(base.getFullYear());
      setViewMonth(base.getMonth());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function isDisabled(d: Date) {
    if (d < today) return true;
    if (restrictDays && !DEPLOY_DAYS.includes(d.getDay())) return true;
    return false;
  }

  // ay grid'i: Pazartesi başlangıçlı 6 hafta
  const weeks = useMemo(() => {
    const first = new Date(viewYear, viewMonth, 1);
    const start = new Date(first);
    const offset = (first.getDay() + 6) % 7; // Pzt=0 ... Paz=6
    start.setDate(first.getDate() - offset);

    const rows: Date[][] = [];
    const cursor = new Date(start);
    for (let w = 0; w < 6; w++) {
      const row: Date[] = [];
      for (let i = 0; i < 7; i++) {
        row.push(new Date(cursor));
        cursor.setDate(cursor.getDate() + 1);
      }
      rows.push(row);
    }
    return rows;
  }, [viewYear, viewMonth]);

  function prevMonth() {
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear((y) => y - 1);
    } else setViewMonth((m) => m - 1);
  }
  function nextMonth() {
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear((y) => y + 1);
    } else setViewMonth((m) => m + 1);
  }

  const monthLabel = new Date(viewYear, viewMonth, 1).toLocaleDateString('tr-TR', {
    month: 'long',
    year: 'numeric',
  });

  const displayLabel = selected
    ? selected.toLocaleDateString('tr-TR', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        weekday: 'long',
      })
    : 'Tarih seçiniz';

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-2.5 rounded-xl border border-black/10 bg-white px-3.5 py-2.5 text-left text-[13px] outline-none transition-colors focus:border-[#5850C7]/60 focus:ring-2 focus:ring-[#5850C7]/10"
      >
        <Calendar size={15} className="shrink-0 text-[#C98A1B]" />
        <span className={value ? 'text-[#1e2030]' : 'text-[#b3b6c4]'}>{displayLabel}</span>
      </button>

      {open && (
        <div className="absolute z-30 mt-2 w-[280px] rounded-2xl border border-black/8 bg-white p-3 shadow-xl shadow-black/10">
          {/* ay navigasyonu */}
          <div className="mb-2 flex items-center justify-between px-1">
            <button
              type="button"
              onClick={prevMonth}
              className="flex h-7 w-7 items-center justify-center rounded-lg text-[#8a8ea0] transition-colors hover:bg-black/5 hover:text-[#1e2030]"
            >
              <ChevronLeft size={16} />
            </button>
            <span className="text-[13px] font-medium capitalize text-[#1e2030]">{monthLabel}</span>
            <button
              type="button"
              onClick={nextMonth}
              className="flex h-7 w-7 items-center justify-center rounded-lg text-[#8a8ea0] transition-colors hover:bg-black/5 hover:text-[#1e2030]"
            >
              <ChevronRight size={16} />
            </button>
          </div>

          {/* haftanın günleri */}
          <div className="grid grid-cols-7 gap-0.5 px-1">
            {WEEKDAYS.map((d, i) => (
              <div
                key={d}
                className={`py-1 text-center text-[10px] font-medium ${
                  restrictDays && !DEPLOY_DAYS.includes((i + 1) % 7)
                    ? 'text-[#c3c5d3]'
                    : 'text-[#5850C7]'
                }`}
              >
                {d}
              </div>
            ))}
          </div>

          {/* günler */}
          <div className="grid grid-cols-7 gap-0.5 px-1">
            {weeks.flat().map((d) => {
              const inMonth = d.getMonth() === viewMonth;
              const key = toKey(d);
              const isSelected = value === key;
              const isToday = d.getTime() === today.getTime();
              const dis = isDisabled(d);

              return (
                <button
                  key={key}
                  type="button"
                  disabled={dis}
                  onClick={() => {
                    onChange(key);
                    setOpen(false);
                  }}
                  className={[
                    'flex h-8 w-8 items-center justify-center rounded-full text-[12px] transition-colors',
                    !inMonth ? 'text-[#d5d7e2]' : '',
                    dis
                      ? 'cursor-not-allowed text-[#d5d7e2]'
                      : 'cursor-pointer text-[#1e2030] hover:bg-[#EEEDFB]',
                    isSelected ? '!bg-[#5850C7] font-medium !text-white' : '',
                    isToday && !isSelected ? 'font-bold text-[#5850C7]' : '',
                  ].join(' ')}
                >
                  {d.getDate()}
                </button>
              );
            })}
          </div>

          <p className="mt-2 border-t border-black/5 px-2 pt-2 text-[10.5px] text-[#8a8ea0]">
            {restrictDays ? 'Normal deployment: yalnızca Salı ve Perşembe' : '🚨 Acil CAB — tüm günler açık'}
          </p>
        </div>
      )}
    </div>
  );
}