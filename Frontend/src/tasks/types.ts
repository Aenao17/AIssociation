export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export interface Task {
  id: string;
  associationId: string;
  projectId: string;
  projectName: string;
  title: string;
  description: string | null;
  dueAt: string;
  status: TaskStatus;
  assigneeUserId: string;
  assigneeName: string;
  createdByUserId: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  canManage: boolean;
  canChangeStatus: boolean;
}

export interface AssigneeOption {
  userId: string;
  displayName: string;
  projectRole: 'PROJECT_MANAGER' | 'DEPARTMENT_MANAGER' | 'MEMBER';
  departmentName: string | null;
}

export const statusLabels: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
};
