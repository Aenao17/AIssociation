-- FR-12 / FR-13 / FR-14: participant registration, cancellation and manual attendance.
--
-- One row per (event, participant), ever. Cancelling flips the row to CANCELLED and
-- registering again (while the window is open) flips it back to ACTIVE. That gives us:
--   * "no duplicate active registration" from a plain UNIQUE constraint (portable: H2 + PostgreSQL),
--   * "repeated attendance updates never duplicate records", because attendance lives on this row.
-- The seat count is simply COUNT(*) WHERE status = 'ACTIVE'; it is only ever read or changed
-- while the event row is locked FOR UPDATE (see RegistrationService), so it cannot be overbooked.
CREATE TABLE event_registrations (
    id UUID PRIMARY KEY,
    
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by_user_id UUID REFERENCES app_users(id),
    cancelled_by_organizer BOOLEAN NOT NULL DEFAULT FALSE,
    attendance VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED_IN' CHECK (attendance IN ('NOT_CHECKED_IN', 'CHECKED_IN')),
    checked_in_at TIMESTAMP WITH TIME ZONE,
    checked_in_by_user_id UUID REFERENCES app_users(id),
    CONSTRAINT event_registrations_one_per_user UNIQUE (event_id, user_id),
    CONSTRAINT event_registrations_cancel_state CHECK (
        (status = 'ACTIVE' AND cancelled_at IS NULL) OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)),
    CONSTRAINT event_registrations_attendance_state CHECK (
        (attendance = 'CHECKED_IN' AND checked_in_at IS NOT NULL) OR (attendance = 'NOT_CHECKED_IN' AND checked_in_at IS NULL)),
    CONSTRAINT event_registrations_checkin_needs_active CHECK (attendance = 'NOT_CHECKED_IN' OR status = 'ACTIVE')
);
CREATE INDEX event_registrations_event_idx ON event_registrations(event_id, status);
CREATE INDEX event_registrations_user_idx ON event_registrations(user_id);
