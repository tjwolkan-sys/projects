const STYLES: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-blue-100 text-blue-700',
  IN_PROGRESS: 'bg-emerald-100 text-emerald-700',
  REJECTED: 'bg-red-100 text-red-700',
  TIMED_OUT: 'bg-gray-200 text-gray-600',
  COMPLETED: 'bg-green-100 text-green-700',
};

const LABELS: Record<string, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  IN_PROGRESS: 'In Progress',
  REJECTED: 'Rejected',
  TIMED_OUT: 'Timed Out',
  COMPLETED: 'Completed',
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${STYLES[status] ?? ''}`}>
      {LABELS[status] ?? status}
    </span>
  );
}