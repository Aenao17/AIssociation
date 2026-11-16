-- FR-08/FR-09 project tasks. Association scope is derived from the project.
-- Visibility follows the live reporting tree in project_memberships; it is
-- evaluated by TaskService, never stored on the task.
CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    assignee_user_id UUID NOT NULL REFERENCES app_users(id),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(5000),
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX tasks_project_assignee_idx ON tasks(project_id, assignee_user_id);
CREATE INDEX tasks_assignee_idx ON tasks(assignee_user_id);
