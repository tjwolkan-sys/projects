'use client';

import { updateStatus } from '@/app/actions';
import { useState, useTransition } from 'react';

const STATUSES = ['PENDING', 'APPROVED', 'IN_PROGRESS', 'REJECTED', 'TIMED_OUT', 'COMPLETED'];

export function StatusManager({ id, current }: { id: number; current: string }) {
  const [status, setStatus] = useState(current);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  return (
    <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-sm space-y-3">
      <div className="font-semibold text-amber-800">Status Yönetimi (geçici — faz 2'de devops rolüne taşınacak)</div>
      <div className="flex gap-3 items-center flex-wrap">
        <select value={status} onChange={(e) => setStatus(e.target.value)} className="border rounded-lg px-3 py-1.5 text-sm">
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        {status === 'REJECTED' && (
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Reject sebebi..."
            className="border rounded-lg px-3 py-1.5 text-sm flex-1 min-w-48"
          />
        )}
        <button
          disabled={pending}
          onClick={() =>
            start(async () => {
              setError(null);
              const res = await updateStatus(id, status, reason);
              if (res?.error) setError(res.error);
            })
          }
          className="bg-amber-600 text-white px-4 py-1.5 rounded-lg hover:bg-amber-700 disabled:opacity-50"
        >
          Güncelle
        </button>
      </div>
      {error && <div className="text-red-600">{error}</div>}
    </div>
  );
}