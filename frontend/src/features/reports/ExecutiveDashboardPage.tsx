import React, { useEffect, useState } from 'react';
import { ApiResponse, request } from '../../api/httpClient';
import { ReportSection } from './ReportSectionPage';

interface Dashboard { reportSectionId:string; openFindings:number; closedFindings:number; riskRating:Record<string,number>; remediation:Record<string,number>; }
interface Props { sections:ReportSection[]; onSelect:(id:string)=>void; }
export const ExecutiveDashboardPage:React.FC<Props> = ({sections,onSelect}) => {
  const general=sections.filter(s=>s.id!=='APPSEC_FINDINGS_PT');
  const [data,setData]=useState<Record<string,Dashboard>>({}); const [error,setError]=useState<string|null>(null); const [loading,setLoading]=useState(true);
  useEffect(()=>{let cancelled=false;setLoading(true);Promise.all(general.map(async section=>{
    try {const result=await request<ApiResponse<Dashboard>>(`/api/reports/sections/${section.id}/dashboard`);return [section.id,result.data] as const;}
    catch(e){throw new Error(`${section.name}: ${e instanceof Error?e.message:'dashboard unavailable'}`);}
  })).then(values=>{if(!cancelled){setData(Object.fromEntries(values.filter((v):v is readonly [string,Dashboard]=>Boolean(v[1])).map(v=>[v[0],v[1]])));setError(null);}}).catch(e=>{if(!cancelled)setError(e instanceof Error?e.message:'Dashboard data unavailable.');}).finally(()=>{if(!cancelled)setLoading(false);});return()=>{cancelled=true;};},[general.map(s=>s.id).join('|')]);
  const total=(field:'openFindings'|'closedFindings')=>Object.values(data).reduce((sum,d)=>sum+d[field],0);
  return <section className="report-page"><div className="report-page-heading"><div><div className="eyebrow">OVERVIEW</div><h2>Executive Dashboard</h2><p>Current authorized snapshot across the five UID-based VA/PT sections. AppSec remains independent.</p></div></div>
    {error&&<div className="inline-error" role="alert">{error}</div>}{loading&&<p className="muted">Loading scoped section summaries…</p>}
    <div className="metric-grid"><div><span>Open findings</span><strong>{total('openFindings').toLocaleString()}</strong></div><div><span>Closed findings</span><strong>{total('closedFindings').toLocaleString()}</strong></div><div><span>Included sections</span><strong>{general.length}</strong></div></div>
    <div className="report-section-grid">{general.map(section=>{const d=data[section.id];return <button key={section.id} className="report-section-tile executive-tile" onClick={()=>onSelect(section.id)}><h3>{section.name}</h3><p>{d?`${d.openFindings.toLocaleString()} open · ${d.closedFindings.toLocaleString()} closed`:'No available snapshot'}</p>{d&&<div className="executive-breakdown">{Object.entries(d.riskRating).sort((a,b)=>b[1]-a[1]).slice(0,4).map(([k,v])=><span key={k}>{k}<b>{v}</b></span>)}</div>}<span className="text-button">Open report →</span></button>;})}</div>
  </section>;
};
