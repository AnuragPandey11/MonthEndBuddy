import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from 'react-router'
import { FlaskConical } from 'lucide-react'
import { Button } from '../../components/Button'
import { Field, Input } from '../../components/Field'
import { ApiError } from '../../lib/api/client'
import { AuthLayout } from './AuthLayout'
import { loginSchema, type LoginValues } from './schemas'
import { useAuth } from './useAuth'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'invalid_credentials') return 'Email or password is incorrect.'
    if (error.code === 'rate_limited') return 'Too many attempts. Wait a minute and try again.'
    return error.message
  }
  return 'Something went wrong. Try again.'
}

export function LoginPage() {
  const { login, loginAsDemo } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const [demoLoading, setDemoLoading] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await login(values.email, values.password)
      navigate('/', { replace: true })
    } catch (error) {
      setFormError(messageFor(error))
    }
  })

  const onDemo = async () => {
    setFormError(null)
    setDemoLoading(true)
    try {
      await loginAsDemo()
      navigate('/', { replace: true })
    } catch (error) {
      setFormError(messageFor(error))
    } finally {
      setDemoLoading(false)
    }
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to see where your money goes.">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <Field label="Email" error={errors.email?.message}>
          {(a11y) => <Input {...a11y} type="email" autoComplete="email" {...register('email')} />}
        </Field>
        <Field label="Password" error={errors.password?.message}>
          {(a11y) => (
            <Input {...a11y} type="password" autoComplete="current-password" {...register('password')} />
          )}
        </Field>
        {formError && (
          <p role="alert" className="text-sm text-negative">
            {formError}
          </p>
        )}
        <Button type="submit" loading={isSubmitting}>
          Sign in
        </Button>
      </form>
      <div className="my-4 flex items-center gap-3 text-xs text-faint">
        <span className="h-px flex-1 bg-line" />
        or
        <span className="h-px flex-1 bg-line" />
      </div>
      <Button variant="secondary" className="w-full" onClick={onDemo} loading={demoLoading}>
        <FlaskConical className="size-4" aria-hidden />
        Explore with demo data
      </Button>
      <p className="mt-2 text-center text-xs text-faint">Synthetic transactions only, reset every night.</p>
      <p className="mt-5 text-center text-sm text-muted">
        New here?{' '}
        <Link to="/register" className="text-accent hover:underline">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
