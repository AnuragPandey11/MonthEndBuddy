import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from 'react-router'
import { Button } from '../../components/Button'
import { Field, Input } from '../../components/Field'
import { ApiError } from '../../lib/api/client'
import { AuthLayout } from './AuthLayout'
import { registerSchema, type RegisterValues } from './schemas'
import { useAuth } from './useAuth'

export function RegisterPage() {
  const { register: signUp } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await signUp(values.email, values.password, values.displayName)
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'email_taken') {
        setError('email', { message: 'An account with this email already exists.' })
      } else if (error instanceof ApiError && error.errors.length > 0) {
        for (const violation of error.errors) {
          const field = violation.field as keyof RegisterValues
          if (field in values) setError(field, { message: violation.message })
        }
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Something went wrong. Try again.')
      }
    }
  })

  return (
    <AuthLayout title="Create your account" subtitle="Import transactions, plan budgets, ask questions.">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <Field label="Name" error={errors.displayName?.message}>
          {(a11y) => <Input {...a11y} autoComplete="name" {...register('displayName')} />}
        </Field>
        <Field label="Email" error={errors.email?.message}>
          {(a11y) => <Input {...a11y} type="email" autoComplete="email" {...register('email')} />}
        </Field>
        <Field label="Password" error={errors.password?.message} hint="At least 10 characters.">
          {(a11y) => (
            <Input {...a11y} type="password" autoComplete="new-password" {...register('password')} />
          )}
        </Field>
        {formError && (
          <p role="alert" className="text-sm text-negative">
            {formError}
          </p>
        )}
        <Button type="submit" loading={isSubmitting}>
          Create account
        </Button>
      </form>
      <p className="mt-5 text-center text-sm text-muted">
        Already have an account?{' '}
        <Link to="/login" className="text-accent hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
