import { prisma } from '@/lib/db';
import { readFile } from 'fs/promises';
import path from 'path';

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const script = await prisma.scriptFile.findUnique({ where: { id: Number(id) } });
  if (!script) return new Response('Not found', { status: 404 });

  const data = await readFile(path.join(process.cwd(), 'uploads', script.storedName));
  return new Response(data, {
    headers: {
      'Content-Disposition': `attachment; filename="${encodeURIComponent(script.fileName)}"`,
      'Content-Type': 'application/octet-stream',
    },
  });
}