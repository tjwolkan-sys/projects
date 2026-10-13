'use client';

import { useActionState, useMemo, useState } from 'react';
import { createRequest, type FormState } from '@/app/actions';
import type { Environment, Application } from '@prisma/client';
import { isDeployDay, supportsCampDb, DEPLOY_DAYS_LABEL } from '@/lib/deploy-rules';
import {
  Target,
  CodeXml,
  ShieldCheck,
  Lock,
  Check,
  Cloud,
  Box,
  Calendar,
  GitBranch,
  Database,
  Send,
  AlertTriangle,
  Pencil,
  Upload,
  FileCheck2,
  Sparkles,
  Siren,
  ClipboardCheck,
} from 'lucide-react';
import { DeployDatePicker } from '@/components/DeployDatePicker';

const STEPS = [
  {
    no: 1,
    title: 'Hedef Belirleyin',
    railLabel: 'HEDEF',
    desc: "Deployment'ın yapılacağı ortam ve uygulamayı seçin.",
    lockedHint: 'ortam ve uygulama seçimi',
    icon: Target,
    iconBg: 'bg-[#EEEDFB] text-[#5850C7]',
  },
  {
    no: 2,
    title: 'Release & Script',
    railLabel: 'RELEASE & SCRIPT',
    desc: 'Release bilgilerini ve çalıştırılacak scriptleri girin.',
    lockedHint: 'tarih, branch ve scriptler',
    icon: CodeXml,
    iconBg: 'bg-[#DEF5EC] text-[#1D9E75]',
  },
  {
    no: 3,
    title: 'Özet & Onay',
    railLabel: 'ÖZET & ONAY',
    desc: 'Seçimlerinizi gözden geçirin ve onaya gönderin.',
    lockedHint: 'gözden geçirip gönderin',
    icon: ShieldCheck,
    iconBg: 'bg-[#FDF2DC] text-[#C98A1B]',
  },
] as const;

const inputCls =
  'w-full rounded-xl border border-black/10 bg-white px-3.5 py-2.5 text-[13px] text-[#1e2030] placeholder-[#b3b6c4] outline-none transition-colors focus:border-[#5850C7]/60 focus:ring-2 focus:ring-[#5850C7]/10';

export function RequestWizard({
  environments,
  applications,
  cabEnabled,
}: {
  environments: Environment[];
  applications: Application[];
  cabEnabled: boolean;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(createRequest, undefined);

  const [step, setStep] = useState(0);
  const [stepError, setStepError] = useState<string | null>(null);

  const [envId, setEnvId] = useState(0);
  const [appId, setAppId] = useState(0);
  const [releaseDate, setReleaseDate] = useState('');
  const [branch, setBranch] = useState('');
  const [noScript, setNoScript] = useState(false);
  const [mainFile, setMainFile] = useState('');
  const [campFile, setCampFile] = useState('');
  const [emergency, setEmergency] = useState(false);
  const [cabRequired, setCabRequired] = useState(false);
  const [cabApproved, setCabApproved] = useState(false);

  const selectedEnv = environments.find((e) => e.id === envId);
  const selectedApp = applications.find((a) => a.id === appId);

  const visibleApps = useMemo(
    () => applications.filter((a) => !a.mhrsOnly || selectedEnv?.name === 'MHRS'),
    [applications, selectedEnv]
  );

  const campSupported = supportsCampDb(selectedApp?.name);
  const step1Done = Boolean(envId && appId);

  function next() {
    if (step === 0 && !step1Done) {
      setStepError('Ortam ve uygulama seçilmeli.');
      return;
    }
    if (step === 1) {
      if (!releaseDate || !branch.trim()) {
        setStepError('Tarih ve branch zorunlu.');
        return;
      }
      if (cabEnabled && !emergency && !isDeployDay(releaseDate)) {
        setStepError(
          `Normal deploymentlar yalnızca ${DEPLOY_DAYS_LABEL} günleri yapılabilir. Acilse "Acil CAB" işaretleyin.`
        );
        return;
      }
      const scriptsOk = noScript || mainFile || (campSupported && campFile);
      if (!scriptsOk) {
        setStepError('Script yükleyin ya da "script yok" işaretleyin.');
        return;
      }
    }
    setStepError(null);
    setStep((s) => Math.min(s + 1, 2));
  }

  function goTo(i: number) {
    setStepError(null);
    setStep(i);
  }

  const fmtLong = releaseDate
    ? new Date(releaseDate + 'T00:00:00').toLocaleDateString('tr-TR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      })
    : undefined;

  const doneCount = (step > 0 ? 1 : 0) + (step > 1 ? 1 : 0);

  const scriptSummary = noScript
    ? 'script yok'
    : [mainFile && 'MainDB', campSupported && campFile && 'CampDB'].filter(Boolean).join(' + ');

  return (
    <section>
      <div className="mb-6 border-b border-black/5 pb-4">
        <h1 className="text-[22px] font-semibold text-[#1e2030]">Yeni deployment request</h1>
        <p className="mt-0.5 text-[13px] text-[#8a8ea0]">
          Adım {step + 1} / 3 — {STEPS[step].railLabel.toLowerCase()}
        </p>
      </div>

      <form action={formAction} className="grid items-start gap-5 xl:grid-cols-[1.55fr_1fr]">
        {/* CAB / acil bayrakları — tek kaynak, her zaman submit'e gider */}
        <input type="hidden" name="emergencyCab" value={emergency ? 'yes' : 'no'} />
        <input type="hidden" name="cabRequired" value={cabRequired ? 'yes' : 'no'} />
        <input type="hidden" name="cabApproved" value={cabApproved ? 'yes' : 'no'} />

        {/* ============ SOL: PIPELINE ============ */}
        <div className="relative">
          {/* dikey ray */}
          <div className="absolute bottom-8 left-[21px] top-6 w-[3px] rounded bg-black/8" aria-hidden />
          <div
            className="absolute left-[21px] top-6 w-[3px] rounded bg-[#1D9E75] transition-all"
            style={{ height: step === 0 ? '0%' : step === 1 ? '35%' : '70%' }}
            aria-hidden
          />

          <div className="flex flex-col gap-3">
            {STEPS.map((s, i) => {
              const Icon = s.icon;
              const isActive = i === step;
              const isDone = i < step;

              return (
                <div key={s.no} className="relative flex gap-4">
                  {/* düğüm */}
                  <div className="z-10 flex w-[44px] shrink-0 justify-center">
                    {isDone ? (
                      <div className="flex h-[44px] w-[44px] items-center justify-center rounded-full bg-[#1D9E75] text-white ring-4 ring-[#f5f6fb]">
                        <Check size={20} strokeWidth={2.5} />
                      </div>
                    ) : isActive ? (
                      <div className="flex h-[44px] w-[44px] items-center justify-center rounded-full bg-[#5850C7] text-[16px] font-semibold text-white ring-4 ring-[#f5f6fb] shadow-[0_0_0_8px_rgba(88,80,199,0.14)]">
                        {s.no}
                      </div>
                    ) : (
                      <div className="mt-1 flex h-8 w-8 items-center justify-center rounded-full border-[1.5px] border-[#dcdeea] bg-white text-[13px] text-[#b3b6c4] ring-4 ring-[#f5f6fb]">
                        {s.no}
                      </div>
                    )}
                  </div>

                  {/* kart */}
                  <div className="min-w-0 flex-1">
                    {/* --- TAMAMLANMIŞ --- */}
                    {isDone && (
                      <button
                        type="button"
                        onClick={() => goTo(i)}
                        className="group flex w-full items-center justify-between rounded-xl border border-[#1D9E75]/25 border-l-4 border-l-[#1D9E75] bg-white px-4 py-2.5 text-left transition-colors hover:bg-[#DEF5EC]/40"
                      >
                        <div className="min-w-0">
                          <div className="text-[11px] font-semibold tracking-wide text-[#0F6E56]">
                            {s.railLabel} <span className="font-normal text-[#8a8ea0]">— tamamlandı</span>
                          </div>
                          <div
                            className={`truncate text-[13px] text-[#1e2030] ${i === 1 ? 'font-mono text-[12px]' : ''}`}
                          >
                            {i === 0 && `${selectedEnv?.name} · ${selectedApp?.name}`}
                            {i === 1 && (
                              <>
                                {fmtLong} · {branch} · {scriptSummary}
                                {emergency && <span className="text-[#A32D2D]"> · 🚨 ACİL</span>}
                              </>
                            )}
                          </div>
                        </div>
                        <span className="ml-3 flex shrink-0 items-center gap-1 text-[11px] text-[#5850C7]">
                          <Pencil size={12} /> Değiştir
                        </span>
                      </button>
                    )}

                    {/* --- AKTİF --- */}
                    <div
                      className={
                        isActive
                          ? 'rounded-2xl border-[1.5px] border-[#5850C7]/35 border-l-4 border-l-[#5850C7] bg-white p-5 shadow-lg shadow-[#5850C7]/10'
                          : 'hidden'
                      }
                    >
                      <div className="mb-4 flex items-center gap-3">
                        <span className={`flex h-9 w-9 items-center justify-center rounded-lg ${s.iconBg}`}>
                          <Icon size={19} strokeWidth={1.9} />
                        </span>
                        <div>
                          <div className="text-[15px] font-semibold text-[#5850C7]">{s.title}</div>
                          <div className="text-[11px] text-[#8a8ea0]">{s.desc}</div>
                        </div>
                      </div>

                      {/* Adım 1 */}
                      {i === 0 && (
                        <div className="grid gap-4 sm:grid-cols-2">
                          <div>
                            <label className="mb-1.5 block text-xs text-[#4a4d5e]">Ortam</label>
                            <div className="relative">
                              <Cloud
                                size={15}
                                className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#5850C7]"
                              />
                              <select
                                name="environmentId"
                                value={envId || ''}
                                onChange={(e) => {
                                  setEnvId(Number(e.target.value));
                                  setAppId(0);
                                }}
                                className={`${inputCls} pl-9`}
                              >
                                <option value="">Ortam seçiniz</option>
                                {environments.map((e) => (
                                  <option key={e.id} value={e.id}>{e.name}</option>
                                ))}
                              </select>
                            </div>
                          </div>
                          <div>
                            <label className="mb-1.5 block text-xs text-[#4a4d5e]">Uygulama</label>
                            <div className="relative">
                              <Box
                                size={15}
                                className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#1D9E75]"
                              />
                              <select
                                name="applicationId"
                                value={appId || ''}
                                onChange={(e) => setAppId(Number(e.target.value))}
                                className={`${inputCls} pl-9`}
                              >
                                <option value="">Uygulama seçiniz</option>
                                {visibleApps.map((a) => (
                                  <option key={a.id} value={a.id}>{a.name}</option>
                                ))}
                              </select>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Adım 2 */}
                      {i === 1 && (
                        <div>
                          {cabEnabled && (
                            <label
                              className={`mb-4 flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 transition-colors ${
                                emergency ? 'border-[#E24B4A]/40 bg-[#FBE9E9]' : 'border-black/10 bg-[#f5f6fb]'
                              }`}
                            >
                              <input
                                type="checkbox"
                                checked={emergency}
                                onChange={(e) => {
                                  setEmergency(e.target.checked);
                                  if (!e.target.checked && releaseDate && !isDeployDay(releaseDate)) {
                                    setReleaseDate('');
                                  }
                                }}
                                className="accent-[#E24B4A]"
                              />
                              <Siren size={16} className={emergency ? 'text-[#A32D2D]' : 'text-[#8a8ea0]'} />
                              <span
                                className={`text-xs ${emergency ? 'font-medium text-[#A32D2D]' : 'text-[#4a4d5e]'}`}
                              >
                                Acil CAB — production bug fix, tüm günler açılır
                              </span>
                            </label>
                          )}

                          <div className="grid gap-4 sm:grid-cols-2">
                            <div>
                              <label className="mb-1.5 block text-xs text-[#4a4d5e]">Release tarihi</label>
                              <DeployDatePicker
                                value={releaseDate}
                                onChange={setReleaseDate}
                                restrictDays={cabEnabled && !emergency}
                              />
                              <input type="hidden" name="releaseDate" value={releaseDate} />
                              {cabEnabled && (
                                <p className="mt-1 text-[10.5px] text-[#8a8ea0]">
                                  {emergency
                                    ? 'Acil deployment — tüm günler seçilebilir'
                                    : `Yalnızca ${DEPLOY_DAYS_LABEL}`}
                                </p>
                              )}
                            </div>
                            <div>
                              <label className="mb-1.5 block text-xs text-[#4a4d5e]">Branch</label>
                              <div className="relative">
                                <GitBranch
                                  size={15}
                                  className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#5850C7]"
                                />
                                <input
                                  type="text"
                                  name="branch"
                                  value={branch}
                                  onChange={(e) => setBranch(e.target.value)}
                                  placeholder="release/6.7.1"
                                  className={`${inputCls} pl-9 font-mono text-[12px]`}
                                />
                              </div>
                            </div>
                          </div>

                          <div className="mt-4">
                            <div className="mb-2 flex items-center justify-between">
                              <label className="text-xs text-[#4a4d5e]">DB scriptleri</label>
                              <label className="flex items-center gap-1.5 text-xs text-[#8a8ea0]">
                                <input
                                  type="checkbox"
                                  checked={noScript}
                                  onChange={(e) => setNoScript(e.target.checked)}
                                  className="accent-[#5850C7]"
                                />
                                Bu release için script yok
                              </label>
                            </div>
                            <input type="hidden" name="hasScript" value={noScript ? 'no' : 'yes'} />
                            <div
                              className={`grid gap-3 ${campSupported ? 'sm:grid-cols-2' : ''} ${
                                noScript ? 'pointer-events-none opacity-40' : ''
                              }`}
                            >
                              <FileTile
                                label="MainDB"
                                name="mainScript"
                                file={mainFile}
                                onFile={setMainFile}
                                accent="#5850C7"
                              />
                              {campSupported && (
                                <FileTile
                                  label="CampDB"
                                  name="campScript"
                                  file={campFile}
                                  onFile={setCampFile}
                                  accent="#1D9E75"
                                />
                              )}
                            </div>
                            {!campSupported && selectedApp && (
                              <p className="mt-2 text-[10.5px] text-[#8a8ea0]">
                                CampDB scripti yalnızca PowerDialer deploymentlarında kullanılır.
                              </p>
                            )}
                          </div>

                          {cabEnabled && (
                            <div className="mt-4 rounded-xl border border-black/8 bg-[#f5f6fb] p-4">
                              <label className="flex cursor-pointer items-center gap-2.5 text-xs text-[#4a4d5e]">
                                <input
                                  type="checkbox"
                                  checked={cabRequired}
                                  onChange={(e) => {
                                    setCabRequired(e.target.checked);
                                    if (!e.target.checked) setCabApproved(false);
                                  }}
                                  className="accent-[#5850C7]"
                                />
                                <ClipboardCheck size={15} className="text-[#5850C7]" />
                                Bu deployment için CAB onayı gerekli
                              </label>
                              {cabRequired && (
                                <label className="ml-6 mt-2.5 flex cursor-pointer items-center gap-2.5 text-xs text-[#4a4d5e]">
                                  <input
                                    type="checkbox"
                                    checked={cabApproved}
                                    onChange={(e) => setCabApproved(e.target.checked)}
                                    className="accent-[#1D9E75]"
                                  />
                                  CAB onayı alındı
                                </label>
                              )}
                            </div>
                          )}
                        </div>
                      )}

                      {/* Adım 3 — gözden geçirme */}
                      {i === 2 && (
                        <div>
                          <div className="grid gap-3 sm:grid-cols-2">
                            <ReviewItem icon={<Cloud size={14} />} label="Ortam" value={selectedEnv?.name ?? '—'} />
                            <ReviewItem icon={<Box size={14} />} label="Uygulama" value={selectedApp?.name ?? '—'} />
                            <ReviewItem icon={<Calendar size={14} />} label="Release tarihi" value={fmtLong ?? '—'} />
                            <ReviewItem icon={<GitBranch size={14} />} label="Branch" value={branch || '—'} mono />
                            <ReviewItem
                              icon={<Database size={14} />}
                              label="MainDB scripti"
                              value={noScript ? 'Script yok' : mainFile || 'Yüklenmedi'}
                              mono={Boolean(mainFile)}
                            />
                            {campSupported && (
                              <ReviewItem
                                icon={<Database size={14} />}
                                label="CampDB scripti"
                                value={noScript ? 'Script yok' : campFile || 'Yüklenmedi'}
                                mono={Boolean(campFile)}
                              />
                            )}
                            {cabEnabled && (
                              <>
                                <ReviewItem
                                  icon={<ClipboardCheck size={14} />}
                                  label="CAB Onayı"
                                  value={
                                    cabRequired ? (cabApproved ? 'Gerekli — alındı ✓' : 'Gerekli — bekliyor') : 'Gerekmiyor'
                                  }
                                />
                                <ReviewItem
                                  icon={<Siren size={14} />}
                                  label="Acil"
                                  value={emergency ? 'ACİL deployment 🚨' : 'Hayır'}
                                />
                              </>
                            )}
                          </div>
                          <div className="mt-4 flex items-start gap-2 rounded-xl bg-[#EEEDFB] px-4 py-3 text-xs text-[#3C3489]">
                            <Sparkles size={14} className="mt-0.5 shrink-0" />
                            Gönderdiğinizde onaycıya detayları içeren bir e-posta gider; sonucu Requestlerim
                            sayfasından takip edebilirsiniz.
                          </div>
                        </div>
                      )}

                      {i < 2 && (
                        <div className="mt-4 flex justify-end">
                          <button
                            type="button"
                            onClick={next}
                            className="rounded-xl bg-[#5850C7] px-7 py-2.5 text-[13px] font-medium text-white shadow-sm shadow-[#5850C7]/30 transition-opacity hover:opacity-90"
                          >
                            İleri →
                          </button>
                        </div>
                      )}
                    </div>

                    {/* --- KİLİTLİ --- */}
                    {!isActive && !isDone && (
                      <div className="mt-1 flex items-center justify-between rounded-xl border border-dashed border-black/10 bg-white/55 px-4 py-2.5">
                        <div className="text-[12px] text-[#a4a7b8]">
                          {s.railLabel.charAt(0) + s.railLabel.slice(1).toLowerCase()}{' '}
                          <span className="text-[#c3c5d3]">— {s.lockedHint}</span>
                        </div>
                        <Lock size={14} className="shrink-0 text-[#c3c5d3]" />
                      </div>
                    )}

                    {/* hidden input'lar — kapanmış adım değerleri submit'e gitsin */}
                    {isDone && i === 0 && (
                      <>
                        <input type="hidden" name="environmentId" value={envId || ''} />
                        <input type="hidden" name="applicationId" value={appId || ''} />
                      </>
                    )}
                    {isDone && i === 1 && (
                      <>
                        <input type="hidden" name="releaseDate" value={releaseDate} />
                        <input type="hidden" name="branch" value={branch} />
                        <input type="hidden" name="hasScript" value={noScript ? 'no' : 'yes'} />
                      </>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {(stepError || state?.error) && (
            <div className="mt-4 flex items-center gap-2 rounded-xl border border-[#A32D2D]/20 bg-[#FBE9E9] px-4 py-2.5 text-xs text-[#A32D2D]">
              <AlertTriangle size={14} />
              {stepError ?? state?.error}
            </div>
          )}
        </div>

        {/* ============ SAĞ: MAKBUZ-ÖZET ============ */}
        <div className="rounded-2xl border border-black/5 bg-[#EDEFF6] p-[3px]">
          <div className="rounded-[13px] border-[1.5px] border-dashed border-[#5850C7]/25 p-5">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-[11px] font-semibold tracking-widest text-[#8a8ea0]">CANLI ÖZET</span>
              <span className="rounded-full bg-[#5850C7]/10 px-2.5 py-0.5 text-[10px] font-medium text-[#5850C7]">
                {doneCount}/3 tamam
              </span>
            </div>

            <table className="w-full text-[12.5px]">
              <tbody>
                <LiveRow label="Ortam" value={selectedEnv?.name} />
                <LiveRow label="Uygulama" value={selectedApp?.name} />
                <LiveRow label="Tarih" value={fmtLong} />
                <LiveRow label="Branch" value={branch || undefined} mono />
                <tr>
                  <td className="py-1 text-[#8a8ea0]">MainDB</td>
                  <td className="py-1 text-right">
                    <ScriptState file={mainFile} noScript={noScript} />
                  </td>
                </tr>
                {campSupported && (
                  <tr>
                    <td className="py-1 text-[#8a8ea0]">CampDB</td>
                    <td className="py-1 text-right">
                      <ScriptState file={campFile} noScript={noScript} />
                    </td>
                  </tr>
                )}
                {cabEnabled && (
                  <>
                    <LiveRow
                      label="CAB"
                      value={cabRequired ? (cabApproved ? 'alındı ✓' : 'bekliyor') : 'gerekmiyor'}
                    />
                    {emergency && (
                      <tr>
                        <td className="py-1 text-[#8a8ea0]">Acil</td>
                        <td className="py-1 text-right font-semibold text-[#A32D2D]">🚨 evet</td>
                      </tr>
                    )}
                  </>
                )}
              </tbody>
            </table>

            <div className="mt-3 border-t-[1.5px] border-dashed border-black/10 pt-3">
              <button
                type="submit"
                disabled={pending || step < 2}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-[#5850C7] py-2.5 text-[12.5px] font-medium text-white transition-opacity hover:opacity-90 disabled:bg-[#C6C2EC]"
              >
                <Send size={14} />
                {pending ? 'Gönderiliyor...' : 'Gönder ve onaya yolla'}
              </button>
              <p className="mt-2 flex items-center justify-center gap-1 text-center text-[10.5px] text-[#a4a7b8]">
                {step < 2 ? (
                  <>
                    <Lock size={10} /> adım 3&apos;te aktifleşir
                  </>
                ) : (
                  'Hazır — gönderebilirsiniz'
                )}
              </p>
            </div>
          </div>
        </div>
      </form>
    </section>
  );
}

/* ---------- yardımcı componentler ---------- */

function FileTile({
  label,
  name,
  file,
  onFile,
  accent,
}: {
  label: string;
  name: string;
  file: string;
  onFile: (v: string) => void;
  accent: string;
}) {
  return (
    <label
      className="block cursor-pointer rounded-xl border-[1.5px] border-dashed p-4 transition-colors"
      style={{
        borderColor: file ? `${accent}55` : 'rgba(0,0,0,0.12)',
        background: file ? `${accent}0d` : undefined,
      }}
    >
      <div className="flex items-center gap-3">
        <span
          className="flex h-9 w-9 items-center justify-center rounded-lg"
          style={{ background: file ? `${accent}1f` : 'rgba(0,0,0,0.05)', color: file ? accent : '#b3b6c4' }}
        >
          {file ? <FileCheck2 size={17} /> : <Upload size={17} />}
        </span>
        <div className="min-w-0">
          <div className="text-xs font-medium" style={{ color: file ? accent : '#4a4d5e' }}>
            {label}
          </div>
          <div className="truncate text-[11px] text-[#8a8ea0]">{file || 'dosya seç veya sürükle'}</div>
        </div>
      </div>
      <input
        type="file"
        name={name}
        className="hidden"
        onChange={(e) => onFile(e.target.files?.[0]?.name ?? '')}
      />
    </label>
  );
}

function ReviewItem({
  icon,
  label,
  value,
  mono,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="rounded-xl bg-[#f5f6fb] px-4 py-3">
      <div className="flex items-center gap-1.5 text-[11px] text-[#8a8ea0]">
        {icon} {label}
      </div>
      <div
        className={`mt-1 truncate text-[13px] font-medium text-[#1e2030] ${mono ? 'font-mono text-[12px]' : ''}`}
      >
        {value}
      </div>
    </div>
  );
}

function LiveRow({ label, value, mono }: { label: string; value?: string; mono?: boolean }) {
  return (
    <tr>
      <td className="py-1 text-[#8a8ea0]">{label}</td>
      <td
        className={`py-1 text-right ${value ? 'font-semibold text-[#1e2030]' : 'text-[#c3c5d3]'} ${
          mono && value ? 'font-mono text-[11.5px] font-normal text-[#5850C7]' : ''
        }`}
      >
        {value ?? '—'}
      </td>
    </tr>
  );
}

function ScriptState({ file, noScript }: { file: string; noScript: boolean }) {
  if (noScript) return <span className="text-[#c3c5d3]">yok</span>;
  if (file)
    return (
      <span className="inline-flex items-center gap-1 text-[#1D9E75]">
        <Check size={12} /> yüklendi
      </span>
    );
  return <span className="text-[#c3c5d3]">—</span>;
}