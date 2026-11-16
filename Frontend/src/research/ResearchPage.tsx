import { IonContent, IonPage } from '@ionic/react';
import { useRef, useState } from 'react';
import type { FormEvent, ReactElement } from 'react';
import { Link } from 'react-router-dom';

import { ApiError, apiRequest } from '../auth/client';
import BrandHeader from '../components/BrandHeader';

import { emptyAnswers, questions } from './questions';
import '../pages/Identity.css';
import './Research.css';

export default function ResearchPage(): ReactElement {
  const [answers, setAnswers] = useState(emptyAnswers);
  const [submissionId] = useState(() => crypto.randomUUID());
  const [saving, setSaving] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');
  const sending = useRef(false);

  function select(key: string, option: string) {
    const value = answers[key];
    if (!Array.isArray(value)) return;
    const next = value.includes(option) ? value.filter((item) => item !== option) : [...value, option];
    setAnswers({ ...answers, [key]: next });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current || submitted) return;
    for (const question of questions) {
      const value = answers[question.key];
      if (question.options && Array.isArray(value) && value.length === 0) {
        setError(`Choose at least one option: ${question.label}`);
        document.getElementById(question.key)?.focus();
        return;
      }
    }
    sending.current = true;
    setSaving(true);
    setError('');
    try {
      await apiRequest('/api/public/research/responses', {
        method: 'POST',
        body: JSON.stringify({ submissionId, answers }),
      });
      setSubmitted(true);
    } catch (failure) {
      setError(
        failure instanceof ApiError
          ? `${failure.message} ${Object.values(failure.fieldErrors).join(' ')}`.trim()
          : 'Unable to submit. Your answers are still here. Please try again.',
      );
    } finally {
      sending.current = false;
      setSaving(false);
    }
  }

  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell">
          <BrandHeader wide />
          <section className="identity-card research-card">
            <p className="identity-eyebrow">Help shape AIssociation</p>
            <h1>How does your association work?</h1>
            {submitted ? (
              <div role="status">
                <h2>Thank you for sharing.</h2>
                <p>Your response has been saved and will help us understand different associations’ needs.</p>
                <Link to="/home">Back to home</Link>
              </div>
            ) : (
              <>
                <p className="identity-intro">
                  About 5 minutes. Tell us about your current experience. There is no single right way to organize an
                  association.
                </p>
                <p className="identity-help">
                  Team Stucanii uses these responses to guide AIssociation’s requirements. Only AIssociation platform
                  administrators can read them. No account is needed. Written answers, association name and follow-up
                  email are optional. Please avoid including other people’s personal information. Provide an email only
                  if you are happy for us to contact you about this research.
                </p>
                <form
                  onSubmit={(event) => {
                    void submit(event);
                  }}
                >
                  <fieldset className="research-fields" disabled={saving}>
                    {questions.map((question) => {
                      const value = answers[question.key];
                      if (question.options) {
                        return (
                          <fieldset className="research-question" key={question.key} id={question.key} tabIndex={-1}>
                            <legend>{question.label}</legend>
                            <p className="identity-help">
                              {question.limit === 1
                                ? 'Choose one.'
                                : question.limit
                                  ? `Choose up to ${question.limit}.`
                                  : 'Choose all that apply.'}
                            </p>
                            <div className="research-options">
                              {question.options.map((option) => {
                                const checked = Array.isArray(value) ? value.includes(option) : value === option;
                                const atLimit =
                                  Array.isArray(value) &&
                                  question.limit !== undefined &&
                                  value.length >= question.limit;
                                return (
                                  <label key={option}>
                                    <input
                                      type={question.limit === 1 ? 'radio' : 'checkbox'}
                                      name={question.key}
                                      value={option}
                                      checked={checked}
                                      required={question.limit === 1}
                                      disabled={!checked && atLimit}
                                      onChange={() => {
                                        if (question.limit === 1) setAnswers({ ...answers, [question.key]: option });
                                        else select(question.key, option);
                                      }}
                                    />
                                    {option}
                                  </label>
                                );
                              })}
                            </div>
                          </fieldset>
                        );
                      }
                      return (
                        <label className="research-question" key={question.key}>
                          {question.label} <span className="research-optional">(optional)</span>
                          {question.key === 'associationName' || question.key === 'contactEmail' ? (
                            <input
                              type={question.key === 'contactEmail' ? 'email' : 'text'}
                              name={question.key}
                              maxLength={question.maxLength}
                              value={typeof value === 'string' ? value : ''}
                              onChange={(event) => setAnswers({ ...answers, [question.key]: event.target.value })}
                            />
                          ) : (
                            <textarea
                              name={question.key}
                              rows={3}
                              maxLength={question.maxLength ?? 2000}
                              value={typeof value === 'string' ? value : ''}
                              onChange={(event) => setAnswers({ ...answers, [question.key]: event.target.value })}
                            />
                          )}
                        </label>
                      );
                    })}
                    {error && (
                      <p className="identity-error" role="alert">
                        {error}
                      </p>
                    )}
                    <button className="identity-primary" type="submit" disabled={saving}>
                      {saving ? 'Sending…' : 'Send response'}
                    </button>
                  </fieldset>
                </form>
              </>
            )}
          </section>
        </main>
      </IonContent>
    </IonPage>
  );
}
