'use server';

import { prisma } from '@/lib/db';
import { sendApprovalMail, sendAssignmentMail } from '@/lib/mail';
import { flags } from '@/lib/flags';
import { isDeployDay, supportsCampDb, DEPLOY_DAYS_LABEL } from '@/lib/deploy-rules';
import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';
import { writeFile, mkdir, unlink } from 'fs/promises';
import path from 'path';
import crypto from 'crypto';

const UPLOAD_DIR = path.join(process.cwd(), 'uploads');

export type FormState = { error?: string } | undefined;

async function saveScript(requestId: number, dbType: 'MAIN' | 'CAMP', file: File) {
  await mkdir(UPLOAD_DIR, { recursive: true });
  const storedName = `${crypto.randomUUID()}_${file.name}`;
  await writeFile(path.join(UPLOAD_DIR, storedName), Buffer.from(await file.arrayBuffer()));

  const existing = await prisma.scriptFile.findFirst({ where: { requestId, dbType } });
  if (existing) {
    await unlink(path.join(UPLOAD_DIR, existing.storedName)).catch(() => {});
    await prisma.scriptFile.update({
      where: { id: existing.id },
      data: { fileName: file.name, storedName },
    });
  } else {
    await prisma.scriptFile.create({
      data: { requestId, dbType, fileName: file.name, storedName },
    });
  }
}

function parseForm(formData: FormData) {
  const environmentId = Number(formData.get('environmentId'));
  const applicationId = Number(formData.get('applicationId'));
  const rawDate = String(formData.get('releaseDate') ?? '');
  const branch = String(formData.get('branch') ?? '').trim();
  const hasScript = formData.get('hasScript') === 'yes';
  const mainScript = formData.get('mainScript') as File | null;
  const campScript = formData.get('campScript') as File | null;
  const emergencyCab = formData.get('emergencyCab') === 'yes';
  const cabRequired = formData.get('cabRequired') === 'yes';
  const cabApproved = formData.get('cabApproved') === 'yes';

  if (!environmentId) return { error: 'Ortam seçilmeli.' } as const;
  if (!applicationId) return { error: 'Uygulama seçilmeli.' } as const;
  if (!rawDate) return { error: 'Tarih seçilmeli.' } as const;
  if (!branch) return { error: 'Branch adı zorunlu.' } as const;

  return {
    environmentId,
    applicationId,
    rawDate,
    releaseDate: new Date(rawDate),
    branch,
    hasScript,
    mainScript,
    campScript,
    emergencyCab,
    cabRequired,
    cabApproved,
  };
}

export async function createRequest(_prev: FormState, formData: FormData): Promise<FormState> {
  const p = parseForm(formData);
  if ('error' in p) return p;

  const app = await prisma.application.findUnique({ where: { id: p.applicationId } });
  if (!app) return { error: 'Uygulama bulunamadı.' };
  const campOk = supportsCampDb(app.name);

  // Gün kuralı: acil değilse yalnızca Salı/Perşembe (feature flag açıkken)
  if (flags.cabWorkflow && !p.emergencyCab && !isDeployDay(p.rawDate)) {
    return { error: `Normal deploymentlar yalnızca ${DEPLOY_DAYS_LABEL} günleri yapılabilir. Acil deployment için "Acil CAB" işaretleyin.` };
  }

  if (p.hasScript) {
    const hasMain = p.mainScript && p.mainScript.size > 0;
    const hasCamp = campOk && p.campScript && p.campScript.size > 0;
    if (!hasMain && !hasCamp) {
      return { error: `Script var dediniz ama dosya yüklemediniz${campOk ? ' (MainDB veya CampDB)' : ' (MainDB)'}.` };
    }
  }

  const req = await prisma.deploymentRequest.create({
    data: {
      environmentId: p.environmentId,
      applicationId: p.applicationId,
      releaseDate: p.releaseDate,
      branch: p.branch,
      hasScript: p.hasScript,
      emergencyCab: flags.cabWorkflow ? p.emergencyCab : false,
      cabRequired: flags.cabWorkflow ? p.cabRequired : false,
      cabApproved: flags.cabWorkflow ? p.cabRequired && p.cabApproved : false,
      approvalToken: crypto.randomUUID(),
    },
  });

  if (p.hasScript) {
    if (p.mainScript && p.mainScript.size > 0) await saveScript(req.id, 'MAIN', p.mainScript);
    if (campOk && p.campScript && p.campScript.size > 0) await saveScript(req.id, 'CAMP', p.campScript);
  }

  const full = await prisma.deploymentRequest.findUniqueOrThrow({
    where: { id: req.id },
    include: { environment: true, application: true, scripts: true },
  });

  sendApprovalMail(full).catch((e) => console.error('Onay maili gönderilemedi:', e));

  revalidatePath('/requests');
  redirect('/requests');
}

export async function updateRequest(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const existing = await prisma.deploymentRequest.findUnique({ where: { id }, include: { scripts: true } });
  if (!existing) return { error: 'Request bulunamadı.' };
  if (existing.status !== 'PENDING') return { error: 'Sadece Pending durumundaki requestler düzenlenebilir.' };

  const p = parseForm(formData);
  if ('error' in p) return p;

  const app = await prisma.application.findUnique({ where: { id: p.applicationId } });
  if (!app) return { error: 'Uygulama bulunamadı.' };
  const campOk = supportsCampDb(app.name);

  // Edit formu CAB alanlarını göndermiyorsa mevcut değerler korunur
  const emergencyCab = formData.has('emergencyCab') ? p.emergencyCab : existing.emergencyCab;
  const cabRequired = formData.has('cabRequired') ? p.cabRequired : existing.cabRequired;
  const cabApproved = formData.has('cabApproved') ? p.cabApproved : existing.cabApproved;

  if (flags.cabWorkflow && !emergencyCab && !isDeployDay(p.rawDate)) {
    return { error: `Normal deploymentlar yalnızca ${DEPLOY_DAYS_LABEL} günleri yapılabilir.` };
  }

  await prisma.deploymentRequest.update({
    where: { id },
    data: {
      environmentId: p.environmentId,
      applicationId: p.applicationId,
      releaseDate: p.releaseDate,
      branch: p.branch,
      hasScript: p.hasScript,
      emergencyCab,
      cabRequired,
      cabApproved: cabRequired && cabApproved,
    },
  });

  if (p.hasScript) {
    if (p.mainScript && p.mainScript.size > 0) await saveScript(id, 'MAIN', p.mainScript);
    if (campOk && p.campScript && p.campScript.size > 0) await saveScript(id, 'CAMP', p.campScript);
    // Camp desteklemeyen uygulamaya geçildiyse eski CAMP scriptini temizle
    if (!campOk) {
      const camp = existing.scripts.find((s) => s.dbType === 'CAMP');
      if (camp) {
        await unlink(path.join(UPLOAD_DIR, camp.storedName)).catch(() => {});
        await prisma.scriptFile.delete({ where: { id: camp.id } });
      }
    }
    const updated = await prisma.deploymentRequest.findUniqueOrThrow({ where: { id }, include: { scripts: true } });
    if (updated.scripts.length === 0) return { error: 'Script var dediniz ama hiç dosya yok.' };
  } else {
    for (const s of existing.scripts) {
      await unlink(path.join(UPLOAD_DIR, s.storedName)).catch(() => {});
    }
    await prisma.scriptFile.deleteMany({ where: { requestId: id } });
  }

  revalidatePath('/requests');
  redirect(`/requests/${id}`);
}

export async function deleteRequest(id: number) {
  const existing = await prisma.deploymentRequest.findUnique({ where: { id }, include: { scripts: true } });
  if (!existing) return;
  for (const s of existing.scripts) {
    await unlink(path.join(UPLOAD_DIR, s.storedName)).catch(() => {});
  }
  await prisma.deploymentRequest.delete({ where: { id } });
  revalidatePath('/requests');
}

export async function updateStatus(id: number, status: string, rejectReason?: string) {
  if (status === 'REJECTED' && !rejectReason?.trim()) {
    return { error: 'Reject için sebep zorunlu.' };
  }
  await prisma.deploymentRequest.update({
    where: { id },
    data: { status, rejectReason: status === 'REJECTED' ? rejectReason : null },
  });
  revalidatePath('/requests');
  revalidatePath(`/requests/${id}`);
  return { ok: true };
}

export async function approveByToken(token: string, assigneeId: number, note?: string) {
  if (!assigneeId) return { error: 'Atanacak kişi seçilmeli.' };

  const req = await prisma.deploymentRequest.findUnique({ where: { approvalToken: token } });
  if (!req) return { error: 'Geçersiz link.' };
  if (req.status !== 'PENDING') return { error: `Bu request zaten işlem görmüş (${req.status}).` };

  const updated = await prisma.deploymentRequest.update({
    where: { id: req.id },
    data: {
      status: 'APPROVED',
      approvedBy: process.env.APPROVER_EMAIL,
      approvedAt: new Date(),
      assigneeId,
      assignmentNote: note?.trim() || null,
      assignedAt: new Date(),
    },
    include: { environment: true, application: true, scripts: true, assignee: true },
  });

  sendAssignmentMail(updated).catch((e) => console.error('Atama maili gönderilemedi:', e));

  revalidatePath('/requests');
  return { ok: true };
}

export async function rejectByToken(token: string, reason: string) {
  if (!reason.trim()) return { error: 'Reject için sebep zorunlu.' };
  const req = await prisma.deploymentRequest.findUnique({ where: { approvalToken: token } });
  if (!req) return { error: 'Geçersiz link.' };
  if (req.status !== 'PENDING') return { error: `Bu request zaten işlem görmüş (${req.status}).` };

  await prisma.deploymentRequest.update({
    where: { id: req.id },
    data: {
      status: 'REJECTED',
      rejectReason: reason,
      approvedBy: process.env.APPROVER_EMAIL,
      approvedAt: new Date(),
    },
  });
  revalidatePath('/requests');
  return { ok: true };
}

export async function reassignRequest(id: number, assigneeId: number, note?: string) {
  if (!assigneeId) return { error: 'Atanacak kişi seçilmeli.' };

  const req = await prisma.deploymentRequest.findUnique({ where: { id } });
  if (!req) return { error: 'Request bulunamadı.' };
  if (!['APPROVED', 'IN_PROGRESS'].includes(req.status)) {
    return { error: 'Sadece Approved veya In Progress durumunda yeniden atama yapılabilir.' };
  }

  const updated = await prisma.deploymentRequest.update({
    where: { id },
    data: { assigneeId, assignmentNote: note?.trim() || null, assignedAt: new Date() },
    include: { environment: true, application: true, scripts: true, assignee: true },
  });

  sendAssignmentMail(updated).catch((e) => console.error('Atama maili gönderilemedi:', e));

  revalidatePath('/requests');
  revalidatePath(`/requests/${id}`);
  return { ok: true };
}