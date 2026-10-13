import { http } from './http'

export interface RoleVO {
  id: number
  code: string
  name: string
  description: string | null
  builtin: boolean
  userCount: number
  createdAt: string
}

export interface UserSummaryVO {
  uid: number
  username: string
  nickname: string | null
  accountStatus: string
  userType: string
  roles: string[]
  createdAt: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface AdminSessionVO {
  uid: number
  username: string
  name: string
  roles: string[]
}

export interface ClientVO {
  id: string
  clientId: string
  clientName: string
  clientAuthenticationMethods: string[]
  authorizationGrantTypes: string[]
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  scopes: string[]
  requireProofKey: boolean
  requireAuthorizationConsent: boolean
  accessTokenTtlMinutes: number
  refreshTokenTtlDays: number
  clientIdIssuedAt: string
  /** True when the client holds a secret (any non-`none` auth method). */
  confidential: boolean
  /** True for first-party clients that cannot be deleted. */
  protectedClient: boolean
}

export interface ClientRegistrationVO {
  client: ClientVO
  /** Plaintext secret, non-null only when this operation minted a new one (create / reset). */
  clientSecret: string | null
}

export interface ClientSecretVO {
  clientSecret: string
}

export interface ClientForm {
  clientId: string
  clientName: string
  clientAuthenticationMethods: string[]
  authorizationGrantTypes: string[]
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  scopes: string[]
  requireProofKey: boolean
  requireAuthorizationConsent: boolean
  accessTokenTtlMinutes: number
  refreshTokenTtlDays: number
}

export interface RoleForm {
  code: string
  name: string
  description: string
}

export const adminApi = {
  session: () => http.get<AdminSessionVO>('/api/admin/session'),

  listRoles: () => http.get<RoleVO[]>('/api/admin/roles'),
  createRole: (body: RoleForm) => http.post<RoleVO>('/api/admin/roles', body),
  updateRole: (id: number, body: { name: string; description: string }) =>
    http.put<RoleVO>(`/api/admin/roles/${id}`, body),
  deleteRole: (id: number) => http.del<void>(`/api/admin/roles/${id}`),

  listUsers: (keyword: string, page: number, size: number) =>
    http.get<PageResult<UserSummaryVO>>(
      `/api/admin/users?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`,
    ),
  assignRoles: (uid: number, roleCodes: string[]) =>
    http.put<string[]>(`/api/admin/users/${uid}/roles`, { roleCodes }),

  listClients: () => http.get<ClientVO[]>('/api/admin/clients'),
  createClient: (body: ClientForm) => http.post<ClientRegistrationVO>('/api/admin/clients', body),
  updateClient: (id: string, body: Omit<ClientForm, 'clientId'>) =>
    http.put<ClientRegistrationVO>(`/api/admin/clients/${id}`, body),
  resetClientSecret: (id: string) => http.post<ClientSecretVO>(`/api/admin/clients/${id}/secret`),
  deleteClient: (id: string) => http.del<void>(`/api/admin/clients/${id}`),
}
