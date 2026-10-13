import { prisma } from '@/lib/db';
import { updateRequest } from '@/app/actions';
import { RequestForm } from '@/components/RequestForm';
import { notFound, redirect } from 'next/navigation';

export default async function EditRequest({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const req = await prisma.deploymentRequest.findUnique({
    where: { id: Number(id) },
    include: { scripts: true },
  });
  if (!req) notFound();
  if (req.status !== 'PENDING') redirect(`/requests/${req.id}`);

  const [environments, applications] = await Promise.all([
    prisma.environment.findMany({ orderBy: { id: 'asc' } }),
    prisma.application.findMany({ orderBy: { name: 'asc' } }),
  ]);

  const boundUpdate = updateRequest.bind(null, req.id);

  return (
    <main>
      <h1 className="text-2xl font-bold mb-4">Request #{req.id} Düzenle</h1>
      <RequestForm environments={environments} applications={applications} action={boundUpdate} initial={req} />
    </main>
  );
}