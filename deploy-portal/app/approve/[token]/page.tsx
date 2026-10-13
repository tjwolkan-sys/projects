import { prisma } from '@/lib/db';
import { StatusBadge } from '@/components/StatusBadge';
import { ApprovalActions } from '@/components/ApprovalActions';

export default async function ApprovePage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;

  const [req, assignees] = await Promise.all([
    prisma.deploymentRequest.findUnique({
      where: { approvalToken: token },
      include: { environment: true, application: true, scripts: true },
    }),
    prisma.assignee.findMany({ where: { active: true }, orderBy: { name: 'asc' } }),
  ]);

  if (!req) {
    return (
      <main className="max-w-xl">
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-red-700">
          Geçersiz veya süresi dolmuş onay linki.
        </div>
      </main>
    );
  }

  return (
    <main className="max-w-xl space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Onay: Request #{req.id}</h1>
        <StatusBadge status={req.status} />
      </div>

      <div className="bg-white rounded-xl shadow-sm p-6 grid grid-cols-2 gap-y-4 text-sm">
        <div className="text-gray-500">Uygulama</div>
        <div className="font-medium">{req.application.name}</div>
        <div className="text-gray-500">Ortam</div>
        <div className="font-medium">{req.environment.name}</div>
        <div className="text-gray-500">Branch</div>
        <div className="font-mono text-xs">{req.branch}</div>
        <div className="text-gray-500">Release Tarihi</div>
        <div>{new Date(req.releaseDate).toLocaleDateString('tr-TR')}</div>
        <div className="text-gray-500">Requester</div>
        <div>{req.requester}</div>
      </div>

      <div className="bg-white rounded-xl shadow-sm p-6">
        <h2 className="text-sm font-semibold mb-3">DB Scriptleri</h2>
        {!req.hasScript && <p className="text-sm text-gray-400">Bu release için script yok.</p>}
        {req.scripts.map((s) => (
          <div key={s.id} className="flex items-center justify-between py-2 border-b last:border-0 text-sm">
            <span>
              <span className={`px-2 py-0.5 rounded text-xs font-medium mr-2 ${s.dbType === 'MAIN' ? 'bg-indigo-100 text-indigo-700' : 'bg-emerald-100 text-emerald-700'}`}>
                {s.dbType === 'MAIN' ? 'MainDB' : 'CampDB'}
              </span>
              {s.fileName}
            </span>
            <a href={`/api/scripts/${s.id}`} className="text-indigo-600 hover:underline text-xs">İndir</a>
          </div>
        ))}
      </div>

      {req.status === 'PENDING' ? (
        <ApprovalActions token={token} assignees={assignees} />
      ) : (
        <div className="bg-gray-50 border rounded-xl p-4 text-sm text-gray-600">
          Bu request {req.approvedAt ? new Date(req.approvedAt).toLocaleString('tr-TR') + ' tarihinde' : ''} işlem görmüş.
          {req.rejectReason && <div className="mt-1 text-red-600">Sebep: {req.rejectReason}</div>}
        </div>
      )}
    </main>
  );
}