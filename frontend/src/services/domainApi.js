const API_URL = 'http://localhost:8080/api/v1/domains/analyze'
const AI_PROBE_URL = 'http://localhost:8080/api/v1/ai/probe'

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
