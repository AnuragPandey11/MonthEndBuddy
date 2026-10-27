import { useMutation, useQuery } from '@tanstack/react-query'
import { api, ApiError, refreshSession } from '../../lib/api/client'
import { tokenStore } from '../../lib/auth/tokenStore'
import type { ImportJob, ImportResult, ImportRow } from '../../lib/api/types'
import { useInvalidateFinance } from '../../lib/api/finance'

type DateFormat = ImportJob['dateFormat']

/** Multipart upload; the JSON client can't send files, so this mirrors its auth and problem handling. */
async function upload(file: File, dateFormat: DateFormat): Promise<ImportJob> {
  const send = () => {
    const body = new FormData()
    body.append('file', file)
    const token = tokenStore.get()
    return fetch(`/api/v1/imports?dateFormat=${dateFormat}`, {
      method: 'POST',
      body,
      headers: { 'X-MEB-Client': 'web', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      credentials: 'same-origin',
    })
  }
  let response = await send()
  if (response.status === 401 && (await refreshSession())) response = await send()
  const json = (await response.json().catch(() => ({}))) as Record<string, unknown>
  if (!response.ok) {
    throw new ApiError(
      response.status,
      typeof json.code === 'string' ? json.code : 'error',
      typeof json.detail === 'string' ? json.detail : 'Upload failed',
      Array.isArray(json.errors) ? (json.errors as { field: string; message: string }[]) : [],
    )
  }
  return json as unknown as ImportJob
}

export function useUpload() {
  return useMutation({
    mutationFn: ({ file, dateFormat }: { file: File; dateFormat: DateFormat }) => upload(file, dateFormat),
  })
}

export function useImportRows(jobId: string | undefined) {
  return useQuery({
    queryKey: ['imports', jobId, 'rows'],
    queryFn: ({ signal }) => api<ImportRow[]>(`/api/v1/imports/${jobId}/rows`, { signal }),
    enabled: Boolean(jobId),
  })
}

export function useCommit() {
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: ({ jobId, forceRowIds }: { jobId: string; forceRowIds: string[] }) =>
      api<ImportResult>(`/api/v1/imports/${jobId}/commit`, { method: 'POST', body: { forceRowIds } }),
    onSuccess: invalidate,
  })
}

export function useDiscard() {
  return useMutation({
    mutationFn: (jobId: string) => api<void>(`/api/v1/imports/${jobId}`, { method: 'DELETE' }),
  })
}
