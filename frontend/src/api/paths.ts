/**
 * Converts a backend file URL into a path relative to apiClient's `/biopay` base URL.
 * A leading slash would make Axios discard that base path and request `/api/...` instead.
 */
export function apiRelativeFilePath(value: string): string {
  return String(value ?? '')
    .trim()
    .replace(/^https?:\/\/[^/]+\/?/i, '')
    .replace(/^\/+/, '')
    .replace(/^biopay\/+/, '')
}
