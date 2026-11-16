export interface Question {
  key: string;
  label: string;
  options?: string[];
  limit?: number;
  maxLength?: number;
}

export const questions: Question[] = [
  {
    key: 'involvement',
    label: 'What is your involvement in a student association?',
    options: ['Member', 'Project organizer', 'Association leader', 'Participant', 'Other'],
    limit: 1,
  },
  {
    key: 'memberCount',
    label: 'Approximately how many active members does your association have?',
    options: ['1–20', '21–50', '51–100', '101–200', '201+', 'Not sure', 'Not applicable'],
    limit: 1,
  },
  { key: 'organization', label: 'How do you organize projects and divide responsibilities?' },
  {
    key: 'recruitment',
    label: 'How do members join project teams?',
    options: ['Open call', 'Application form', 'Direct invitation', 'Appointment', 'Informal discussion', 'Other'],
  },
  { key: 'recruitmentDetails', label: 'Anything to add about recruitment?', maxLength: 1000 },
  {
    key: 'tasks',
    label: 'How do you assign tasks and follow their progress?',
    options: ['Meetings', 'Spreadsheets', 'Task management app', 'Chat messages', 'No consistent process', 'Other'],
  },
  { key: 'taskDetails', label: 'Anything to add about tasks?', maxLength: 1000 },
  {
    key: 'tools',
    label: 'Which tools do you currently use?',
    options: [
      'Google Workspace',
      'Microsoft 365',
      'WhatsApp',
      'Discord',
      'Notion',
      'Trello or similar',
      'None',
      'Other',
    ],
  },
  { key: 'toolDetails', label: 'Anything to add about your tools?', maxLength: 1000 },
  {
    key: 'challenges',
    label: 'What are your biggest organizational difficulties?',
    options: [
      'Finding information',
      'Unclear responsibilities',
      'Following task progress',
      'Recruiting teams',
      'Communication',
      'Registration and attendance',
      'Other',
    ],
    limit: 3,
  },
  { key: 'challengeDetails', label: 'Tell us more about these difficulties.', maxLength: 1000 },
  { key: 'registration', label: 'How do you manage event registration and attendance?' },
  {
    key: 'capabilities',
    label: 'Which capabilities would be most useful to your association?',
    options: ['Tasks', 'Recruitment', 'Event publishing', 'Registrations', 'Attendance', 'Communication', 'Other'],
    limit: 3,
  },
  { key: 'capabilityDetails', label: 'Anything to add about useful capabilities?', maxLength: 1000 },
  { key: 'barriers', label: 'What would prevent your association from adopting a new platform?' },
  { key: 'suggestions', label: 'What important need have we missed?' },
  { key: 'associationName', label: 'Association name', maxLength: 150 },
  { key: 'contactEmail', label: 'Email for an optional follow-up', maxLength: 254 },
];

export type Answers = Record<string, string | string[]>;

export function emptyAnswers(): Answers {
  const answers: Answers = {};
  for (const question of questions) {
    answers[question.key] = question.options && question.limit !== 1 ? [] : '';
  }
  return answers;
}

export interface ResearchResponse {
  id: string;
  submittedAt: string;
  answers: Answers;
}
