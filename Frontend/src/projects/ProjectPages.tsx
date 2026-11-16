import { IonContent, IonPage } from '@ionic/react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent, ReactElement, ReactNode } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';
import BrandHeader from '../components/BrandHeader';
import type { Workspace } from '../workspaces/types';

import { applicationStatus } from './types';
import type { MemberOption, Position, ProjectApplication, ProjectOverview, ProjectSummary, TeamMember } from './types';
import '../pages/Identity.css';
import './Projects.css';

const failureMessage = (failure: unknown): string =>
  failure instanceof ApiError ? failure.message : 'Unable to reach the server. Please try again.';
const json = (method: string, body?: unknown): RequestInit => ({
  method,
  ...(body === undefined ? {} : { body: JSON.stringify(body) }),
});
const localDateInput = (value: string | null): string => {
  if (!value) return '';
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
};

function Shell({ slug, children }: { slug: string; children: ReactNode }): ReactElement {
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell projects-shell">
          <BrandHeader wide />
          <nav className="project-nav" aria-label="Workspace navigation">
            <Link to={`/workspaces/${encodeURIComponent(slug)}`}>Workspace</Link>
            <Link to={`/workspaces/${encodeURIComponent(slug)}/projects`}>Projects</Link>
          </nav>
          {children}
        </main>
      </IonContent>
    </IonPage>
  );
}

function ProjectList({ slug }: { slug: string }): ReactElement {
  const { user, request } = useAuth();
  const navigate = useNavigate();
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [members, setMembers] = useState<MemberOption[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [managerUserIds, setManagers] = useState<string[]>([]);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const current = await request<Workspace>(`/api/workspaces/${encodeURIComponent(slug)}`);
        const [items, options] = await Promise.all([
          request<ProjectSummary[]>(`/api/associations/${current.associationId}/projects`),
          current.role === 'ADMIN'
            ? request<MemberOption[]>(`/api/associations/${current.associationId}/members`)
            : Promise.resolve([]),
        ]);
        if (active) {
          setWorkspace(current);
          setProjects(items);
          setMembers(options);
          setError('');
        }
      } catch (failure) {
        if (active) {
          setError(failureMessage(failure));
          setWorkspace(null);
          setProjects([]);
          setMembers([]);
        }
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [request, slug, reload]);

  async function create(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (!workspace) return;
    setBusy(true);
    setError('');
    try {
      const result = await request<ProjectOverview>(
        `/api/associations/${workspace.associationId}/projects`,
        json('POST', { name, description, managerUserIds }),
      );
      navigate(`/workspaces/${encodeURIComponent(slug)}/projects/${result.project.id}`);
    } catch (failure) {
      setError(failureMessage(failure));
      if (failure instanceof ApiError && failure.status === 403) setWorkspace(null);
    } finally {
      setBusy(false);
    }
  }

  if (!user) return <Navigate to="/login" replace />;
  return (
    <Shell slug={slug}>
      <header className="project-heading">
        <p className="identity-eyebrow">{workspace?.name ?? 'Association workspace'}</p>
        <h1>Projects & recruitment</h1>
        <button
          type="button"
          className="identity-secondary"
          disabled={busy}
          onClick={() => setReload((value) => value + 1)}
        >
          Refresh projects
        </button>
        <p>Find a position, apply, and build your association’s project team.</p>
      </header>
      {error && (
        <p className="identity-error" role="alert">
          {error}
        </p>
      )}
      {loading ? (
        <p role="status">Loading projects…</p>
      ) : (
        <div className="project-columns">
          <section className="project-panel" aria-labelledby="projects-title">
            <h2 id="projects-title">Association projects</h2>
            {!projects.length && <p>No projects yet. An association administrator can create the first one.</p>}
            <ul className="project-list">
              {projects.map((project) => (
                <li key={project.id}>
                  <h3>
                    <Link to={`/workspaces/${encodeURIComponent(slug)}/projects/${project.id}`}>{project.name}</Link>
                  </h3>
                  <p className="project-prose">{project.description}</p>
                  {project.canManage && <span className="project-badge">You manage this project</span>}
                  {project.recruitmentDeadline && (
                    <p>Recruitment deadline: {new Date(project.recruitmentDeadline).toLocaleString()}</p>
                  )}
                </li>
              ))}
            </ul>
          </section>
          {workspace?.role === 'ADMIN' && (
            <section className="project-panel" aria-labelledby="create-project-title">
              <h2 id="create-project-title">Create a project</h2>
              <p>Appoint at least one existing association member as project manager.</p>
              <form
                onSubmit={(event) => {
                  void create(event);
                }}
              >
                <fieldset disabled={busy}>
                  <label>
                    Project name
                    <input required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} />
                  </label>
                  <label>
                    Description
                    <textarea
                      required
                      maxLength={5000}
                      value={description}
                      onChange={(event) => setDescription(event.target.value)}
                    />
                  </label>
                  <label>
                    Project managers
                    <select
                      required
                      multiple
                      size={Math.min(6, Math.max(2, members.length))}
                      value={managerUserIds}
                      onChange={(event) =>
                        setManagers(Array.from(event.target.selectedOptions, (option) => option.value))
                      }
                    >
                      {members.map((member) => (
                        <option key={member.userId} value={member.userId}>
                          {member.displayName}
                        </option>
                      ))}
                    </select>
                  </label>
                  <p className="identity-help">
                    Use Ctrl or Command to select several managers. Position titles do not grant management access.
                  </p>
                  <button className="identity-primary" type="submit">
                    {busy ? 'Creating…' : 'Create project'}
                  </button>
                </fieldset>
              </form>
            </section>
          )}
        </div>
      )}
    </Shell>
  );
}

type RunAction = (operation: () => Promise<unknown>, notice: string) => Promise<void>;

function ApplicationReview({
  application,
  positions,
  team,
  base,
  busy,
  run,
  request,
}: {
  application: ProjectApplication;
  positions: Position[];
  team: TeamMember[];
  base: string;
  busy: boolean;
  run: RunAction;
  request: ReturnType<typeof useAuth>['request'];
}): ReactElement {
  const [parent, setParent] = useState('');
  const department = positions.find((position) => position.id === application.positionId)?.departmentId;
  const supervisors = team.filter(
    (member) =>
      member.active &&
      (member.projectRole === 'PROJECT_MANAGER' ||
        (member.projectRole === 'DEPARTMENT_MANAGER' && member.departmentId === department)),
  );
  return (
    <li className="project-record">
      <h3>
        {application.displayName} · {application.positionLabel} / {application.departmentName}
      </h3>
      <p className="project-prose">{application.motivation}</p>
      <p className="project-badge">{applicationStatus[application.status]}</p>
      {application.status === 'PENDING' && (
        <fieldset disabled={busy}>
          <label>
            Reporting manager for {application.displayName}
            <select value={parent} onChange={(event) => setParent(event.target.value)}>
              <option value="">Choose a manager</option>
              {supervisors.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.displayName} (
                  {member.projectRole === 'PROJECT_MANAGER' ? 'Project manager' : 'Department manager'})
                </option>
              ))}
            </select>
          </label>
          <div className="identity-actions">
            <button
              type="button"
              className="identity-primary"
              disabled={!parent}
              onClick={() => {
                void run(
                  () =>
                    request(
                      `${base}/applications/${application.id}/accept`,
                      json('POST', { reportingParentMembershipId: parent }),
                    ),
                  'Application accepted. Team membership and reporting assignment saved; other pending applications for this member are closed.',
                );
              }}
            >
              Accept application
            </button>
            <button
              type="button"
              className="identity-secondary"
              onClick={() => {
                void run(
                  () => request(`${base}/applications/${application.id}/reject`, json('POST')),
                  'Application rejected.',
                );
              }}
            >
              Reject application
            </button>
          </div>
        </fieldset>
      )}
    </li>
  );
}

function TeamRow({
  member,
  team,
  canManage,
  base,
  busy,
  run,
  request,
}: {
  member: TeamMember;
  team: TeamMember[];
  canManage: boolean;
  base: string;
  busy: boolean;
  run: RunAction;
  request: ReturnType<typeof useAuth>['request'];
}): ReactElement {
  const [parent, setParent] = useState(member.reportingParentMembershipId ?? '');
  const supervisors = team.filter(
    (candidate) =>
      candidate.active &&
      candidate.id !== member.id &&
      (candidate.projectRole === 'PROJECT_MANAGER' ||
        (member.projectRole === 'MEMBER' &&
          candidate.projectRole === 'DEPARTMENT_MANAGER' &&
          candidate.departmentId === member.departmentId)),
  );
  const manager = team.find((candidate) => candidate.id === member.reportingParentMembershipId);
  return (
    <li className="project-record">
      <h3>
        {member.displayName}
        {!member.active && ' · Access revoked'}
      </h3>
      <p>
        {member.projectRole.replaceAll('_', ' ').toLowerCase()}
        {member.departmentName && ` · ${member.departmentName}`}
        {member.positionLabel && ` · ${member.positionLabel}`}
      </p>
      <p>
        {member.reportingParentMembershipId
          ? `Reports to ${manager?.displayName ?? 'unavailable manager'}${manager && !manager.active ? ' (inactive — reassign)' : ''}`
          : 'Root project manager'}
      </p>
      {canManage && member.active && member.projectRole !== 'PROJECT_MANAGER' && (
        <fieldset disabled={busy}>
          <label>
            Supervisor for {member.displayName}
            <select value={parent} onChange={(event) => setParent(event.target.value)}>
              <option value="">Choose a manager</option>
              {supervisors.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.displayName}
                </option>
              ))}
            </select>
          </label>
          <div className="identity-actions">
            <button
              type="button"
              className="identity-secondary"
              disabled={!parent}
              onClick={() => {
                void run(
                  () =>
                    request(
                      `${base}/team/${member.id}/reporting`,
                      json('PATCH', { reportingParentMembershipId: parent }),
                    ),
                  'Reporting assignment updated.',
                );
              }}
            >
              Save supervisor
            </button>
            {member.projectRole === 'MEMBER' && (
              <button
                type="button"
                className="identity-secondary"
                onClick={() => {
                  void run(
                    () => request(`${base}/department-managers/${member.id}`, json('PUT')),
                    'Department manager appointed and assigned to you.',
                  );
                }}
              >
                Appoint department manager
              </button>
            )}
          </div>
        </fieldset>
      )}
    </li>
  );
}

function ProjectDetail({ slug, projectId }: { slug: string; projectId: string }): ReactElement {
  const { user, request } = useAuth();
  const [overview, setOverview] = useState<ProjectOverview | null>(null);
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [team, setTeam] = useState<TeamMember[]>([]);
  const [mine, setMine] = useState<ProjectApplication[]>([]);
  const [applications, setApplications] = useState<ProjectApplication[]>([]);
  const [options, setOptions] = useState<MemberOption[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const revision = useRef(0);
  const base = workspace
    ? `/api/associations/${workspace.associationId}/projects/${encodeURIComponent(projectId)}`
    : '';
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [callDescription, setCallDescription] = useState('');
  const [deadline, setDeadline] = useState('');
  const [roleLabel, setRoleLabel] = useState('');
  const [departmentName, setDepartmentName] = useState('');
  const [editingPosition, setEditingPosition] = useState<string | null>(null);
  const [positionId, setPosition] = useState('');
  const [motivation, setMotivation] = useState('');
  const [managerUserId, setManagerUserId] = useState('');
  const [checkedAt, setCheckedAt] = useState(() => Date.now());

  const load = useCallback(async () => {
    const version = ++revision.current;
    try {
      const current = await request<Workspace>(`/api/workspaces/${encodeURIComponent(slug)}`);
      const path = `/api/associations/${current.associationId}/projects/${encodeURIComponent(projectId)}`;
      const data = await request<ProjectOverview>(`${path}/recruitment`);
      const [own, members, queue, memberOptions] = await Promise.all([
        request<ProjectApplication[]>(`${path}/applications/mine`),
        data.project.canOpen ? request<TeamMember[]>(`${path}/team`) : Promise.resolve([]),
        data.project.canManage ? request<ProjectApplication[]>(`${path}/applications`) : Promise.resolve([]),
        data.associationAdmin ? request<MemberOption[]>(`${path}/member-options`) : Promise.resolve([]),
      ]);
      if (version !== revision.current) return;
      setWorkspace(current);
      setOverview(data);
      setMine(own);
      setTeam(members);
      setApplications(queue);
      setOptions(memberOptions);
      setName(data.project.name);
      setDescription(data.project.description);
      setCallDescription(data.recruitmentDescription ?? '');
      setDeadline(localDateInput(data.recruitmentDeadline));
      setCheckedAt(Date.now());
      setLoading(false);
    } catch (failure) {
      if (version === revision.current) {
        setOverview(null);
        setTeam([]);
        setMine([]);
        setApplications([]);
        setOptions([]);
        setLoading(false);
      }
      throw failure;
    }
  }, [request, slug, projectId]);

  useEffect(() => {
    let active = true;
    const lifecycle = revision;
    void load().catch((failure) => {
      if (active) setError(failureMessage(failure));
    });
    return () => {
      active = false;
      lifecycle.current++;
    };
  }, [load]);

  useEffect(() => {
    const timer = window.setInterval(() => setCheckedAt(Date.now()), 15_000);
    return () => window.clearInterval(timer);
  }, []);

  const run: RunAction = async (operation, success) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await operation();
      setNotice(success);
      await load().catch((failure) => setError(`Saved, but the view could not refresh. ${failureMessage(failure)}`));
    } catch (failure) {
      setError(failureMessage(failure));
      if (failure instanceof ApiError && (failure.status === 403 || failure.status === 404)) {
        await load().catch(() => undefined);
      }
    } finally {
      setBusy(false);
    }
  };

  if (!user) return <Navigate to="/login" replace />;
  const canManage = overview?.project.canManage ?? false;
  const isOnTeam = team.some((member) => member.userId === user.id && member.active);
  const callOpen = !!overview?.recruitmentDeadline && new Date(overview.recruitmentDeadline).getTime() > checkedAt;
  const hasAcceptedApplication = mine.some((application) => application.status === 'ACCEPTED');

  return (
    <Shell slug={slug}>
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
        <p role="status">Loading project…</p>
      ) : !overview ? (
        <button
          type="button"
          className="identity-secondary"
          onClick={() => {
            void load().catch((failure) => setError(failureMessage(failure)));
          }}
        >
          Try again
        </button>
      ) : (
        <>
          <header className="project-heading">
            <p className="identity-eyebrow">
              {workspace?.name} · {canManage ? 'Project management' : 'Project recruitment'}
            </p>
            <h1>{overview.project.name}</h1>
            <p className="project-prose">{overview.project.description}</p>
            <button
              type="button"
              className="identity-secondary"
              disabled={busy}
              onClick={() => {
                setError('');
                void load().catch((failure) => setError(failureMessage(failure)));
              }}
            >
              Refresh project
            </button>
          </header>
          <div className="project-columns">
            <section className="project-panel" aria-labelledby="recruitment-title">
              <h2 id="recruitment-title">Recruitment {callOpen ? 'is open' : 'is closed'}</h2>
              <p className="project-prose">
                {overview.recruitmentDescription ?? 'The project manager has not opened recruitment yet.'}
              </p>
              {overview.recruitmentDeadline && (
                <p>
                  Deadline: {new Date(overview.recruitmentDeadline).toLocaleString()} (
                  {Intl.DateTimeFormat().resolvedOptions().timeZone})
                </p>
              )}
              <ul className="project-list">
                {overview.positions.map((position) => (
                  <li key={position.id}>
                    <strong>{position.roleLabel}</strong> · {position.departmentName}
                    {canManage && (
                      <button
                        type="button"
                        className="project-text-button"
                        disabled={busy}
                        onClick={() => {
                          setEditingPosition(position.id);
                          setRoleLabel(position.roleLabel);
                          setDepartmentName(position.departmentName);
                        }}
                      >
                        Edit position
                      </button>
                    )}
                  </li>
                ))}
              </ul>
              {!overview.positions.length && <p>No positions configured yet.</p>}
              {!isOnTeam && !hasAcceptedApplication && callOpen && overview.positions.length > 0 && (
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void run(async () => {
                      await request(`${base}/applications`, json('POST', { positionId, motivation }));
                      setMotivation('');
                    }, 'Application submitted for review.');
                  }}
                >
                  <fieldset disabled={busy}>
                    <h3>Apply for a position</h3>
                    <label>
                      Position
                      <select required value={positionId} onChange={(event) => setPosition(event.target.value)}>
                        <option value="">Choose a position</option>
                        {overview.positions.map((position) => (
                          <option key={position.id} value={position.id}>
                            {position.roleLabel} / {position.departmentName}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Motivation
                      <textarea
                        required
                        maxLength={5000}
                        value={motivation}
                        onChange={(event) => setMotivation(event.target.value)}
                      />
                    </label>
                    <p className="identity-help">
                      You may apply to several positions. Accepting one closes your other pending applications in this
                      project. Withdrawal and reapplication are not available.
                    </p>
                    <button type="submit" className="identity-primary">
                      Submit application
                    </button>
                  </fieldset>
                </form>
              )}
              {isOnTeam && <p className="identity-help">You are already on this project’s team.</p>}
              <h3>My applications</h3>
              {!mine.length && <p>You have not applied to this project.</p>}
              <ul className="project-list">
                {mine.map((application) => (
                  <li key={application.id}>
                    <strong>
                      {application.positionLabel} / {application.departmentName}
                    </strong>
                    <p>{applicationStatus[application.status]}</p>
                    <p className="project-prose">{application.motivation}</p>
                  </li>
                ))}
              </ul>
            </section>
            {canManage && (
              <section className="project-panel" aria-labelledby="configuration-title">
                <h2 id="configuration-title">Project configuration</h2>
                <fieldset disabled={busy}>
                  <form
                    onSubmit={(event) => {
                      event.preventDefault();
                      void run(() => request(base, json('PATCH', { name, description })), 'Project updated.');
                    }}
                  >
                    <label>
                      Project name
                      <input required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} />
                    </label>
                    <label>
                      Description
                      <textarea
                        required
                        maxLength={5000}
                        value={description}
                        onChange={(event) => setDescription(event.target.value)}
                      />
                    </label>
                    <button className="identity-secondary" type="submit">
                      Save project
                    </button>
                  </form>
                  <form
                    onSubmit={(event) => {
                      event.preventDefault();
                      void run(
                        () =>
                          request(
                            `${base}/recruitment`,
                            json('PUT', {
                              description: callDescription,
                              deadline: new Date(deadline).toISOString(),
                            }),
                          ),
                        'Recruitment call updated.',
                      );
                    }}
                  >
                    <h3>Recruitment call</h3>
                    <label>
                      Call description
                      <textarea
                        required
                        maxLength={5000}
                        value={callDescription}
                        onChange={(event) => setCallDescription(event.target.value)}
                      />
                    </label>
                    <label>
                      Recruitment deadline
                      <input
                        required
                        type="datetime-local"
                        value={deadline}
                        onChange={(event) => setDeadline(event.target.value)}
                      />
                    </label>
                    <p className="identity-help">
                      Times use {Intl.DateTimeFormat().resolvedOptions().timeZone}. Review may continue after the
                      deadline; changing it to a future time reopens applications.
                    </p>
                    <button className="identity-secondary" type="submit">
                      Save recruitment call
                    </button>
                  </form>
                  <form
                    onSubmit={(event) => {
                      event.preventDefault();
                      void run(async () => {
                        await request(
                          `${base}/positions${editingPosition ? `/${editingPosition}` : ''}`,
                          json(editingPosition ? 'PATCH' : 'POST', { roleLabel, departmentName }),
                        );
                        setEditingPosition(null);
                        setRoleLabel('');
                        setDepartmentName('');
                      }, 'Position saved.');
                    }}
                  >
                    <h3>{editingPosition ? 'Edit position' : 'Add a position'}</h3>
                    <label>
                      Role label
                      <input
                        required
                        maxLength={100}
                        value={roleLabel}
                        onChange={(event) => setRoleLabel(event.target.value)}
                      />
                    </label>
                    <label>
                      Department name
                      <input
                        required
                        maxLength={100}
                        value={departmentName}
                        onChange={(event) => setDepartmentName(event.target.value)}
                      />
                    </label>
                    <p className="identity-help">
                      Use your association’s own labels. A position with applications cannot move departments.
                    </p>
                    <button className="identity-secondary" type="submit">
                      Save position
                    </button>
                    {editingPosition && (
                      <button
                        className="project-text-button"
                        type="button"
                        onClick={() => {
                          setEditingPosition(null);
                          setRoleLabel('');
                          setDepartmentName('');
                        }}
                      >
                        Cancel editing
                      </button>
                    )}
                  </form>
                </fieldset>
              </section>
            )}
            {overview.associationAdmin && (
              <section className="project-panel" aria-labelledby="appointment-title">
                <h2 id="appointment-title">Appoint a project manager</h2>
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void run(
                      () => request(`${base}/managers/${managerUserId}`, json('PUT')),
                      'Project manager appointed.',
                    );
                  }}
                >
                  <fieldset disabled={busy}>
                    <label>
                      Association member
                      <select required value={managerUserId} onChange={(event) => setManagerUserId(event.target.value)}>
                        <option value="">Choose a member</option>
                        {options.map((member) => (
                          <option key={member.userId} value={member.userId}>
                            {member.displayName}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button type="submit" className="identity-secondary">
                      Appoint project manager
                    </button>
                  </fieldset>
                </form>
              </section>
            )}
          </div>
          {canManage && (
            <section className="project-panel project-wide" aria-labelledby="applications-title">
              <h2 id="applications-title">Review applications</h2>
              {!applications.length && <p>No applications yet.</p>}
              <ul className="project-list">
                {applications.map((application) => (
                  <ApplicationReview
                    key={application.id}
                    {...{
                      application,
                      positions: overview.positions,
                      team,
                      base,
                      busy,
                      run,
                      request,
                    }}
                  />
                ))}
              </ul>
            </section>
          )}
          {overview.project.canOpen && (
            <section className="project-panel project-wide" aria-labelledby="team-title">
              <h2 id="team-title">Project team & reporting</h2>
              <p className="identity-help">
                Reporting controls task access. Sharing a department or conversation does not grant access to
                colleagues’ tasks. Removed association members retain history but have no active project access.
              </p>
              <ul className="project-list">
                {team.map((member) => (
                  <TeamRow
                    key={`${member.id}:${member.reportingParentMembershipId}:${member.projectRole}`}
                    {...{
                      member,
                      team,
                      canManage,
                      base,
                      busy,
                      run,
                      request,
                    }}
                  />
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </Shell>
  );
}

export default function ProjectPages(): ReactElement {
  const { slug = '', projectId } = useParams<{ slug: string; projectId: string }>();
  const { user } = useAuth();
  const key = `${user?.id}:${slug}:${projectId ?? 'list'}`;
  if (!user) return <Navigate to="/login" replace />;
  return projectId ? (
    <ProjectDetail key={key} slug={slug} projectId={projectId} />
  ) : (
    <ProjectList key={key} slug={slug} />
  );
}
