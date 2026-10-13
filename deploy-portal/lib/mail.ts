import nodemailer from 'nodemailer';
import type { DeploymentRequest, Environment, Application, ScriptFile } from '@prisma/client';

type FullRequest = DeploymentRequest & {
  environment: Environment;
  application: Application;
  scripts: ScriptFile[];
};

const transporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST,
  port: Number(process.env.SMTP_PORT ?? 587),
  secure: false,
  requireTLS: true,
  auth: {
    user: process.env.SMTP_USER,
    pass: process.env.SMTP_PASS,
  },
  family: 4,
  connectionTimeout: 15000,
  greetingTimeout: 15000,
  socketTimeout: 20000,
});

/** boş/undefined adresleri ayıklar, aynı adresi tekilleştirir */
function buildCc(...addresses: (string | null | undefined)[]) {
  return [...new Set(addresses.filter((a): a is string => Boolean(a?.trim())))];
}

export async function sendApprovalMail(req: FullRequest) {
  const approveUrl = `${process.env.APP_URL}/approve/${req.approvalToken}`;

  const scriptRows = req.hasScript
    ? req.scripts
        .map(
          (s) =>
            `<tr><td style="padding:4px 12px;color:#666">${s.dbType === 'MAIN' ? 'MainDB' : 'CampDB'} Script</td><td style="padding:4px 12px">${s.fileName}</td></tr>`
        )
        .join('')
    : `<tr><td style="padding:4px 12px;color:#666">Script</td><td style="padding:4px 12px">Yok</td></tr>`;

  const cabRow = req.cabRequired
    ? `<tr><td style="padding:4px 12px;color:#666">CAB Onayı</td><td style="padding:4px 12px">${req.cabApproved ? '✅ Gerekli — alındı' : '⏳ Gerekli — henüz alınmadı'}</td></tr>`
    : `<tr><td style="padding:4px 12px;color:#666">CAB Onayı</td><td style="padding:4px 12px">Gerekmiyor</td></tr>`;

  const emergencyRow = req.emergencyCab
    ? `<tr><td style="padding:4px 12px;color:#666">Acil</td><td style="padding:4px 12px;color:#b91c1c"><b>🚨 ACİL DEPLOYMENT (Acil CAB)</b></td></tr>`
    : '';

  const html = `
    <div style="font-family:Segoe UI,Arial,sans-serif;max-width:560px">
      <h2 style="color:#4338ca">Deployment Request #${req.id}</h2>
      <table style="border-collapse:collapse;font-size:14px;background:#f8fafc;border-radius:8px;width:100%">
        <tr><td style="padding:4px 12px;color:#666">Uygulama</td><td style="padding:4px 12px"><b>${req.application.name}</b></td></tr>
        <tr><td style="padding:4px 12px;color:#666">Ortam</td><td style="padding:4px 12px"><b>${req.environment.name}</b></td></tr>
        <tr><td style="padding:4px 12px;color:#666">Branch</td><td style="padding:4px 12px"><code>${req.branch}</code></td></tr>
        <tr><td style="padding:4px 12px;color:#666">Release Tarihi</td><td style="padding:4px 12px">${req.releaseDate.toLocaleDateString('tr-TR')}</td></tr>
        <tr><td style="padding:4px 12px;color:#666">Requester</td><td style="padding:4px 12px">${req.requester}</td></tr>
        ${scriptRows}
        ${cabRow}
        ${emergencyRow}
      </table>
      <p style="margin-top:20px">
        <a href="${approveUrl}"
           style="background:#4f46e5;color:#fff;padding:10px 24px;border-radius:8px;text-decoration:none;font-size:14px">
          İncele ve Onayla
        </a>
      </p>
      <p style="color:#999;font-size:12px">Bu link ile request detaylarını görebilir, onaylayabilir veya reddedebilirsiniz.</p>
    </div>`;

  await transporter.sendMail({
    from: process.env.SMTP_FROM,
    to: process.env.APPROVER_EMAIL,
    cc: buildCc(process.env.REQUESTER_EMAIL),
    subject: `${req.emergencyCab ? '🚨 [ACİL] ' : ''}[Deploy Request #${req.id}] ${req.application.name} → ${req.environment.name} (${req.branch})`,
    html,
  });
}

export async function sendAssignmentMail(
  req: FullRequest & { assignee: { name: string; email: string } | null }
) {
  if (!req.assignee) return;
  const detailUrl = `${process.env.APP_URL}/requests/${req.id}`;

  const cabRow = req.cabRequired
    ? `<tr><td style="padding:4px 12px;color:#666">CAB Onayı</td><td style="padding:4px 12px">${req.cabApproved ? '✅ Gerekli — alındı' : '⏳ Gerekli — henüz alınmadı'}</td></tr>`
    : '';

  const emergencyRow = req.emergencyCab
    ? `<tr><td style="padding:4px 12px;color:#666">Acil</td><td style="padding:4px 12px;color:#b91c1c"><b>🚨 ACİL DEPLOYMENT</b></td></tr>`
    : '';

  const html = `
    <div style="font-family:Segoe UI,Arial,sans-serif;max-width:560px">
      <h2 style="color:#059669">Request #${req.id} size atandı</h2>
      <table style="border-collapse:collapse;font-size:14px;background:#f8fafc;border-radius:8px;width:100%">
        <tr><td style="padding:4px 12px;color:#666">Uygulama</td><td style="padding:4px 12px"><b>${req.application.name}</b></td></tr>
        <tr><td style="padding:4px 12px;color:#666">Ortam</td><td style="padding:4px 12px"><b>${req.environment.name}</b></td></tr>
        <tr><td style="padding:4px 12px;color:#666">Branch</td><td style="padding:4px 12px"><code>${req.branch}</code></td></tr>
        <tr><td style="padding:4px 12px;color:#666">Release Tarihi</td><td style="padding:4px 12px">${req.releaseDate.toLocaleDateString('tr-TR')}</td></tr>
        ${cabRow}
        ${emergencyRow}
        ${req.assignmentNote ? `<tr><td style="padding:4px 12px;color:#666">Not</td><td style="padding:4px 12px">${req.assignmentNote}</td></tr>` : ''}
      </table>
      <p style="margin-top:20px">
        <a href="${detailUrl}" style="background:#059669;color:#fff;padding:10px 24px;border-radius:8px;text-decoration:none;font-size:14px">
          Detayları Gör
        </a>
      </p>
      <p style="color:#999;font-size:12px">CC: onaylayan ve request sahibi bilgilendirme amaçlı eklendi.</p>
    </div>`;

  await transporter.sendMail({
    from: process.env.SMTP_FROM,
    to: req.assignee.email,
    cc: buildCc(req.approvedBy, process.env.REQUESTER_EMAIL),
    subject: `${req.emergencyCab ? '🚨 [ACİL] ' : ''}[Atama] Deploy Request #${req.id}: ${req.application.name} → ${req.environment.name}`,
    html,
  });
}