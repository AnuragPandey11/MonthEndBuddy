import { z } from 'zod'

/** Mirrors the backend rules (architecture §5.1); the server remains the authority. */
export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email'),
  password: z.string().min(1, 'Enter your password'),
})

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, 'Enter your name').max(100, 'Use at most 100 characters'),
  email: z.email('Enter a valid email address').max(254),
  password: z.string().min(10, 'Use at least 10 characters').max(128, 'Use at most 128 characters'),
})

export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
