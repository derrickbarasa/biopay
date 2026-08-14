import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { PUBLIC_PROCESSING_CODES, type ProcessingCode } from './processingCodes'

const TOKEN_KEY = 'bp_token'
const USER_KEY = 'bp_user'
const REFRESH_TOKEN_KEY = 'bp_refresh'

/** Marks which processing code produced a request, read back in the response interceptor -- a
 *  header rather than a parsed request body, so it survives whatever axios does to `config.data`
 *  on the way out. Purely a client-side signal; the backend ignores it. */
const PROCESSING_CODE_HEADER = 'X-Processing-Code'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:7730/biopay'

export const apiClient = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Bare client for the renewal call itself: going through `apiClient` would attach the expired
 *  access token and, on failure, re-enter the 401 handler below and recurse. */
const refreshClient = axios.create({ baseURL, headers: { 'Content-Type': 'application/json' } })

let renewalInFlight: Promise<string> | null = null

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function storeSession(accessToken: string, refreshToken: string, user: unknown) {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function readStoredUser<T>(): T | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

export function storedToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** Refresh tokens rotate on every use (see backend Auth#refreshToken) -- persist the replacement
 *  or the next renewal spends an already-revoked token. */
async function renewAccessToken(): Promise<string> {
  const stored = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!stored) throw new Error('No refresh token')

  const { data } = await refreshClient.post<{ accessToken: string; refreshToken: string }>(
    '/authentication',
    { processingCode: 'REFRESH_TOKEN', refreshToken: stored },
  )
  localStorage.setItem(TOKEN_KEY, data.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken)
  return data.accessToken
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined
    const processingCode = original?.headers?.[PROCESSING_CODE_HEADER] as ProcessingCode | undefined
    const isSessionCall = processingCode !== undefined && PUBLIC_PROCESSING_CODES.has(processingCode as any)

    if (
      error.response?.status === 401 &&
      original &&
      !original._retried &&
      !isSessionCall &&
      localStorage.getItem(REFRESH_TOKEN_KEY)
    ) {
      original._retried = true
      try {
        renewalInFlight ??= renewAccessToken().finally(() => { renewalInFlight = null })
        const token = await renewalInFlight
        original.headers.Authorization = `Bearer ${token}`
        return await apiClient(original)
      } catch {
        clearSession()
        if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
        return Promise.reject(new Error('Your session has expired. Please log in again.'))
      }
    }

    const message = (error.response?.data as { responseMessage?: string } | undefined)?.responseMessage
      ?? error.message ?? 'Request failed'
    return Promise.reject(Object.assign(new Error(message), {
      responseCode: (error.response?.data as { responseCode?: string } | undefined)?.responseCode,
      status: error.response?.status,
    }))
  },
)

/**
 * Sends one `{processingCode, ...params}` envelope to the backend's dispatch endpoint and returns
 * the parsed response body. Routes to `/authentication` (no Authorization header) or
 * `/api/v1/req` (JWT-backed) based on whether the code is a session-establishing one.
 */
export async function dispatch<T = any>(
  processingCode: ProcessingCode,
  params: Record<string, unknown> = {},
): Promise<T> {
  const url = PUBLIC_PROCESSING_CODES.has(processingCode as any) ? '/authentication' : '/api/v1/req'
  const { data } = await apiClient.post<T>(url, { processingCode, ...params }, {
    headers: { [PROCESSING_CODE_HEADER]: processingCode },
  })
  const body = data as any
  if (body && typeof body === 'object' && 'responseCode' in body && body.responseCode !== '000') {
    throw Object.assign(new Error(body.responseMessage ?? 'Request failed'), { responseCode: body.responseCode })
  }
  return data
}
