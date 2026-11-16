-- Local allocation for Yasmine's implementation; reconcile with Raul before shared application.
ALTER TABLE associations ADD COLUMN description VARCHAR(5000) NOT NULL DEFAULT '';
ALTER TABLE associations ADD COLUMN website VARCHAR(500);
ALTER TABLE projects ADD CONSTRAINT projects_id_association_unique UNIQUE (id, association_id);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    association_id UUID NOT NULL REFERENCES associations(id),
    project_id UUID,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    location VARCHAR(500) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    time_zone VARCHAR(100) NOT NULL,
    registration_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (project_id, association_id) REFERENCES projects(id, association_id),
    CHECK (ends_at > starts_at),
    CHECK (registration_deadline <= starts_at),
    CHECK (capacity IS NULL OR capacity > 0),
    CHECK (LENGTH(TRIM(title)) > 0 AND LENGTH(TRIM(description)) > 0 AND LENGTH(TRIM(location)) > 0)
);
CREATE INDEX events_public_date_idx ON events(status, starts_at, id);
CREATE INDEX events_association_idx ON events(association_id, status, starts_at, id);
CREATE INDEX events_project_idx ON events(project_id);
