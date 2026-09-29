const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const API_URL = `${API_BASE_URL}/api/v1/domains/analyze`
const AI_PROBE_URL = `${API_BASE_URL}/api/v1/ai/probe`

export async function analyzeDomain(domainName) {
  const response = await fetch(`${API_URL}?domainName=${encodeURIComponent(domainName)}`)
  if (!response.ok) throw new Error('Domain analysis failed')
  return response.json()
}

export async function generateAiProbe(report) {
  const response = await fetch(AI_PROBE_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(report),
  })
  if (!response.ok) throw new Error('AI Probe request failed')
  return response.json()
}