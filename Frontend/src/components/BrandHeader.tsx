import { Link } from 'react-router-dom';

import './BrandHeader.css';

/** Preserve the supplied logo composition; use its standalone symbol on mobile. */
const BrandHeader: React.FC<{ wide?: boolean }> = ({ wide = false }) => (
  <header className={`brand-header${wide ? ' brand-header-wide' : ''}`}>
    <Link to="/home" aria-label="AIssociation home" className="brand-home-link">
      <picture>
        <source media="(max-width: 480px)" srcSet="/brand/symbol-ink.svg" />
        <img src="/brand/logo-ink.svg" alt="AIssociation" width="280" height="31" />
      </picture>
    </Link>
  </header>
);

export default BrandHeader;
