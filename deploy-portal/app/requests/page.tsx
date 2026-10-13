import { prisma } from '@/lib/db';
import { StatusBadge } from '@/components/StatusBadge';
import { DeleteButton } from '@/components/DeleteButton';
import Link from 'next/link';

export default async function Requests() {
  const requests = await prisma.deploymentRequest.findMany({
    include: { environment: true, application: true, scripts: true, assignee: true },
    orderBy: { createdAt: 'desc' },
  });

  return (
    <main>
      <div className="flex items-center justify-between mb-5">
        <h1 className="text-2xl font-bold">Requestlerim</h1>
        <Link href="/" className="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-indigo-700">
          + Yeni Request
        </Link>
      </div>

      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-left text-gray-500">
            <tr>
              <th className="px-5 py-3 font-medium">ID</th>
              <th className="px-5 py-3 font-medium">Uygulama</th>
              <th className="px-5 py-3 font-medium">Ortam</th>
              <th className="px-5 py-3 font-medium">Branch</th>
              <th className="px-5 py-3 font-medium">Tarih</th>
              <th className="px-5 py-3 font-medium">Script</th>
              <th className="px-5 py-3 font-medium">Atanan</th>
              <th className="px-5 py-3 font-medium">Status</th>
              <th className="px-5 py-3 font-medium">Aksiyon</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {requests.map((r) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-5 py-3 font-medium">#{r.id}</td>
                <td className="px-5 py-3">{r.application.name}</td>
                <td className="px-5 py-3">{r.environment.name}</td>
                <td className="px-5 py-3 font-mono text-xs">{r.branch}</td>
                <td className="px-5 py-3">{new Date(r.releaseDate).toLocaleDateString('tr-TR')}</td>
                <td className="px-5 py-3">{r.hasScript ? `${r.scripts.length} dosya` : '—'}</td>
                <td className="px-5 py-3">{r.assignee?.name ?? '—'}</td>
                <td className="px-5 py-3"><StatusBadge status={r.status} /></td>
                <td className="px-5 py-3">
                  <div className="flex gap-3">
                    <Link href={`/requests/${r.id}`} className="text-indigo-600 hover:underline text-xs">Detay</Link>
                    {r.status === 'PENDING' && (
                      <>
                        <Link href={`/requests/${r.id}/edit`} className="text-gray-600 hover:underline text-xs">Düzenle</Link>
                        <DeleteButton id={r.id} />
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {requests.length === 0 && (
              <tr>
                <td colSpan={9} className="px-5 py-10 text-center text-gray-400">Henüz request yok.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </main>
  );
}