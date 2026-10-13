import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();

async function main() {
  for (const name of ['TCELL', 'NONTCELL', 'MHRS', 'SET2']) {
    await prisma.environment.upsert({ where: { name }, update: {}, create: { name } });
  }


const assignees: [string, string][] = [
    ['Ahmet Yılmaz', 'ahmet.yilmaz@globalbilgi.com.tr'],
    ['Mehmet Demir', 'mehmet.demir@globalbilgi.com.tr'],
    ['Ayşe Kaya', 'ayse.kaya@globalbilgi.com.tr'],
  ];
  for (const [name, email] of assignees) {
    await prisma.assignee.upsert({ where: { email }, update: { name }, create: { name, email } });
  }

  const apps: [string, boolean][] = [
    ['PowerDialer', false],
    ['GBDashboard', false],
    ['RealtimeStats - Backend', true], // sadece MHRS
    ['MHRSDashboard - Frontend', false],
    ['WebPhone', false],
    ['QSS', false],
    ['QueueEngine', false],
    ['AIDEngine', false],
    ['GoMatcher', false],
  ];
  for (const [name, mhrsOnly] of apps) {
    await prisma.application.upsert({ where: { name }, update: { mhrsOnly }, create: { name, mhrsOnly } });
  }
  console.log('Seed OK');
}

main().finally(() => prisma.$disconnect());