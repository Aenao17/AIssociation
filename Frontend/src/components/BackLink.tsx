import type { ReactElement, ReactNode } from 'react';
import { Link } from 'react-router-dom';

import './BackLink.css';

export default function BackLink({
  to,
  children,
  direction = 'left',
}: {
  to: string;
  children: ReactNode;
  direction?: 'left' | 'right';
}): ReactElement {
  return (
    <Link className={`back-link${direction === 'right' ? ' back-link-forward' : ''}`} to={to}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
        <path
          d={direction === 'right' ? 'm14 5 7 7-7 7M21 12H3' : 'm10 5-7 7 7 7M3 12h18'}
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <span>{children}</span>
    </Link>
  );
}
