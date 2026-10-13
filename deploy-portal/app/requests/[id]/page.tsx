import { prisma } from '@/lib/db';
import { StatusBadge } from '@/components/StatusBadge';
import { StatusManager } from '@/components/StatusManager';
import Link from 'next/link';
import { notFound } from 'next/navigation';

export default async function RequestDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const req = await prisma.deploymentRequest.findUnique({
    where: { id: Number(id) },
    include: { environment: true, application: true, scripts: true },
  });
  if (!req) notFound();

  return (
    <main className="max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Request #{req.id}</h1>
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
        <div className="text-gray-500">Oluşturulma</div>
        <div>{new Date(req.createdAt).toLocaleString('tr-TR')}</div>
        <div className="text-gray-500">Requester</div>
        <div>{req.requester}</div>
      </div>

      {req.status === 'REJECTED' && req.rejectReason && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-sm">
          <div className="font-semibold text-red-700 mb-1">Reject Sebebi</div>
          <div className="text-red-600">{req.rejectReason}</div>
        </div>
      )}

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

      <StatusManager id={req.id} current={req.status} />

      <div className="flex gap-4 text-sm">
        {req.status === 'PENDING' && (
          <Link href={`/requests/${req.id}/edit`} className="text-indigo-600 hover:underline">Düzenle</Link>
        )}
        <Link href="/requests" className="text-gray-500 hover:underline">← Listeye dön</Link>
      </div>
    </main>
  );
}