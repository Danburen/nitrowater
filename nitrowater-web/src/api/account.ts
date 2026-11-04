/**
 * Account-center API client.
 *
 * <p>All calls go through the BFF (`/api/**`); mutating calls get the CSRF header from
 * {@link apiFetch}. The browser never holds an access token.</p>
 */
import { apiFetch } from '../auth/bff'
import { readJson } from './envelope'

export interface AccountMe {
  uid: number
  username: string
  nickname: string | null
  phoneMasked: string | null
  emailMasked: string | null
  phoneVerified: boolean
  emailVerified: boolean
  passwordSet: boolean
}

export interface ReAuthInfo {
  maskedPhone: string | null
  maskedEmail: string | null
  availableChannels: string[]
}

export type VerifyChannel = 'SMS' | 'EMAIL'
export type ReAuthScene =
  | 'reset_password'
  | 'set_password'
  | 'change_email'
  | 'change_phone'
  | 'activate'
  | 'unbind'

export interface ReAuthToken {
  reAuthTokenValue: string | null
}

export interface ChangeTarget {
  verifyKey: string
}

/** Stable per-browser fingerprint for account audit/quota context (not an auth factor). */
function deviceFp(): string {
  const key = 'nitrowater:deviceFp'
  try {
    const existing = window.localStorage.getItem(key)
    if (existing) return existing
    const value = crypto.randomUUID()
    window.localStorage.setItem(key, value)
    return value
  } catch {
    return crypto.randomUUID()
  }
}

function deviceInfo(): { deviceFp: string; os: string; browser: string } {
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } }
  return {
    deviceFp: deviceFp(),
    os: nav.userAgentData?.platform || navigator.platform || 'unknown',
    browser: navigator.userAgent,
  }
}

function jsonInit(payload: unknown, method = 'POST'): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }
}

export async function fetchAccountMe(): Promise<AccountMe> {
  return readJson<AccountMe>(await apiFetch('/api/auth/account/me'))
}

export async function updateNickname(nickname: string): Promise<void> {
  await readJson<void>(await apiFetch('/api/auth/account/nickname', jsonInit({ nickname }, 'PUT')))
}

export async function fetchReAuthInfo(): Promise<ReAuthInfo> {
  return readJson<ReAuthInfo>(await apiFetch('/api/auth/account/re-auth/info'))
}

export async function sendReAuthCode(scene: ReAuthScene, channel: VerifyChannel): Promise<string> {
  const data = await readJson<{ reAuthKey: string }>(await apiFetch(
    '/api/auth/account/re-auth',
    jsonInit({ scene, channel }),
  ))
  if (!data.reAuthKey) throw new Error('验证码发送失败，请重试')
  return data.reAuthKey
}

export async function verifyReAuthCode(
  scene: ReAuthScene,
  channel: VerifyChannel,
  verifyKey: string,
  code: string,
): Promise<string> {
  const data = await readJson<ReAuthToken>(await apiFetch(
    '/api/auth/account/re-auth/verify',
    jsonInit({ scene, channel, verifyKey, code }),
  ))
  if (!data.reAuthTokenValue) throw new Error('验证码验证失败')
  return data.reAuthTokenValue
}

export async function resetPassword(newPwd: string, confirmPwd: string, reAuthToken: string): Promise<void> {
  await readJson<void>(await apiFetch('/api/auth/account/password/reset', jsonInit({
    reAuthToken,
    newPwd,
    confirmPwd,
    deviceInfo: deviceInfo(),
  })))
}

export async function setPassword(newPwd: string, confirmPwd: string, reAuthToken: string): Promise<void> {
  await readJson<void>(await apiFetch('/api/auth/account/password/set', jsonInit({
    reAuthToken,
    newPwd,
    confirmPwd,
    deviceInfo: deviceInfo(),
  })))
}

export async function changeEmail(newEmail: string, reAuthToken: string): Promise<ChangeTarget> {
  return readJson<ChangeTarget>(await apiFetch('/api/auth/account/email/change', jsonInit({
    reAuthToken,
    newEmail,
    deviceInfo: deviceInfo(),
  })))
}

export async function verifyChangeEmail(verifyKey: string, code: string): Promise<void> {
  await readJson<void>(await apiFetch('/api/auth/account/email/change/verify', jsonInit({
    verifyKey,
    code,
    deviceInfo: deviceInfo(),
  })))
}

export async function activateEmail(reAuthToken: string): Promise<ChangeTarget> {
  return readJson<ChangeTarget>(await apiFetch('/api/auth/account/email/activate', jsonInit({
    reAuthToken,
    deviceInfo: deviceInfo(),
  })))
}

export async function unbindEmail(reAuthToken: string): Promise<void> {
  await readJson<void>(await apiFetch('/api/auth/account/email/unbind', jsonInit({
    reAuthToken,
    deviceInfo: deviceInfo(),
  })))
}

export async function changePhone(newPhone: string, reAuthToken: string): Promise<ChangeTarget> {
  return readJson<ChangeTarget>(await apiFetch('/api/auth/account/phone/change', jsonInit({
    reAuthToken,
    newPhone,
    deviceInfo: deviceInfo(),
  })))
}

export async function verifyChangePhone(verifyKey: string, code: string): Promise<void> {
  await readJson<void>(await apiFetch('/api/auth/account/phone/change/verify', jsonInit({
    verifyKey,
    code,
    deviceInfo: deviceInfo(),
  })))
}

export async function activatePhone(reAuthToken: string, phone?: string): Promise<ChangeTarget> {
  return readJson<ChangeTarget>(await apiFetch('/api/auth/account/phone/activate', jsonInit({
    reAuthToken,
    phone,
    deviceInfo: deviceInfo(),
  })))
}
