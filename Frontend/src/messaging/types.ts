import { ApiError } from '../auth/client';

export type ConversationType = 'ASSOCIATION' | 'PROJECT' | 'DEPARTMENT' | 'GROUP' | 'DIRECT';

export interface Message {
  id: string;
  seq: number; 
  conversationId: string;
  senderUserId: string;
  senderName: string;
  body: string;
  createdAt: string;
  mine: boolean;
}

export interface Conversation {
  id: string;
  associationId: string;
  type: ConversationType;
  title: string; 
  projectId: string | null;
  projectName: string | null;
  departmentId: string | null;
  departmentName: string | null;
  createdByUserId: string | null;
  canManageMembers: boolean;
  lastMessage: Message | null;
}

export interface Participant {
  userId: string;
  displayName: string;
}

export interface MessagePage {
  messages: Message[];
  hasMore: boolean;
}

export const MAX_MESSAGE_LENGTH = 4000;
export const MAX_GROUP_NAME_LENGTH = 100;

const sections: { type: ConversationType; label: string }[] = [
  { type: 'ASSOCIATION', label: 'Association' },
  { type: 'PROJECT', label: 'Projects' },
  { type: 'DEPARTMENT', label: 'Departments' },
  { type: 'GROUP', label: 'Groups' },
  { type: 'DIRECT', label: 'Direct messages' },
];

export function groupConversations(
  conversations: Conversation[],
): { type: ConversationType; label: string; items: Conversation[] }[] {
  return sections
    .map((section) => ({ ...section, items: conversations.filter((item) => item.type === section.type) }))
    .filter((section) => section.items.length > 0);
}

/**
 * Merges new messages into the ones on screen: no duplicates (matched by id), always oldest first (by seq).
 * Needed because a message we just sent can also come back from the next poll.
 */
export function mergeMessages(current: Message[], incoming: Message[]): Message[] {
  const byId = new Map(current.map((message) => [message.id, message]));
  for (const message of incoming) byId.set(message.id, message);
  return [...byId.values()].sort((a, b) => a.seq - b.seq);
}

export function previewOf(conversation: Conversation): string {
  const last = conversation.lastMessage;
  if (!last) return 'No messages yet';
  return `${last.mine ? 'You' : last.senderName}: ${last.body}`;
}

const messages: Record<string, string> = {
  CONVERSATION_NOT_FOUND: 'This conversation is no longer available to you.',
  MEMBERSHIP_REQUIRED: 'You are not a member of this association.',
  INVALID_PARTICIPANT: 'Only current members of this association can be added.',
  GROUP_MANAGER_REQUIRED: 'Only the person who created this group can change its members.',
  CREATOR_CANNOT_LEAVE: 'The creator of a group cannot leave it.',
  NOT_A_GROUP: 'Members can only be changed in custom groups.',
  VALIDATION_ERROR: 'Please check the message or name: it must not be empty or too long.',
};

export function messagingErrorText(failure: unknown): string {
  if (failure instanceof ApiError) {
    if (failure.status === 401) return 'Your session has expired. Sign in again.';
    return messages[failure.code] ?? failure.message;
  }
  return 'Unable to reach the server. Please try again.';
}