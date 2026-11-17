import { ApiError } from '../auth/client';

export type RegistrationState = 'NOT_REGISTERED' | 'ACTIVE' | 'CANCELLED';
export type Attendance = 'NOT_CHECKED_IN' | 'CHECKED_IN';

export interface Availability {
  eventId: string;
  eventStatus: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED';
  capacity: number | null;
  activeRegistrations: number;
  seatsRemaining: number | null;
  registrationOpen: boolean;
  registrationDeadline: string;
}

export interface MyRegistration {
  eventId: string;
  status: RegistrationState;
  registeredAt: string | null;
  cancelledAt: string | null;
  cancelledByOrganizer: boolean;
  canRegister: boolean;
  canCancel: boolean;
  availability: Availability;
}

export interface RosterEntry {
  registrationId: string;
  userId: string;
  displayName: string;
  email: string;
  status: 'ACTIVE' | 'CANCELLED';
  registeredAt: string;
  cancelledAt: string | null;
  cancelledByOrganizer: boolean;
  attendance: Attendance;
  checkedInAt: string | null;
}

export interface Roster {
  eventId: string;
  eventTitle: string;
  eventStatus: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED';
  capacity: number | null;
  activeRegistrations: number;
  checkedIn: number;
  items: RosterEntry[];
}

const messages: Record<string, string> = {
  EVENT_FULL: 'This event is full: there are no places left.',
  REGISTRATION_CLOSED: 'Registration is closed: the deadline has passed.',
  ALREADY_REGISTERED: 'You are already registered for this event.',
  ALREADY_CANCELLED: 'This registration is already cancelled.',
  CANCELLATION_CLOSED: 'The deadline has passed. Ask an organizer to cancel your registration.',
  REGISTRATION_CANCELLED_BY_ORGANIZER: 'An organizer cancelled your registration. Contact them to register again.',
  REGISTRATION_NOT_FOUND: 'No registration was found.',
  REGISTRATION_NOT_ACTIVE: 'Only an active registration can be checked in.',
  EVENT_NOT_OPEN: 'This event is not open for this action.',
  EVENT_NOT_FOUND: 'Event not found or not published.',
  MEMBERSHIP_REQUIRED: 'You are not a member of this association.',
  PROJECT_MANAGER_REQUIRED: 'Only the project manager or an association administrator can see this roster.',
  ORGANIZER_REQUIRED: 'Only association administrators can manage this event.',
};

export function registrationErrorText(failure: unknown): string {
  if (failure instanceof ApiError) {
    if (failure.status === 401) return 'Your session has expired. Sign in again.';
    return messages[failure.code] ?? failure.message;
  }
  return 'Unable to reach the server. Please try again.';
}

export function seatsText(availability: Availability): string {
  if (availability.capacity === null || availability.seatsRemaining === null) {
    return `Unlimited places · ${availability.activeRegistrations} registered`;
  }
  return `${availability.seatsRemaining} of ${availability.capacity} places left`;
}

export function countRoster(items: RosterEntry[]): { activeRegistrations: number; checkedIn: number } {
  return {
    activeRegistrations: items.filter((item) => item.status === 'ACTIVE').length,
    checkedIn: items.filter((item) => item.attendance === 'CHECKED_IN').length,
  };
}
