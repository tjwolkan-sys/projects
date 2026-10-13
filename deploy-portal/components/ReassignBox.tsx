'use client';

import { reassignRequest } from '@/app/actions';
import { useState, useTransition } from 'react';
import type { Assignee } from '@prisma/client';

export function ReassignBox({ id, assignees }: { id: number; assignees: Assignee[] }) {
  const [open, setOpen] = useState(false);
  const [assigneeId, setAssigneeId] = useState<number>(0);
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="text-indigo-600 hover:underline text-xs">
        Yeniden ata
      </button>
    );
  }

  return (
    <div className="mt-3 space-y-2 border-t pt-3">
      <select
        value={assigneeId || ''}
        onChange={(e) => setAssigneeId(Number(e.target.value))}
        className="w-full border rounded-lg px-3 py-1.5 text-sm"
      >
        <option value="">Yeni kişi seçiniz...</option>
        {assignees.map((a) => (
          <option key={a.id} value={a.id}>{a.name}</option>
        ))}
      </select>
      <input
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Not (opsiyonel)..."
        className="w-full border rounded-lg px-3 py-1.5 text-sm"
      />
      <div className="flex gap-3 items-center">
        <button
          disabled={pending}
          onClick={() =>
            start(async () => {
              setError(null);
              const res = await reassignRequest(id, assigneeId, note);
              if (res?.error) setError(res.error);
              else { setOpen(false); setAssigneeId(0); setNote(''); }
            })
          }
          className="bg-indigo-600 text-white px-4 py-1.5 rounded-lg text-xs hover:bg-indigo-700 disabled:opacity-50"
        >
          Ata
        </button>
        <button onClick={() => setOpen(false)} className="text-xs text-gray-500 hover:underline">Vazgeç</button>
        {error && <span className="text-xs text-red-600">{error}</span>}
      </div>
    </div>
  );
}