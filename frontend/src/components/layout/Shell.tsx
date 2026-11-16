import React, { useEffect, useState } from 'react';
import { request, ApiResponse } from '../../api/httpClient';
import { LoginForm, AuthUser } from '../../features/auth/LoginForm';
import { ReportSectionPage } from '../../features/reports/ReportSectionPage';
import { ReportSectionsPage } from '../../features/reports/ReportSectionsPage';
import { CmdbManagementPage } from '../../features/cmdb/CmdbManagementPage';
import { ExecutiveDashboardPage } from '../../features/reports/ExecutiveDashboardPage';
import { AdminUsersPage } from '../../features/user/AdminUsersPage';
import { ActivateAccountPage } from '../../features/auth/ActivateAccountPage';
import { ChangePasswordDialog } from '../../features/auth/ChangePasswordDialog';

interface ShellProps {
  children?: React.ReactNode;
}

interface HealthStatus {
  status: string;
}

export const Shell: React.FC<ShellProps> = ({ children }) => {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null);
  const [authChecking, setAuthChecking] = useState<boolean>(true);
  const [health, setHealth] = useState<string>('Checking...');
  const [activeSection, setActiveSection] = useState<string>('REPORT_SECTIONS');
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);

  useEffect(() => {
    // Materialize the CSRF cookie before any later state-changing request.
    // Login is exempt from CSRF, so without this bootstrap the first uploads,
    // CMDB confirmations and admin actions are rejected with HTTP 403.
    request<ApiResponse<string>>('/api/auth/csrf')
      .catch(() => undefined)
      .finally(() => {
        request<HealthStatus>('/actuator/health')
          .then((data) => setHealth(data.status))
          .catch(() => setHealth('OFFLINE'));
        request<ApiResponse<AuthUser>>('/api/auth/me')
          .then((res) => {
            if (res.success && res.data) setCurrentUser(res.data);
            else setCurrentUser(null);
          })
          .catch(() => setCurrentUser(null))
          .finally(() => setAuthChecking(false));
      });
  }, []);

  const handleLogout = async () => {
    try {
      await request<ApiResponse<string>>('/api/auth/logout', { method: 'POST' });
    } catch {
      // ignore
    } finally {
      setCurrentUser(null);
    }
  };

  if (window.location.pathname === '/activate') return <ActivateAccountPage />;

  if (authChecking) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <p style={{ color: 'var(--color-primary)', fontWeight: 600 }}>Loading VAPT Management Platform...</p>
      </div>
    );
  }

  if (!currentUser) {
    return <LoginForm onLoginSuccess={(user) => setCurrentUser(user)} />;
  }

  const reportSections = [
    { id: 'GDP_VA_REPORT', name: 'GDP VA Report' },
    { id: 'INTERNAL_VA_BRINQA', name: 'Internal VA Brinqa' },
    { id: 'WIZ', name: 'Wiz' },
    { id: 'VERIZON_26_NETWORK_PT', name: 'VERIZON 26 Network PT' },
    { id: 'PT_VERIZON_GCP', name: 'PT Verizon GCP' },
    { id: 'APPSEC_FINDINGS_PT', name: 'AppSec Findings PT' },
  ];

  const selectedReportSection = reportSections.find((section) => section.id === activeSection);

  return (
    <div style={{ display: 'flex', minHeight: '100vh', flexDirection: 'column' }}>
      {/* Header */}
      <header
        style={{
          backgroundColor: 'var(--color-primary)',
          color: '#ffffff',
          padding: '0.75rem 1.5rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '4px solid var(--color-accent)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div
            style={{
              backgroundColor: 'var(--color-accent)',
              color: 'var(--color-primary-dark)',
              fontWeight: 800,
              padding: '0.25rem 0.6rem',
              borderRadius: '4px',
              fontSize: '1rem',
            }}
          >
            VAPT
          </div>
          <h1 style={{ fontSize: '1.2rem', fontWeight: 600 }}>VAPT Management Platform</h1>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.875rem' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            System:
            <span
              style={{
                display: 'inline-block',
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: health === 'UP' ? 'var(--color-success)' : 'var(--color-danger)',
              }}
            />
            <strong>{health}</strong>
          </span>

          <span style={{ opacity: 0.8 }}>|</span>

          <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span>{currentUser.fullName} ({currentUser.email})</span>
            <span
              style={{
                backgroundColor: currentUser.role === 'ADMIN' ? 'var(--color-accent)' : '#e2e8f0',
                color: currentUser.role === 'ADMIN' ? 'var(--color-primary-dark)' : 'var(--color-text-main)',
                fontWeight: 700,
                fontSize: '0.75rem',
                padding: '0.15rem 0.4rem',
                borderRadius: '3px',
              }}
            >
              {currentUser.role}
            </span>
          </span>

          <button
            onClick={() => setChangePasswordOpen(true)}
            style={{ backgroundColor: 'transparent', border: '1px solid #ffffff', color: '#ffffff', padding: '0.25rem 0.6rem', borderRadius: '4px', fontSize: '0.8rem', cursor: 'pointer' }}
          >Change password</button>
          <button
            onClick={handleLogout}
            style={{
              backgroundColor: 'transparent',
              border: '1px solid #ffffff',
              color: '#ffffff',
              padding: '0.25rem 0.6rem',
              borderRadius: '4px',
              fontSize: '0.8rem',
              cursor: 'pointer',
            }}
          >
            Sign Out
          </button>
        </div>
      </header>

      {/* Main body: sidebar + content */}
      <div style={{ display: 'flex', flex: 1 }}>
        {/* Left Sidebar */}
        <aside
          style={{
            width: '260px',
            backgroundColor: 'var(--color-surface)',
            borderRight: '1px solid var(--color-border)',
            padding: '1rem 0',
          }}
        >
          <div style={{ padding: '0.5rem 1.25rem', fontSize: '0.75rem', fontWeight: 700, color: 'var(--color-text-muted)', textTransform: 'uppercase' }}>
            Overview
          </div>
          <div
            onClick={() => setActiveSection('EXECUTIVE')}
            style={{
              padding: '0.6rem 1.25rem',
              cursor: 'pointer',
              backgroundColor: activeSection === 'EXECUTIVE' ? '#e6f0f7' : 'transparent',
              color: activeSection === 'EXECUTIVE' ? 'var(--color-primary)' : 'var(--color-text-main)',
              fontWeight: activeSection === 'EXECUTIVE' ? 600 : 400,
              borderLeft: activeSection === 'EXECUTIVE' ? '4px solid var(--color-primary)' : '4px solid transparent',
            }}
          >
            Executive Dashboard
          </div>

          <div onClick={() => setActiveSection('REPORT_SECTIONS')} style={{ padding: '1rem 1.25rem 0.5rem', fontSize: '0.75rem', fontWeight: 700, color: 'var(--color-text-muted)', textTransform: 'uppercase', cursor: 'pointer' }}>
            Report Sections (6)
          </div>
          {reportSections.map((sec) => (
            <div
              key={sec.id}
              onClick={() => setActiveSection(sec.id)}
              style={{
                padding: '0.55rem 1.25rem',
                cursor: 'pointer',
                fontSize: '0.9rem',
                backgroundColor: activeSection === sec.id ? '#e6f0f7' : 'transparent',
                color: activeSection === sec.id ? 'var(--color-primary)' : 'var(--color-text-main)',
                fontWeight: activeSection === sec.id ? 600 : 400,
                borderLeft: activeSection === sec.id ? '4px solid var(--color-primary)' : '4px solid transparent',
              }}
            >
              {sec.name}
            </div>
          ))}

          {currentUser.role === 'ADMIN' && (
            <>
              <div style={{ padding: '1rem 1.25rem 0.5rem', fontSize: '0.75rem', fontWeight: 700, color: 'var(--color-text-muted)', textTransform: 'uppercase' }}>
                Administration
              </div>
              <div
                onClick={() => setActiveSection('CMDB')}
                style={{
                  padding: '0.55rem 1.25rem', cursor: 'pointer', fontSize: '0.9rem',
                  backgroundColor: activeSection === 'CMDB' ? '#e6f0f7' : 'transparent',
                  color: activeSection === 'CMDB' ? 'var(--color-primary)' : 'var(--color-text-main)',
                  fontWeight: activeSection === 'CMDB' ? 600 : 400,
                  borderLeft: activeSection === 'CMDB' ? '4px solid var(--color-primary)' : '4px solid transparent',
                }}
              >
                CMDB Management
              </div>
              <div
                onClick={() => setActiveSection('ADMIN_USERS')}
                style={{
                  padding: '0.55rem 1.25rem',
                  cursor: 'pointer',
                  fontSize: '0.9rem',
                  backgroundColor: activeSection === 'ADMIN_USERS' ? '#e6f0f7' : 'transparent',
                  color: activeSection === 'ADMIN_USERS' ? 'var(--color-primary)' : 'var(--color-text-main)',
                  fontWeight: activeSection === 'ADMIN_USERS' ? 600 : 400,
                  borderLeft: activeSection === 'ADMIN_USERS' ? '4px solid var(--color-primary)' : '4px solid transparent',
                }}
              >
                User & Scope Management
              </div>
            </>
          )}
        </aside>

        {/* Content Area */}
        <main style={{ flex: 1, padding: '2rem', backgroundColor: 'var(--color-bg)' }}>
          {selectedReportSection ? (
            <ReportSectionPage section={selectedReportSection} role={currentUser.role} email={currentUser.email} />
          ) : activeSection === 'ADMIN_USERS' && currentUser.role === 'ADMIN' ? (
            <AdminUsersPage />
          ) : activeSection === 'CMDB' && currentUser.role === 'ADMIN' ? (
            <CmdbManagementPage />
          ) : activeSection === 'EXECUTIVE' ? (
            <ExecutiveDashboardPage sections={reportSections} onSelect={setActiveSection} />
          ) : activeSection === 'REPORT_SECTIONS' ? (
            <ReportSectionsPage sections={reportSections} onSelect={setActiveSection} />
          ) : children ? (
            children
          ) : (
            <div
              style={{
                backgroundColor: 'var(--color-surface)',
                borderRadius: '8px',
                padding: '2rem',
                border: '1px solid var(--color-border)',
                boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
              }}
            >
              <h2 style={{ fontSize: '1.4rem', color: 'var(--color-primary)', marginBottom: '0.5rem' }}>
                Phase 2 — Identity & Security Active
              </h2>
              <p style={{ color: 'var(--color-text-muted)', marginBottom: '1.5rem' }}>
                Active Navigation: <strong>{activeSection}</strong>
              </p>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
                <div style={{ padding: '1rem', border: '1px solid var(--color-border)', borderRadius: '6px', backgroundColor: '#fcfcfc' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', fontWeight: 600 }}>AUTHENTICATED AS</div>
                  <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--color-primary)', marginTop: '0.25rem' }}>
                    {currentUser.email}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.25rem' }}>
                    Role: {currentUser.role} • Status: {currentUser.status}
                  </div>
                </div>

                <div style={{ padding: '1rem', border: '1px solid var(--color-border)', borderRadius: '6px', backgroundColor: '#fcfcfc' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', fontWeight: 600 }}>ASSIGNED SCOPE</div>
                  <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--color-primary-dark)', marginTop: '0.25rem' }}>
                    {currentUser.role === 'ADMIN' ? 'Universal (All Assets)' : `${currentUser.scopes.length} Asset Scope(s)`}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.25rem' }}>
                    Enforced by backend on every query
                  </div>
                </div>

                <div style={{ padding: '1rem', border: '1px solid var(--color-border)', borderRadius: '6px', backgroundColor: '#fcfcfc' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', fontWeight: 600 }}>SECURITY SESSION</div>
                  <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--color-success)', marginTop: '0.25rem' }}>
                    Active (Session Cookie)
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.25rem' }}>
                    CSRF Protection Enabled
                  </div>
                </div>

                <div style={{ padding: '1rem', border: '1px solid var(--color-border)', borderRadius: '6px', backgroundColor: '#fcfcfc' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', fontWeight: 600 }}>AUDIT LOGGING</div>
                  <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--color-primary)', marginTop: '0.25rem' }}>
                    Audit Events Tracked
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.25rem' }}>
                    Logins, Lockouts, Scope Updates
                  </div>
                </div>
              </div>

              {currentUser.role === 'USER' && (
                <div style={{ marginTop: '1.5rem', padding: '1rem', backgroundColor: '#f8fafc', border: '1px solid var(--color-border)', borderRadius: '6px' }}>
                  <h3 style={{ fontSize: '1rem', color: 'var(--color-primary)', marginBottom: '0.5rem' }}>Your Permitted Asset/Application Scopes:</h3>
                  {currentUser.scopes.length === 0 ? (
                    <p style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem' }}>No asset scopes mapped yet. Contact an administrator.</p>
                  ) : (
                    <ul style={{ paddingLeft: '1.25rem', fontSize: '0.85rem' }}>
                      {currentUser.scopes.map((s) => (
                        <li key={s.id} style={{ margin: '0.25rem 0' }}>
                          <strong>IP:</strong> {s.ipAddress || 'Any'} | <strong>Application:</strong> {s.applicationName || 'Any'}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>
          )}
        </main>
      </div>
      {changePasswordOpen && <ChangePasswordDialog onClose={() => setChangePasswordOpen(false)} />}
    </div>
  );
};
