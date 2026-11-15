export type AssociationRole = 'MEMBER' | 'ADMIN';
export interface Workspace {
  associationId: string;
  name: string;
  slug: string;
  role: AssociationRole;
}
export interface AssociationMember {
  id: string;
  userId: string;
  displayName: string;
  email: string;
  role: AssociationRole;
  createdAt: string;
}
