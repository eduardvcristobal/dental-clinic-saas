import { useGoogleOAuth } from '@react-oauth/google'
import { useEffect, useMemo, useRef } from 'react'
import { mountGoogleCredentialButton, type GoogleButtonOptions, type GoogleCredential } from './googleIdentity'

type Props = GoogleButtonOptions & {
  onSuccess: (credential: GoogleCredential) => void
  onError: () => void
}

export function GoogleCredentialButton({ onSuccess, onError, type, theme, size, text, shape, width }: Props) {
  const { clientId, scriptLoadedSuccessfully } = useGoogleOAuth()
  const container = useRef<HTMLDivElement>(null)
  const successRef = useRef(onSuccess)
  const errorRef = useRef(onError)
  const buttonOptions = useMemo(() => ({ type, theme, size, text, shape, width }), [type, theme, size, text, shape, width])

  useEffect(() => {
    successRef.current = onSuccess
    errorRef.current = onError
  }, [onSuccess, onError])

  useEffect(() => {
    if (!scriptLoadedSuccessfully || !container.current) return
    return mountGoogleCredentialButton(clientId, container.current, {
      onSuccess: (credential) => successRef.current(credential),
      onError: () => errorRef.current(),
    }, buttonOptions)
  }, [clientId, scriptLoadedSuccessfully, buttonOptions])

  return <div ref={container} className="google-slot-button" />
}
