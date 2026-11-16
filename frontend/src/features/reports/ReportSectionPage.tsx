import React, { DragEvent, useCallback, useEffect, useRef, useState } from 'react';
import { ApiResponse, request, upload } from '../../api/httpClient';

export interface ReportSection { id: string; name: string; }
interface Page<T> { content: T[]; pageNumber: number; totalPages: number; totalElements: number; last: boolean; }
interface ImportBatch { id: string; originalFilename: string; status: string; active: boolean; totalRows: number; acceptedRows: number; rejectedRows: number; errorMessage: string | null; createdAt: string; completeSnapshot?: boolean; }
interface Dashboard { reportSectionId: string; asOf: string; openFindings: number; closedFindings: number; riskRating: Record<string, number>; complianceStatus: Record<string, number>; applications: Record<string, number>; appTags: Record<string, number>; environments: Record<string, number>; riskScores: Record<string, number>; severities: Record<string, number>; remediation: Record<string, number>; }
interface Finding { findingId: string | null; sourceUid: string | null; classification: string; lifecycleState: string; observedAt: string; type: string; port: string; riskScore: string; riskRating: string; complianceStatus: string; severity: string; results: string; targets: string; displayName: string; appTag: string; ipAddress: string; applicationName: string; environment: string; ownedBy: string; remediationAction: string | null; remediationDate: string | null; }
interface Group { ipAddress: string; applicationName: string; appTag: string; critical: number; high: number; medium: number; low: number; findingCount: number; }
interface Comment { id: string; text: string; actorUserId: string; createdAt: string; }
interface HistoryItem { kind: string; action: string; actorUserId: string | null; detail: string | null; referenceId: string | null; at: string; status: string | null; }
interface Props { section: ReportSection; role: string; email?: string; }
type Tab = 'Dashboard' | 'Comments' | 'Data';

function message(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'error' in error) return (error as { error?: { message?: string } }).error?.message || 'Request failed.';
  return error instanceof Error ? error.message : 'The request failed. Try again or contact an administrator.';
}
const qs = (values: Record<string, string | number | undefined>) => new URLSearchParams(Object.entries(values).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => [k, String(v)])).toString();

function BarChart({ title, values }: { title: string; values: Record<string, number> }) {
  const entries = Object.entries(values || {}).sort((a, b) => b[1] - a[1]).slice(0, 10);
  const max = Math.max(1, ...entries.map(([, value]) => value));
  return <article className="report-card chart-card"><h3>{title}</h3>{entries.length ? entries.map(([label, value]) => <div className="chart-bar-row" key={label}><span title={label}>{label || 'Unspecified'}</span><div><i style={{ width: `${Math.max(2, value / max * 100)}%` }} /></div><b>{value.toLocaleString()}</b></div>) : <p className="muted">No current data.</p>}</article>;
}

function DonutChart({ title, values }: { title: string; values: Record<string, number> }) {
  const palette = ['#005b8e', '#f0a500', '#39a78e', '#7b61a8', '#df6b4d', '#62a8d1', '#a6b84a', '#c35f8d'];
  const entries = Object.entries(values || {}).sort((a, b) => b[1] - a[1]).filter(([, value]) => value > 0);
  const total = entries.reduce((sum, [, value]) => sum + value, 0);
  let offset = 0;
  const segments = entries.map(([, value], index) => {
    const start = offset;
    offset += total ? value / total * 100 : 0;
    return `${palette[index % palette.length]} ${start}% ${offset}%`;
  });
  return <article className="report-card donut-card"><h3>{title}</h3>{total ? <div className="donut-layout"><div className="donut-visual" style={{ background: `conic-gradient(${segments.join(',')})` }} role="img" aria-label={`${title}: ${total.toLocaleString()} total`}><div><strong>{total.toLocaleString()}</strong><span>Total</span></div></div><ul className="donut-legend">{entries.map(([label, value], index) => <li key={label}><i style={{ backgroundColor: palette[index % palette.length] }}/><span>{label || 'Unspecified'}</span><b>{value.toLocaleString()}</b></li>)}</ul></div> : <p className="muted">No current data.</p>}</article>;
}

export const ReportSectionPage: React.FC<Props> = ({ section, role }) => {
  const [tab, setTab] = useState<Tab>('Dashboard');
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [rows, setRows] = useState<Page<Finding> | null>(null);
  const [groups, setGroups] = useState<Page<Group> | null>(null);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState({ ip: '', application: '', appTag: '', riskRating: '', compliance: '', environment: '', query: '' });
  const [history, setHistory] = useState<Page<ImportBatch> | null>(null);
  const [active, setActive] = useState<ImportBatch | null>(null);
  const [modal, setModal] = useState<'upload' | 'history' | null>(null);
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [comments, setComments] = useState<Page<Comment> | null>(null);
  const [commentText, setCommentText] = useState('');
  const [selectedFinding, setSelectedFinding] = useState<Finding | null>(null);
  const [findingHistory, setFindingHistory] = useState<HistoryItem[]>([]);
  const [rca, setRca] = useState('');
  const [completeSnapshot, setCompleteSnapshot] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [dragging, setDragging] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const admin = role === 'ADMIN';
  const supportsWorkspace = section.id !== 'APPSEC_FINDINGS_PT';

  const loadDashboard = useCallback(async () => {
    if (!supportsWorkspace) return;
    const response = await request<ApiResponse<Dashboard>>(`/api/reports/sections/${section.id}/dashboard?${qs(filters)}`);
    setDashboard(response.data || null);
  }, [filters, section.id, supportsWorkspace]);
  const loadRows = useCallback(async () => {
    if (!supportsWorkspace) return;
    const response = await request<ApiResponse<Page<Finding>>>(`/api/reports/sections/${section.id}/data?${qs({ ...filters, page, size: 25, sort: 'observedAt', direction: 'desc' })}`);
    setRows(response.data || null);
  }, [filters, page, section.id, supportsWorkspace]);
  const loadGroups = useCallback(async () => {
    if (!supportsWorkspace) return;
    const response = await request<ApiResponse<Page<Group>>>(`/api/reports/sections/${section.id}/comments/groups?page=${page}&size=25`);
    setGroups(response.data || null);
  }, [page, section.id, supportsWorkspace]);
  const loadImports = useCallback(async () => {
    if (!admin) return;
    const [h, a] = await Promise.all([
      request<ApiResponse<Page<ImportBatch>>>(`/api/reports/sections/${section.id}/imports?page=${page}&size=10`),
      request<ApiResponse<ImportBatch | null>>(`/api/reports/sections/${section.id}/imports/active`),
    ]);
    setHistory(h.data || null); setActive(a.data || null);
  }, [admin, page, section.id]);

  useEffect(() => {
    setLoading(true); setError(null);
    const work = tab === 'Dashboard' ? loadDashboard() : tab === 'Data' ? loadRows() : loadGroups();
    void work.catch((e: unknown) => setError(message(e))).finally(() => setLoading(false));
  }, [tab, loadDashboard, loadRows, loadGroups]);
  useEffect(() => { if (modal === 'history') void loadImports().catch((e: unknown) => setError(message(e))); }, [modal, loadImports]);

  const submitFile = async (file?: File) => {
    if (!file) return;
    setError(null); setNotice(null);
    const extension = file.name.toLowerCase().split('.').pop();
    if (!['csv', 'xls', 'xlsx'].includes(extension || '')) { setError('Choose a CSV, XLS, or XLSX file.'); return; }
    setUploading(true); setProgress(0);
    try {
      const response = await upload<ApiResponse<ImportBatch>>(`/api/reports/sections/${section.id}/imports`, file,
        setProgress, { completeSnapshot: String(completeSnapshot) });
      const batch = response.data;
      if (batch?.status === 'FAILED') setError(batch.errorMessage || 'The import failed.');
      else setNotice(`${batch?.acceptedRows?.toLocaleString() ?? 0} records imported. ${batch?.completeSnapshot ? 'Declared complete snapshot.' : 'Not declared complete.'}`);
      setPage(0); await loadImports();
    } catch (e: unknown) { setError(message(e)); }
    finally { setUploading(false); setModal(null); }
  };
  const dropFile = (event: DragEvent<HTMLDivElement>) => { event.preventDefault(); setDragging(false); void submitFile(event.dataTransfer.files[0]); };
  const openComments = async (group: Group) => {
    setSelectedGroup(group); setError(null);
    try { const response = await request<ApiResponse<Page<Comment>>>(`/api/reports/sections/${section.id}/comments?${qs({ ip: group.ipAddress, application: group.applicationName, appTag: group.appTag, page: 0, size: 50 })}`); setComments(response.data || null); }
    catch (e: unknown) { setError(message(e)); }
  };
  const postComment = async () => {
    if (!selectedGroup || !commentText.trim()) return;
    try { await request(`/api/reports/sections/${section.id}/comments`, { method: 'POST', body: JSON.stringify({ ipAddress: selectedGroup.ipAddress, applicationName: selectedGroup.applicationName, appTag: selectedGroup.appTag, text: commentText }) }); setCommentText(''); await openComments(selectedGroup); }
    catch (e: unknown) { setError(message(e)); }
  };
  const chooseAction = async (finding: Finding, action: string) => {
    if (!finding.findingId) return;
    try {
      let selectedDate: string | undefined;
      if (action === 'REMEDIATION_DATE') { selectedDate = window.prompt('Select remediation date (YYYY-MM-DD)') || undefined; if (!selectedDate) return; }
      await request(`/api/findings/${finding.findingId}/remediation`, { method: 'POST', body: JSON.stringify({ action, ...(selectedDate ? { selectedDate } : {}) }) });
      setNotice('Remediation action recorded.'); await loadRows();
    } catch (e: unknown) { setError(message(e)); }
  };
  const showFindingHistory = async (finding: Finding) => {
    if (!finding.findingId) return;
    setSelectedFinding(finding); setRca('');
    try { const result = await request<ApiResponse<HistoryItem[]>>(`/api/findings/${finding.findingId}/history`); setFindingHistory(result.data || []); }
    catch (e: unknown) { setError(message(e)); }
  };
  const reviewRca = async (rcaId: string, approve: boolean) => {
    if (!admin || !selectedFinding?.findingId) return;
    try {
      await request(`/api/admin/findings/${selectedFinding.findingId}/rca/${rcaId}/review`, {
        method: 'POST', body: JSON.stringify({ approve, note: null }),
      });
      setNotice(`RCA ${approve ? 'approved' : 'rejected'}.`);
      const result = await request<ApiResponse<HistoryItem[]>>(`/api/findings/${selectedFinding.findingId}/history`);
      setFindingHistory(result.data || []);
    } catch (e: unknown) { setError(message(e)); }
  };
  const exportFile = (kind: 'xlsx' | 'pptx') => {
    const params = qs({ application: filters.application, appTag: filters.appTag, riskRating: filters.riskRating, compliance: filters.compliance, environment: filters.environment, ipAddress: filters.ip, query: filters.query });
    window.location.assign(`/api/reports/sections/${section.id}/exports/${kind}?${params}`);
  };

  return <section className="report-page">
    <div className="report-page-heading"><div><div className="eyebrow">REPORT SECTION</div><h2>{section.name}</h2><p>Scoped findings, lifecycle, remediation and reporting for this independent source.</p></div>
      <div className="report-actions">{supportsWorkspace && <><button className="secondary-button" onClick={() => exportFile('xlsx')}>Export Excel</button><button className="secondary-button" onClick={() => exportFile('pptx')}>Export PowerPoint</button></>}{admin && <><button className="secondary-button" onClick={() => setModal('history')}>History</button><button className="primary-button" onClick={() => setModal('upload')}>Upload</button></>}</div>
    </div>
    {supportsWorkspace ? <nav className="report-tabs" aria-label="Report views">{(['Dashboard', 'Comments', 'Data'] as Tab[]).map((name) => <button key={name} className={tab === name ? 'active' : ''} onClick={() => { setTab(name); setPage(0); }}>{name}</button>)}</nav> : <div className="report-notice">AppSec Findings PT retains its independent Finding ID import workflow. Comments, UID reconciliation and this finding workspace are disabled until the AppSec identity mapping is confirmed.</div>}
    {notice && <div className="inline-success" role="status">{notice}</div>}{error && <div className="inline-error" role="alert">{error}</div>}
    {supportsWorkspace && <div className="report-filters"><label>Application<input value={filters.application} onChange={e => { setPage(0); setFilters({ ...filters, application: e.target.value }); }} placeholder="All applications" /></label><label>AppTag<input value={filters.appTag} onChange={e => { setPage(0); setFilters({ ...filters, appTag: e.target.value }); }} placeholder="All AppTags" /></label><label>Risk rating<select value={filters.riskRating} onChange={e => { setPage(0); setFilters({ ...filters, riskRating: e.target.value }); }}><option value="">All ratings</option>{['Breach Potential','Emerging Threat','Critical','High','Medium','Low'].map(x => <option key={x}>{x}</option>)}</select></label><label>Compliance<input value={filters.compliance} onChange={e => { setPage(0); setFilters({ ...filters, compliance: e.target.value }); }} placeholder="All statuses" /></label><label>Environment<input value={filters.environment} onChange={e => { setPage(0); setFilters({ ...filters, environment: e.target.value }); }} placeholder="All environments" /></label><label className="filter-wide">Search<input value={filters.query} onChange={e => { setPage(0); setFilters({ ...filters, query: e.target.value }); }} placeholder="Finding text or UID" /></label><button className="text-button" onClick={() => { setFilters({ ip: '', application: '', appTag: '', riskRating: '', compliance: '', environment: '', query: '' }); setPage(0); }}>Clear</button></div>}
    {loading && <p className="muted">Loading {tab.toLowerCase()}…</p>}
    {tab === 'Dashboard' && supportsWorkspace && dashboard && <><div className="metric-grid"><div><span>Open findings</span><strong>{dashboard.openFindings.toLocaleString()}</strong></div><div><span>Closed findings</span><strong>{dashboard.closedFindings.toLocaleString()}</strong></div><div><span>Snapshot as of (UTC)</span><strong>{new Date(dashboard.asOf).toLocaleString()}</strong></div></div><div className="chart-grid"><DonutChart title="Risk Rating" values={dashboard.riskRating}/><DonutChart title="Compliance Status" values={dashboard.complianceStatus}/><BarChart title="Application" values={dashboard.applications}/><BarChart title="AppTag" values={dashboard.appTags}/><BarChart title="Environment" values={dashboard.environments}/><BarChart title="Risk score" values={dashboard.riskScores}/><BarChart title="Severity" values={dashboard.severities}/><BarChart title="Remediation progress" values={dashboard.remediation}/></div></>}
    {tab === 'Comments' && supportsWorkspace && <div className="report-card"><h3>IP → Application → AppTag</h3><p>Comments are attached to the selected asset and AppTag context.</p><div className="history-table-wrap"><table className="history-table"><thead><tr><th>IP Address</th><th>Application</th><th>AppTags</th><th>Critical</th><th>High</th><th>Medium</th><th>Low</th><th>Comments</th><th>Remediation</th></tr></thead><tbody>{groups?.content.map(g => <tr key={`${g.ipAddress}|${g.applicationName}|${g.appTag}`}><td>{g.ipAddress}</td><td>{g.applicationName}</td><td>{g.appTag}</td><td>{g.critical}</td><td>{g.high}</td><td>{g.medium}</td><td>{g.low}</td><td><button className="text-button" onClick={() => void openComments(g)}>Open</button></td><td><button className="text-button" onClick={() => { setFilters({ ...filters, ip: g.ipAddress, application: g.applicationName, appTag: g.appTag }); setTab('Data'); }}>Review</button></td></tr>)}</tbody></table></div><Pager page={groups} current={page} setPage={setPage}/></div>}
    {tab === 'Data' && supportsWorkspace && <div className="report-card"><h3>Finding observations</h3><div className="history-table-wrap"><table className="history-table"><thead><tr><th>UID</th><th>Finding</th><th>IP / Application</th><th>AppTag</th><th>Risk</th><th>Compliance</th><th>Environment</th><th>Lifecycle</th><th>Remediation</th><th>Actions</th></tr></thead><tbody>{rows?.content.map((f,i)=><tr key={`${f.findingId || f.sourceUid}-${i}`}><td>{f.sourceUid || 'Unmatched'}</td><td title={f.results}>{f.displayName || f.type || '—'}</td><td>{f.ipAddress || '—'}<br/><small>{f.applicationName || '—'}</small></td><td>{f.appTag || '—'}</td><td>{f.riskRating || '—'}<br/><small>{f.riskScore || ''}</small></td><td>{f.complianceStatus || '—'}</td><td>{f.environment || '—'}</td><td>{f.lifecycleState}<br/><small>{f.classification}</small></td><td>{f.remediationAction || 'Not started'}<br/><small>{f.remediationDate || ''}</small></td><td><div className="row-actions"><button onClick={()=>void showFindingHistory(f)}>History</button><select aria-label="Remediation action" defaultValue="" onChange={e=>{if(e.target.value) void chooseAction(f,e.target.value);e.target.value='';}}><option value="">Remediate…</option><option value="FALSE_POSITIVE_RAISED">Raise false positive</option>{admin&&<option value="FALSE_POSITIVE_APPROVED">Approve false positive</option>}<option value="REMEDIATION_DATE">Set remediation date</option><option value="ALREADY_REMEDIATED">Already remediated</option></select>{admin&&f.classification==='MISSING_FROM_COMPLETE_SNAPSHOT'&&<button onClick={()=>{const reason=window.prompt('Reason for confirming closure?');if(reason&&f.findingId) void request(`/api/admin/findings/${f.findingId}/close`,{method:'POST',body:JSON.stringify({reason})}).then(()=>loadRows()).catch(e=>setError(message(e)));}}>Review close</button>}</div></td></tr>)}</tbody></table></div><Pager page={rows} current={page} setPage={setPage}/></div>}

    {modal === 'upload' && admin && <div className="modal-backdrop" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)setModal(null);}}><div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="upload-title"><div className="modal-heading"><h3 id="upload-title">Upload {section.name}</h3><button onClick={()=>setModal(null)} aria-label="Close">×</button></div><p>Files are validated before activation. Maximum size: 50 MB.</p><label className="snapshot-option"><input type="checkbox" checked={completeSnapshot} onChange={e=>setCompleteSnapshot(e.target.checked)}/> This file is a complete snapshot for this section <small>Missing findings are marked for manual admin review; they are never closed automatically.</small></label><div className={`drop-zone${dragging?' is-dragging':''}`} onDragEnter={e=>{e.preventDefault();setDragging(true);}} onDragOver={e=>e.preventDefault()} onDragLeave={e=>{if(!e.currentTarget.contains(e.relatedTarget as Node))setDragging(false);}} onDrop={dropFile}><input ref={fileInput} type="file" accept=".csv,.xls,.xlsx" hidden onChange={e=>{void submitFile(e.target.files?.[0]);e.target.value='';}}/><strong>Drop a file here</strong><span>or</span><button className="primary-button" disabled={uploading} onClick={()=>fileInput.current?.click()}>{uploading?'Uploading…':'Choose file'}</button>{uploading&&<><progress max="100" value={progress}/><span>{progress}%</span></>}</div></div></div>}
    {modal === 'history' && admin && <div className="modal-backdrop" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)setModal(null);}}><div className="modal-card modal-wide" role="dialog" aria-modal="true"><div className="modal-heading"><h3>Import history · {section.name}</h3><button onClick={()=>setModal(null)} aria-label="Close">×</button></div>{active&&<p className="active-import-summary"><b>Active:</b> {active.originalFilename} · {active.acceptedRows.toLocaleString()} records</p>}<div className="history-table-wrap"><table className="history-table"><thead><tr><th>File</th><th>Uploaded</th><th>Status</th><th>Rows</th><th>Accepted</th><th>Rejected</th><th>Snapshot</th></tr></thead><tbody>{history?.content.map(b=><tr key={b.id}><td>{b.originalFilename}</td><td>{new Date(b.createdAt).toLocaleString()}</td><td>{b.status}</td><td>{b.totalRows}</td><td>{b.acceptedRows}</td><td>{b.rejectedRows}</td><td>{b.completeSnapshot?'Complete':'Partial'}</td></tr>)}</tbody></table></div><Pager page={history} current={page} setPage={setPage}/></div></div>}
    {selectedGroup && <div className="modal-backdrop" role="presentation"><div className="modal-card" role="dialog" aria-modal="true"><div className="modal-heading"><h3>{selectedGroup.appTag} comments</h3><button onClick={()=>setSelectedGroup(null)} aria-label="Close">×</button></div><p>{selectedGroup.ipAddress} · {selectedGroup.applicationName}</p><div className="comment-list">{comments?.content.map(c=><article key={c.id}><p>{c.text}</p><small>{c.actorUserId} · {new Date(c.createdAt).toLocaleString()}</small></article>)}</div><textarea rows={4} value={commentText} onChange={e=>setCommentText(e.target.value)} placeholder="Add a comment for this IP, application and AppTag"/><button className="primary-button" onClick={()=>void postComment()}>Add comment</button></div></div>}
    {selectedFinding && <div className="modal-backdrop" role="presentation"><div className="modal-card modal-wide" role="dialog" aria-modal="true"><div className="modal-heading"><h3>Finding history · {selectedFinding.sourceUid || selectedFinding.displayName}</h3><button onClick={()=>setSelectedFinding(null)} aria-label="Close">×</button></div><div className="history-timeline">{findingHistory.map((h,i)=><article key={`${h.kind}-${h.at}-${i}`}><b>{h.kind}: {h.action}</b><span>{new Date(h.at).toLocaleString()} · {h.actorUserId || 'System'} {h.status ? `· ${h.status}` : ''}</span><p>{h.detail}</p>{admin && h.kind==='RCA' && h.status==='PENDING' && h.referenceId && <div className="row-actions"><button onClick={()=>void reviewRca(h.referenceId!,true)}>Approve RCA</button><button onClick={()=>void reviewRca(h.referenceId!,false)}>Reject RCA</button></div>}</article>)}</div><label>RCA for reopened finding<textarea rows={4} value={rca} onChange={e=>setRca(e.target.value)} placeholder="Describe root cause"/></label><button className="primary-button" onClick={async()=>{if(!selectedFinding.findingId||!rca.trim())return;try{await request(`/api/findings/${selectedFinding.findingId}/rca`,{method:'POST',body:JSON.stringify({narrative:rca})});setNotice('RCA submitted for administrator review.');setRca('');const result=await request<ApiResponse<HistoryItem[]>>(`/api/findings/${selectedFinding.findingId}/history`);setFindingHistory(result.data||[]);}catch(e){setError(message(e));}}}>Submit RCA</button></div></div>}
  </section>;
};

function Pager({ page, current, setPage }: { page: Page<unknown> | null; current: number; setPage: React.Dispatch<React.SetStateAction<number>> }) {
  if (!page) return null;
  return <div className="pagination-row"><span>{page.totalElements.toLocaleString()} records</span><div><button disabled={current <= 0} onClick={()=>setPage(p=>Math.max(0,p-1))}>Previous</button><span>Page {page.pageNumber+1} of {Math.max(1,page.totalPages)}</span><button disabled={page.last} onClick={()=>setPage(p=>p+1)}>Next</button></div></div>;
}
