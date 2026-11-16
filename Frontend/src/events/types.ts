export interface PublicEvent {
  id: string;
  association: { id: string; name: string; slug: string };
  title: string;
  description: string;
  location: string;
  startsAt: string;
  endsAt: string;
  timeZone: string;
  registrationDeadline: string;
  capacity: number | null;
  status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED';
}
export interface OrganizerEvent {
  event: PublicEvent;
  projectId: string | null;
  updatedAt: string;
}
export interface EventPage {
  items: PublicEvent[];
  total: number;
  page: number;
  size: number;
}
export interface PublicAssociation {
  id: string;
  name: string;
  slug: string;
  description?: string;
  website?: string | null;
}

export function eventTime(value: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone,
  }).format(new Date(value));
}

// Date filters use UTC days explicitly; event cards retain each event's timezone.
export function discoveryQuery(associationId: string, from: string, to: string, page: number): string {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  if (associationId) query.set('associationId', associationId);
  if (from) query.set('from', `${from}T00:00:00Z`);
  if (to) {
    const exclusive = new Date(`${to}T00:00:00Z`);
    exclusive.setUTCDate(exclusive.getUTCDate() + 1);
    query.set('to', Number.isFinite(exclusive.getTime()) ? exclusive.toISOString() : to);
  }
  return query.toString();
}
