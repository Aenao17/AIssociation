import { IonContent, IonPage } from '@ionic/react';
import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Link, Navigate } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import BrandHeader from '../components/BrandHeader';

import { questions } from './questions';
import type { ResearchResponse } from './questions';
import '../pages/Identity.css';
import './Research.css';

export default function ResearchResponsesPage(): ReactElement {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell">
          <BrandHeader wide />
          <section className="identity-card research-card">
            <p className="identity-eyebrow">Platform administration</p>
            <h1>Research responses</h1>
            <Link to="/account">Back to my account</Link>
            {user.platformAdmin ? (
              <ResponseList key={user.id} />
            ) : (
              <p>This area is available to AIssociation platform administrators.</p>
            )}
          </section>
        </main>
      </IonContent>
    </IonPage>
  );
}

function ResponseList() {
  const { request } = useAuth();
  const [responses, setResponses] = useState<ResearchResponse[]>([]);
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    request<ResearchResponse[]>(`/api/admin/research/responses?page=${page}`)
      .then((result) => {
        if (active) setResponses(result);
      })
      .catch(() => {
        if (active) setError('Unable to load responses. Please try again.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [request, page, attempt]);

  function load(nextPage: number) {
    setLoading(true);
    setError('');
    setResponses([]);
    setPage(nextPage);
    setAttempt(attempt + 1);
  }

  return (
    <>
      {loading && <p role="status">Loading responses…</p>}
      {error && (
        <p className="identity-error" role="alert">
          {error}
        </p>
      )}
      {!loading && !error && responses.length === 0 && <p>No responses on this page yet.</p>}
      {!loading &&
        !error &&
        responses.map((response) => (
          <details className="research-response" key={response.id}>
            <summary>
              {response.answers.associationName || 'Association not provided'} ·{' '}
              {new Date(response.submittedAt).toLocaleString()}
            </summary>
            <dl>
              {questions.map((question) => {
                const answer = response.answers[question.key];
                return (
                  <div key={question.key}>
                    <dt>{question.label}</dt>
                    <dd>{Array.isArray(answer) ? answer.join(', ') : answer || 'No answer'}</dd>
                  </div>
                );
              })}
            </dl>
          </details>
        ))}
      <div className="identity-actions">
        <button className="identity-secondary" disabled={loading || page === 0} onClick={() => load(page - 1)}>
          Previous
        </button>
        <button
          className="identity-secondary"
          disabled={loading || !!error || responses.length < 50}
          onClick={() => load(page + 1)}
        >
          Next
        </button>
        <button className="identity-secondary" disabled={loading} onClick={() => load(page)}>
          Refresh
        </button>
      </div>
      <p className="identity-help">Page {page + 1} · Up to 50 responses per page, newest first.</p>
    </>
  );
}
