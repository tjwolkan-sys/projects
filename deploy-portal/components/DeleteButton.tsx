'use client';

import { deleteRequest } from '@/app/actions';
import { useTransition } from 'react';

export function DeleteButton({ id }: { id: number }) {
  const [pending, start] = useTransition();
  return (
    <button
      disabled={pending}
      onClick={() => {
        if (confirm(`#${id} numaralı request silinsin mi?`)) {
          start(() => deleteRequest(id));
        }
      }}
      className="text-red-600 hover:underline text-xs disabled:opacity-50"
    >
      Sil
    </button>
  );
}