import { prisma } from '@/lib/db';
import { RequestWizard } from '@/components/RequestWizard';
import Link from 'next/link';
import { Rocket } from 'lucide-react';
import { flags } from '@/lib/flags';

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-[#FDF2DC] text-[#9A6207]',
  APPROVED: 'bg-[#E1EEFB] text-[#1D5F9E]',
  IN_PROGRESS: 'bg-[#DEF5EC] text-[#0F6E56]',
  REJECTED: 'bg-[#FBE9E9] text-[#A32D2D]',
  TIMED_OUT: 'bg-black/6 text-[#8a8ea0]',
  COMPLETED: 'bg-[#EAF3DE] text-[#3F6212]',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  IN_PROGRESS: 'In Progress',
  REJECTED: 'Rejected',
  TIMED_OUT: 'Timed Out',
  COMPLETED: 'Completed',
};

export default async function Home() {
  const [environments, applications, recent] = await Promise.all([
    prisma.environment.findMany({ orderBy: { id: 'asc' } }),
    prisma.application.findMany({ orderBy: { name: 'asc' } }),
    prisma.deploymentRequest.findMany({
      include: { environment: true, application: true },
      orderBy: { createdAt: 'desc' },
      take: 4,
    }),
  ]);

  return (
    <div className="space-y-6">
      <RequestWizard 
          environments={environments} 
          applications={applications}
          cabEnabled={flags.cabWorkflow} 
      />

      {recent.length > 0 && (
        <section className="rounded-2xl border border-black/6 bg-white p-5 shadow-sm shadow-black/3">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="flex items-center gap-2 text-[14px] font-semibold text-[#1e2030]">
              <Rocket size={15} className="text-[#5850C7]" /> Son Requestler
            </h2>
            <Link href="/requests" className="text-xs font-medium text-[#5850C7] hover:underline">
              Tümünü gör →
            </Link>
          </div>
          <div className="grid gap-3 lg:grid-cols-2">
            {recent.map((r) => (
              <Link
                key={r.id}
                href={`/requests/${r.id}`}
                className="flex items-center justify-between rounded-xl border border-black/6 px-4 py-3 transition-colors hover:border-[#5850C7]/30 hover:bg-[#EEEDFB]/40"
              >
                <span className="min-w-0 truncate text-[13px] text-[#1e2030]">
                  <span className="font-medium">#{r.id}</span> {r.application.name} → {r.environment.name}
                  <span className="ml-2 font-mono text-[11px] text-[#b3b6c4]">{r.branch}</span>
                </span>
                <span
                  className={`ml-3 shrink-0 rounded-full px-2.5 py-1 text-[11px] font-medium ${STATUS_STYLES[r.status] ?? ''}`}
                >
                  {STATUS_LABELS[r.status] ?? r.status}
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}