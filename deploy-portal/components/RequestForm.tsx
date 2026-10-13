'use client';

import { useActionState, useState } from 'react';
import type { FormState } from '@/app/actions';
import type { Environment, Application, DeploymentRequest, ScriptFile } from '@prisma/client';

type Props = {
  environments: Environment[];
  applications: Application[];
  action: (prev: FormState, formData: FormData) => Promise<FormState>;
  initial?: DeploymentRequest & { scripts: ScriptFile[] };
};

export function RequestForm({ environments, applications, action, initial }: Props) {
  const [state, formAction, pending] = useActionState(action, undefined);
  const [envId, setEnvId] = useState<number>(initial?.environmentId ?? 0);
  const [hasScript, setHasScript] = useState<boolean>(initial?.hasScript ?? false);

  const selectedEnv = environments.find((e) => e.id === envId);
  const visibleApps = applications.filter((a) => !a.mhrsOnly || selectedEnv?.name === 'MHRS');

  const existingMain = initial?.scripts.find((s) => s.dbType === 'MAIN');
  const existingCamp = initial?.scripts.find((s) => s.dbType === 'CAMP');

  return (
    <form action={formAction} className="bg-white rounded-xl shadow-sm p-6 space-y-5 max-w-2xl">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium mb-1">Ortam</label>
          <select
            name="environmentId"
            value={envId || ''}
            onChange={(e) => setEnvId(Number(e.target.value))}
            className="w-full border rounded-lg px-3 py-2 text-sm"
          >
            <option value="">Seçiniz...</option>
            {environments.map((e) => (
              <option key={e.id} value={e.id}>{e.name}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Uygulama</label>
          <select
            name="applicationId"
            defaultValue={initial?.applicationId ?? ''}
            className="w-full border rounded-lg px-3 py-2 text-sm"
          >
            <option value="">Seçiniz...</option>
            {visibleApps.map((a) => (
              <option key={a.id} value={a.id}>{a.name}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Release Tarihi</label>
          <input
            type="date"
            name="releaseDate"
            defaultValue={initial ? new Date(initial.releaseDate).toISOString().slice(0, 10) : ''}
            className="w-full border rounded-lg px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Branch</label>
          <input
            type="text"
            name="branch"
            placeholder="örn: develop, release/2.14.0"
            defaultValue={initial?.branch ?? ''}
            className="w-full border rounded-lg px-3 py-2 text-sm font-mono"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium mb-2">DB Script var mı?</label>
        <div className="flex gap-6 text-sm">
          <label className="flex items-center gap-2">
            <input type="radio" name="hasScript" value="yes" checked={hasScript} onChange={() => setHasScript(true)} />
            Var
          </label>
          <label className="flex items-center gap-2">
            <input type="radio" name="hasScript" value="no" checked={!hasScript} onChange={() => setHasScript(false)} />
            Yok
          </label>
        </div>
      </div>

      {hasScript && (
        <div className="grid grid-cols-2 gap-4">
          <div className="border-2 border-dashed border-indigo-200 rounded-lg p-4 bg-indigo-50/40">
            <div className="text-sm font-semibold text-indigo-700 mb-1">MainDB Script</div>
            {existingMain && (
              <div className="text-xs text-gray-500 mb-2">Mevcut: {existingMain.fileName}</div>
            )}
            <input type="file" name="mainScript" className="text-xs w-full" />
          </div>
          <div className="border-2 border-dashed border-emerald-200 rounded-lg p-4 bg-emerald-50/40">
            <div className="text-sm font-semibold text-emerald-700 mb-1">CampDB Script</div>
            {existingCamp && (
              <div className="text-xs text-gray-500 mb-2">Mevcut: {existingCamp.fileName}</div>
            )}
            <input type="file" name="campScript" className="text-xs w-full" />
          </div>
        </div>
      )}

      {state?.error && (
        <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
          {state.error}
        </div>
      )}

      <button
        type="submit"
        disabled={pending}
        className="bg-indigo-600 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
      >
        {pending ? 'Gönderiliyor...' : initial ? 'Güncelle' : 'Send'}
      </button>
    </form>
  );
}