CREATE TABLE research_responses (
    id UUID PRIMARY KEY,
    answers TEXT NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX research_responses_submitted_idx ON research_responses(submitted_at, id);
