export interface ProjectSummary {
  id: string;
  associationId: string;
  name: string;
  description: string;
  recruitmentDeadline: string | null;
  canManage: boolean;
  canOpen: boolean;
}

export interface Position {
  id: string;
  departmentId: string;
  departmentName: string;
  roleLabel: string;
}

export interface ProjectOverview {
  project: ProjectSummary;
  recruitmentDescription: string | null;
  recruitmentDeadline: string | null;
  positions: Position[];
  associationAdmin: boolean;
}

export interface TeamMember {
  id: string;
  userId: string;
  displayName: string;
  projectRole: 'PROJECT_MANAGER' | 'DEPARTMENT_MANAGER' | 'MEMBER';
  departmentId: string | null;
  departmentName: string | null;
  positionId: string | null;
  positionLabel: string | null;
  reportingParentMembershipId: string | null;
  active: boolean;
}

export interface ProjectApplication {
  id: string;
  positionId: string;
  positionLabel: string;
  departmentName: string;
  userId: string;
  displayName: string;
  motivation: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CLOSED_ACCEPTED_ELSEWHERE';
  createdAt: string;
  reviewedAt: string | null;
}

export interface MemberOption {
  userId: string;
  displayName: string;
}

export const applicationStatus: Record<ProjectApplication['status'], string> = {
  PENDING: 'Pending review',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  CLOSED_ACCEPTED_ELSEWHERE: 'Closed — accepted for another position',
};
