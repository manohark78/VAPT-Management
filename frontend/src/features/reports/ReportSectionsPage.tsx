import React from 'react';
import { ReportSection } from './ReportSectionPage';

interface Props {
  sections: ReportSection[];
  onSelect: (sectionId: string) => void;
}

export const ReportSectionsPage: React.FC<Props> = ({ sections, onSelect }) => (
  <section className="report-page">
    <div className="report-page-heading">
      <div>
        <div className="eyebrow">REPORT SOURCES</div>
        <h2>Report sections</h2>
        <p>Each source keeps its own upload history, records, and active import.</p>
      </div>
      <span className="format-pill">CSV · XLS · XLSX</span>
    </div>
    <div className="report-section-grid">
      {sections.map((section) => (
        <article className="report-section-tile" key={section.id}>
          <div className="eyebrow">{section.id === 'APPSEC_FINDINGS_PT' ? 'FINDING ID SOURCE' : 'UID SOURCE'}</div>
          <h3>{section.name}</h3>
          <p>Independent import history and active dataset for this report section.</p>
          <button className="primary-button" type="button" onClick={() => onSelect(section.id)}>
            Open section
          </button>
        </article>
      ))}
    </div>
  </section>
);
