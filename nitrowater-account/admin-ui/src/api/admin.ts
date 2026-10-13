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
}
