import React, { useEffect, useState } from 'react';
import { ApiResponse, request } from '../../api/httpClient';

function errorText(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'error' in error) return (error as {error?:{message?:string}}).error?.message || 'Activation failed.';
  return error instanceof Error ? error.message : 'Activation failed.';
}

export const ActivateAccountPage: React.FC = () => {
  const [token,setToken]=useState(''); const [password,setPassword]=useState(''); const [confirm,setConfirm]=useState('');
  const [error,setError]=useState<string|null>(null); const [done,setDone]=useState(false); const [loading,setLoading]=useState(false);
  useEffect(()=>{const match=window.location.hash.match(/(?:^#|&)token=([^&]+)/);if(match){setToken(decodeURIComponent(match[1]));window.history.replaceState(null,'',window.location.pathname);}},[]);
  const submit=async(e:React.FormEvent)=>{e.preventDefault();setError(null);if(password!==confirm){setError('Passwords do not match.');return;}setLoading(true);try{await request<ApiResponse<string>>('/api/auth/csrf');await request<ApiResponse<string>>('/api/auth/activate',{method:'POST',body:JSON.stringify({token,password})});setDone(true);setPassword('');setConfirm('');}catch(err){setError(errorText(err));}finally{setLoading(false);}};
  return <main className="account-page"><form className="account-card" onSubmit={submit}><h1>Activate your VAPT account</h1><p>Set a password to finish activating your company account.</p>{error&&<div className="inline-error" role="alert">{error}</div>}{done?<><div className="inline-success" role="status">Your account is active. Sign in to continue.</div><a className="primary-button" href="/">Go to sign in</a></>:<><label>New password<input type="password" required minLength={12} maxLength={128} value={password} onChange={e=>setPassword(e.target.value)} autoComplete="new-password"/><small>12–128 characters; include upper and lower case letters, a number and a symbol.</small></label><label>Confirm password<input type="password" required value={confirm} onChange={e=>setConfirm(e.target.value)} autoComplete="new-password"/></label><button className="primary-button" disabled={loading||!token}>{loading?'Activating…':'Activate account'}</button>{!token&&<p className="inline-error">The activation link is missing its token. Ask an administrator to issue a new link.</p>}</>}</form></main>;
};
