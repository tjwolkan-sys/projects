// Normal deployment günleri: Salı (2), Perşembe (4)
export const DEPLOY_DAYS = [2, 4];
export const DEPLOY_DAYS_LABEL = 'Salı ve Perşembe';

export function isDeployDay(dateStr: string): boolean {
  if (!dateStr) return false;
  const d = new Date(dateStr + 'T00:00:00');
  return DEPLOY_DAYS.includes(d.getDay());
}

// CampDB yalnızca bu uygulamalar için geçerli
export const CAMP_DB_APPS = ['PowerDialer'];

export function supportsCampDb(appName?: string | null): boolean {
  return Boolean(appName && CAMP_DB_APPS.includes(appName));
}