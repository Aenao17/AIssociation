import { IonContent, IonPage } from '@ionic/react';
import { useEffect, useState } from 'react';
import type { FormEvent, ReactElement } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';
import BrandHeader from '../components/BrandHeader';
import type { ProjectOverview } from '../projects/types';
import type { Workspace } from '../workspaces/types';

import { statusLabels } from './types';
import type { AssigneeOption, Task, TaskStatus } from './types';
import '../pages/Identity.css';
import '../projects/Projects.css';
import './Tasks.css';

const statuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];

function errorText(failure: unknown): string {
  if (failure instanceof ApiError) return failure.message;
  return 'Unable to reach the server. Please try again.';
}

function toInputDate(value: string): string {
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function toIsoDate(value: string): string {
  return new Date(value).toISOString();
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString();
}

function optionLabel(option: AssigneeOption): string {
  if (option.departmentName) return `${option.displayName} · ${option.departmentName}`;
  return option.displayName;
}

interface TaskFormValues {
  title: string;
  description: string;
  assigneeUserId: string;
  dueAt: string;
}

interface TaskFormProps {
  initial: TaskFormValues;
  options: AssigneeOption[];
  extraAssignee?: { userId: string; name: string };
  busy: boolean;
  submitLabel: string;
  onSubmit: (values: TaskFormValues) => Promise<boolean>;
  onCancel?: () => void;
}

function TaskForm(props: TaskFormProps): ReactElement {
  const [title, setTitle] = useState(props.initial.title);
  const [description, setDescription] = useState(props.initial.description);
  const [assigneeUserId, setAssigneeUserId] = useState(props.initial.assigneeUserId);
  const [dueAt, setDueAt] = useState(props.initial.dueAt);

  async function submit(event: FormEvent): Promise<void> {
    event.preventDefault();
    const saved = await props.onSubmit({ title, description, assigneeUserId, dueAt });
    if (saved && !props.onCancel) {
      setTitle('');
      setDescription('');
      setAssigneeUserId('');
      setDueAt('');
    }
  }

  return (
    <form onSubmit={submit}>
      <fieldset disabled={props.busy}>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
        </label>
        <label>
          Description
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={5000} />
        </label>
        <label>
          Assignee
          <select value={assigneeUserId} onChange={(e) => setAssigneeUserId(e.target.value)} required>
            <option value="">Choose a team member</option>
            {props.extraAssignee && (
              <option value={props.extraAssignee.userId}>{props.extraAssignee.name} (no longer on the team)</option>
            )}
            {props.options.map((option) => (
              <option key={option.userId} value={option.userId}>
                {optionLabel(option)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Deadline
          <input type="datetime-local" value={dueAt} onChange={(e) => setDueAt(e.target.value)} required />
        </label>
        <div className="identity-actions">
          <button type="submit" className="identity-primary">
            {props.submitLabel}
          </button>
          {props.onCancel && (
            <button type="button" className="identity-secondary" onClick={props.onCancel}>
              Cancel
            </button>
          )}
        </div>
      </fieldset>
    </form>
  );
}

interface TaskCardProps {
  task: Task;
  options: AssigneeOption[];
  busy: boolean;
  now: number;
  onStatus: (task: Task, status: TaskStatus) => void;
  onSave: (task: Task, values: TaskFormValues) => Promise<boolean>;
  onDelete: (task: Task) => void;
}

function TaskCard({ task, options, busy, now, onStatus, onSave, onDelete }: TaskCardProps): ReactElement {
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const overdue = task.status !== 'DONE' && new Date(task.dueAt).getTime() < now;
  const assigneeListed = options.some((option) => option.userId === task.assigneeUserId);

  async function save(values: TaskFormValues): Promise<boolean> {
    const saved = await onSave(task, values);
    if (saved) setEditing(false);
    return saved;
  }

  if (editing) {
    return (
      <li className="task-card">
        <TaskForm
          initial={{
            title: task.title,
            description: task.description ?? '',
            assigneeUserId: task.assigneeUserId,
            dueAt: toInputDate(task.dueAt),
          }}
          options={options}
          extraAssignee={assigneeListed ? undefined : { userId: task.assigneeUserId, name: task.assigneeName }}
          busy={busy}
          submitLabel="Save task"
          onSubmit={save}
          onCancel={() => setEditing(false)}
        />
      </li>
    );
  }

  return (
    <li className="task-card">
      <h3>{task.title}</h3>
      {task.description && <p className="project-prose">{task.description}</p>}
      <p>
        Assigned to <strong>{task.assigneeName}</strong>
      </p>
      <p className={overdue ? 'task-overdue' : undefined}>
        Due {formatDate(task.dueAt)}
        {overdue && ' · overdue'}
      </p>
      {task.canChangeStatus ? (
        <label>
          Status
          <select value={task.status} disabled={busy} onChange={(e) => onStatus(task, e.target.value as TaskStatus)}>
            {statuses.map((status) => (
              <option key={status} value={status}>
                {statusLabels[status]}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <span className="project-badge">{statusLabels[task.status]}</span>
      )}
      {task.canManage && (
        <div className="identity-actions">
          <button type="button" className="identity-secondary" disabled={busy} onClick={() => setEditing(true)}>
            Edit
          </button>
          {confirmDelete ? (
            <>
              <button type="button" className="identity-primary" disabled={busy} onClick={() => onDelete(task)}>
                Confirm delete
              </button>
              <button type="button" className="identity-secondary" onClick={() => setConfirmDelete(false)}>
                Keep task
              </button>
            </>
          ) : (
            <button type="button" className="identity-secondary" disabled={busy} onClick={() => setConfirmDelete(true)}>
              Delete
            </button>
          )}
        </div>
      )}
    </li>
  );
}

export default function ProjectTasksPage(): ReactElement {
  const { slug = '', projectId = '' } = useParams<{ slug: string; projectId: string }>();
  const { user, request } = useAuth();
  const [associationId, setAssociationId] = useState('');
  const [projectName, setProjectName] = useState('');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [options, setOptions] = useState<AssigneeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);
  const [loadedAt, setLoadedAt] = useState(0);

  const projectPath = `/api/associations/${associationId}/projects/${projectId}`;
  const slugPath = `/workspaces/${encodeURIComponent(slug)}`;

  useEffect(() => {
    if (!user) return;
    let active = true;
    async function load(): Promise<void> {
      try {
        const workspace = await request<Workspace>(`/api/workspaces/${encodeURIComponent(slug)}`);
        const base = `/api/associations/${workspace.associationId}/projects/${projectId}`;
        const [overview, taskList, assignees] = await Promise.all([
          request<ProjectOverview>(base),
          request<Task[]>(`${base}/tasks`),
          request<AssigneeOption[]>(`${base}/tasks/assignee-options`),
        ]);
        if (!active) return;
        setAssociationId(workspace.associationId);
        setProjectName(overview.project.name);
        setTasks(taskList);
        setOptions(assignees);
        setLoadedAt(Date.now());
        setError('');
      } catch (failure) {
        if (!active) return;
        setError(errorText(failure));
        setTasks([]);
        setOptions([]);
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [user, request, slug, projectId, reload]);

  async function run(action: () => Promise<void>, message: string): Promise<boolean> {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(message);
      setReload((value) => value + 1);
      return true;
    } catch (failure) {
      setError(errorText(failure));
      return false;
    } finally {
      setBusy(false);
    }
  }

  function body(values: TaskFormValues) {
    return JSON.stringify({
      title: values.title,
      description: values.description,
      assigneeUserId: values.assigneeUserId,
      dueAt: toIsoDate(values.dueAt),
    });
  }

  function createTask(values: TaskFormValues): Promise<boolean> {
    return run(async () => {
      await request(`${projectPath}/tasks`, { method: 'POST', body: body(values) });
    }, 'Task created.');
  }

  function saveTask(task: Task, values: TaskFormValues): Promise<boolean> {
    return run(async () => {
      await request(`${projectPath}/tasks/${task.id}`, { method: 'PUT', body: body(values) });
    }, 'Task saved.');
  }

  function changeStatus(task: Task, status: TaskStatus): void {
    void run(async () => {
      await request(`${projectPath}/tasks/${task.id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      });
    }, `Status changed to ${statusLabels[status]}.`);
  }

  function deleteTask(task: Task): void {
    void run(async () => {
      await request(`${projectPath}/tasks/${task.id}`, { method: 'DELETE' });
    }, 'Task deleted.');
  }

  if (!user) return <Navigate to="/login" replace />;

  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell projects-shell">
          <BrandHeader wide />
          <nav className="project-nav" aria-label="Workspace navigation">
            <Link to={slugPath}>Workspace</Link>
            <Link to={`${slugPath}/projects`}>Projects</Link>
            <Link to={`${slugPath}/projects/${projectId}`}>Back to project</Link>
          </nav>
          <header className="project-heading">
            <p className="identity-eyebrow">{projectName || 'Project'}</p>
            <h1>Project tasks</h1>
            <p>You see your own tasks and the tasks of the people who report to you.</p>
            <button
              type="button"
              className="identity-secondary"
              disabled={busy}
              onClick={() => setReload((value) => value + 1)}
            >
              Refresh tasks
            </button>
          </header>
          {error && (
            <p className="identity-error" role="alert">
              {error}
            </p>
          )}
          {notice && (
            <p className="identity-notice" role="status">
              {notice}
            </p>
          )}
          {loading ? (
            <p role="status">Loading tasks…</p>
          ) : (
            <>
              {options.length > 0 && (
                <section className="project-panel project-wide" aria-labelledby="new-task-title">
                  <h2 id="new-task-title">New task</h2>
                  <TaskForm
                    initial={{ title: '', description: '', assigneeUserId: '', dueAt: '' }}
                    options={options}
                    busy={busy}
                    submitLabel="Create task"
                    onSubmit={createTask}
                  />
                </section>
              )}
              <div className="task-board">
                {statuses.map((status) => {
                  const column = tasks.filter((task) => task.status === status);
                  return (
                    <section key={status} className="project-panel" aria-labelledby={`column-${status}`}>
                      <h2 id={`column-${status}`}>
                        {statusLabels[status]} ({column.length})
                      </h2>
                      {column.length === 0 && <p>No tasks here.</p>}
                      <ul className="project-list">
                        {column.map((task) => (
                          <TaskCard
                            key={`${task.id}:${task.updatedAt}`}
                            task={task}
                            options={options}
                            busy={busy}
                            now={loadedAt}
                            onStatus={changeStatus}
                            onSave={saveTask}
                            onDelete={deleteTask}
                          />
                        ))}
                      </ul>
                    </section>
                  );
                })}
              </div>
            </>
          )}
        </main>
      </IonContent>
    </IonPage>
  );
}
