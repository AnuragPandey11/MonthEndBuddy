import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api/client'
import type { ConversationSummary, ConversationView } from './types'

export function useConversations() {
  return useQuery({
    queryKey: ['agent', 'conversations'],
    queryFn: ({ signal }) => api<ConversationSummary[]>('/api/v1/agent/conversations', { signal }),
  })
}

/** A stored conversation. Never refetched in the background: it is a snapshot, re-rendered "as of" its time. */
export function useConversation(id: string | null) {
  return useQuery({
    queryKey: ['agent', 'conversation', id],
    queryFn: ({ signal }) => api<ConversationView>(`/api/v1/agent/conversations/${id}`, { signal }),
    enabled: id !== null,
    staleTime: Infinity,
  })
}

export function useDeleteConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/agent/conversations/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['agent', 'conversations'] }),
  })
}
