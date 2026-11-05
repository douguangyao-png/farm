import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

/** 种子目录：每个 json 文件是一个顶层集合，文件名即集合名 */
const SEED_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'seed')

export interface Tenant { id: number; name: string; contactName?: string; contactMobile?: string; status: number; packageId: number; expireTime?: number; websites?: string[] }
export interface Menu { id: number; parentId: number; name: string; path: string; component?: string; componentName?: string; icon?: string; visible: boolean; keepAlive: boolean; alwaysShow?: boolean; permission?: string }
export interface Role { id: number; tenantId: number; code: string; name: string; menuIds: number[] | '*' }
export interface User { id: number; tenantId: number; username: string; password: string; nickname: string; avatar?: string; deptId?: number; roleIds: number[]; kind: 'admin' | 'steward' }
export interface Member { id: number; tenantId: number; mobile: string; password: string; nickname: string; avatar?: string }

export interface Db {
  tenants: Tenant[]
  menus: Menu[]
  roles: Role[]
  users: User[]
  members: Member[]
  diyTemplates: Record<string, unknown>[]
  [collection: string]: unknown[]
}

let db: Db = loadSeed()

function loadSeed(): Db {
  const out: Record<string, unknown[]> = {}
  for (const f of readdirSync(SEED_DIR)) {
    if (!f.endsWith('.json')) continue
    out[f.slice(0, -5)] = JSON.parse(readFileSync(join(SEED_DIR, f), 'utf-8'))
  }
  return out as Db
}

export const getDb = (): Db => db

/** 恢复到种子数据（R-9） */
export function resetDb(): void {
  db = loadSeed()
}
