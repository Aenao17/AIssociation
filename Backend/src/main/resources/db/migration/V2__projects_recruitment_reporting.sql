CREATE TABLE projects (
    id UUID PRIMARY KEY,
    association_id UUID NOT NULL REFERENCES associations(id),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    recruitment_description VARCHAR(5000),
    recruitment_deadline TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((recruitment_description IS NULL) = (recruitment_deadline IS NULL))
);
CREATE INDEX projects_association_idx ON projects(association_id);

CREATE TABLE project_departments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    name VARCHAR(100) NOT NULL,
    UNIQUE (project_id, id),
    UNIQUE (project_id, name)
);

CREATE TABLE recruitment_positions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    department_id UUID NOT NULL,
    role_label VARCHAR(100) NOT NULL,
    UNIQUE (project_id, id),
    UNIQUE (project_id, department_id, role_label),
    FOREIGN KEY (project_id, department_id) REFERENCES project_departments(project_id, id)
);

CREATE TABLE project_memberships (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    association_membership_id UUID REFERENCES association_memberships(id) ON DELETE SET NULL,
    project_role VARCHAR(30) NOT NULL CHECK (project_role IN ('PROJECT_MANAGER', 'DEPARTMENT_MANAGER', 'MEMBER')),
    department_id UUID,
    position_id UUID,
    reporting_parent_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (project_id, id),
    UNIQUE (project_id, user_id),
    FOREIGN KEY (project_id, department_id) REFERENCES project_departments(project_id, id),
    FOREIGN KEY (project_id, position_id) REFERENCES recruitment_positions(project_id, id),
    FOREIGN KEY (project_id, reporting_parent_id) REFERENCES project_memberships(project_id, id),
    CHECK (reporting_parent_id IS NULL OR reporting_parent_id <> id),
    CHECK ((project_role = 'PROJECT_MANAGER' AND reporting_parent_id IS NULL)
        OR (project_role IN ('DEPARTMENT_MANAGER', 'MEMBER') AND reporting_parent_id IS NOT NULL AND department_id IS NOT NULL AND position_id IS NOT NULL))
);
CREATE INDEX project_memberships_parent_idx ON project_memberships(project_id, reporting_parent_id);
CREATE INDEX project_memberships_user_idx ON project_memberships(user_id);
CREATE INDEX project_memberships_association_member_idx ON project_memberships(association_membership_id);

CREATE TABLE project_applications (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    position_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES app_users(id),
    association_membership_id UUID REFERENCES association_memberships(id) ON DELETE SET NULL,
    motivation VARCHAR(5000) NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CLOSED_ACCEPTED_ELSEWHERE')),
    selected_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (project_id, position_id, user_id),
    UNIQUE (project_id, selected_user_id),
    FOREIGN KEY (project_id, position_id) REFERENCES recruitment_positions(project_id, id),
    -- Nullable mirror permits multiple pending applications but only one accepted
    -- application per account/project on both PostgreSQL and H2.
    CHECK ((status = 'ACCEPTED' AND selected_user_id IS NOT NULL AND selected_user_id = user_id)
        OR (status <> 'ACCEPTED' AND selected_user_id IS NULL))
);
CREATE INDEX project_applications_review_idx ON project_applications(project_id, status);
CREATE INDEX project_applications_user_idx ON project_applications(user_id);
