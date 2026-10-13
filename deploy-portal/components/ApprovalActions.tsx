'use client';

import { approveByToken, rejectByToken } from '@/app/actions';
import { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import type { Assignee } from '@prisma/client';

type Mode = 'idle' | 'approving' | 'rejecting';

export function ApprovalActions({ token, assignees }: { token: string; assignees: Assignee[] }) {
  const [mode, setMode] = useState<Mode>('idle');
  const [assigneeId, setAssigneeId] = useState<number>(0);
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();
  const router = useRouter();

  const run = (fn: () => Promise<{ error?: string } | { ok: boolean }>) =>
    start(async () => {
      setError(null);
      const res = await fn();
      if ('error' in res && res.error) setError(res.error);
      else router.refresh();
    });

  return (
    <div className="bg-white rounded-xl shadow-sm p-6 space-y-4">
      {mode === 'idle' && (
        <div className="flex gap-3">
          <button
            disabled={pending}
            onClick={() => setMode('approving')}
            className="bg-green-600 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-50"
          >
            ✓ Onayla
          </button>
          <button
            disabled={pending}
            onClick={() => setMode('rejecting')}
            className="bg-red-50 text-red-700 border border-red-200 px-6 py-2 rounded-lg text-sm font-medium hover:bg-red-100 disabled:opacity-50"
          >
            ✗ Reddet
          </button>
        </div>
      )}

      {mode === 'approving' && (
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium mb-1">Deploy edecek kişi</label>
            <select
              value={assigneeId || ''}
              onChange={(e) => setAssigneeId(Number(e.target.value))}
              className="w-full border rounded-lg px-3 py-2 text-sm"
            >
              <option value="">Seçiniz...</option>
              {assignees.map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
          </div>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Atama notu (opsiyonel)..."
            rows={2}
            className="w-full border rounded-lg px-3 py-2 text-sm"
          />
          <div className="flex gap-3">
            <button
              disabled={pending}
              onClick={() => run(() => approveByToken(token, assigneeId, note))}
              className="bg-green-600 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-50"
            >
              Onayla ve Ata
            </button>
            <button onClick={() => setMode('idle')} className="text-sm text-gray-500 hover:underline">Vazgeç</button>
          </div>
        </div>
      )}

      {mode === 'rejecting' && (
        <div className="space-y-3">
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Reject sebebi (zorunlu)..."
            rows={3}
            className="w-full border rounded-lg px-3 py-2 text-sm"
          />
          <div className="flex gap-3">
            <button
              disabled={pending}
              onClick={() => run(() => rejectByToken(token, reason))}
              className="bg-red-600 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-50"
            >
              Reddet
            </button>
            <button onClick={() => setMode('idle')} className="text-sm text-gray-500 hover:underline">Vazgeç</button>
          </div>
        </div>
      )}

      {error && <div className="text-sm text-red-600">{error}</div>}
    </div>
  );
}