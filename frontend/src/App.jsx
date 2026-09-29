import { useEffect, useState } from 'react'
import './App.css'
import { analyzeDomain, generateAiProbe } from './services/domainApi'

const dateLabel = (value) => {
  if (!value) return 'Not provided'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Not provided' : new Intl.DateTimeFormat('en', { month: 'short', day: '2-digit', year: 'numeric', timeZone: 'UTC' }).format(date)
}
const displayValue = (value) => value === null || value === undefined || value === '' || typeof value === 'object' ? 'Not provided' : String(value)
const headerValue = (value) => value === null || value === undefined || typeof value === 'object' || (typeof value === 'string' && !value.trim()) ? 'Not configured' : String(value)
const alignmentLabel = (value) => value === 'r' ? 'Relaxed' : value === 's' ? 'Strict' : displayValue(value)
const distinguishedNameValue = (value, key) => {
  if (typeof value !== 'string') return value
  const match = value.match(new RegExp(`(?:^|,)\\s*${key}=([^,]+)`, 'i'))
  return match ? match[1].trim() : value
}
const securityHeaderFields = [
  ['Content-Security-Policy', 'contentSecurityPolicy'],
  ['Strict-Transport-Security', 'strictTransportSecurity'],
  ['X-Content-Type-Options', 'xContentTypeOptions'],
  ['X-Frame-Options', 'xFrameOptions'],
  ['Referrer-Policy', 'referrerPolicy'],
  ['Permissions-Policy', 'permissionsPolicy'],
]
const validDomain = (value) => /^(?=.{1,253}$)(?:[a-z\d](?:[a-z\d-]{0,61}[a-z\d])?\.)+[a-z]{2,63}$/i.test(value)
const reportSections = [
  ['domain-overview', 'Domain overview'], ['dns-heading', 'DNS records'],
  ['technology-heading', 'Technology detection'], ['ip-heading', 'IP & ASN'],
  ['dnssec-heading', 'DNSSEC'], ['dmarc-heading', 'DMARC'],
  ['ssl-heading', 'SSL / HTTPS'], ['http-heading', 'HTTP / Website'],
]
const aiProbeSections = [
  ['domain', 'Domain & Registration', '↗'], ['dns', 'DNS', '⌁'],
  ['ssl', 'SSL & HTTPS', '◇'], ['http', 'HTTP & Website', '↗'],
  ['dnssec', 'DNSSEC', '◇'], ['dmarc', 'DMARC', '⌘'],
  ['ip', 'IP & Network', '⌁'], ['technology', 'Technology Detection', '⌘'],
]

function App() {
  const [domain, setDomain] = useState(new URLSearchParams(location.search).get('domain') || '')
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [copiedCards, setCopiedCards] = useState({})
  const [generating, setGenerating] = useState(false)
  const [aiProbe, setAiProbe] = useState(null)
  const [aiLoading, setAiLoading] = useState(false)
  const [aiError, setAiError] = useState('')
  const [aiOpen, setAiOpen] = useState(false)

  useEffect(() => {
    if (!aiOpen) return undefined
    const previousOverflow = document.body.style.overflow
    const closeOnEscape = (event) => { if (event.key === 'Escape') setAiOpen(false) }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [aiOpen])

  async function submit(event) {
    event?.preventDefault()
    const clean = domain.trim().replace(/^https?:\/\//i, '').replace(/^www\./i, '').split(/[/?#]/)[0]
    if (!clean) { setError('Enter a domain name to get started.'); return }
    if (!validDomain(clean)) { setError('Enter a valid domain, such as example.com.'); return }
    setDomain(clean)
    history.pushState({}, '', `/?domain=${encodeURIComponent(clean)}`)
    setLoading(true); setError(''); setReport(null); setAiProbe(null); setAiError(''); setAiOpen(false)
    try {
      const result = await analyzeDomain(clean)
      if (!result?.success || !result?.data?.rdap) throw new Error('unexpected')
      setReport(result.data)
    } catch {
      setError('Unable to analyze this domain. Please check the domain name and try again.')
    } finally { setLoading(false) }
  }

  async function requestAiProbe() {
    if (!report || aiLoading) return
    setAiOpen(true)
    setAiLoading(true)
    setAiError('')
    try {
      const result = await generateAiProbe(report)
      if (!result?.success || !result?.data) throw new Error('AI Probe response unavailable')
      setAiProbe(result.data)
    } catch {
      setAiError('AI Probe could not be generated. Please try again.')
    } finally {
      setAiLoading(false)
    }
  }

  function openAiProbe() {
    if (aiProbe) setAiOpen(true)
    else requestAiProbe()
  }

  async function copyCard(cardKey, title, values) {
    const lines = values.filter((value) => value !== undefined && value !== null && String(value).trim()).map(String)
    const text = `${title}\n\n${lines.length ? lines.join('\n') : 'No records found'}`
    try {
      await navigator.clipboard.writeText(text)
      setCopiedCards((previous) => ({ ...previous, [cardKey]: true }))
      setTimeout(() => setCopiedCards((previous) => ({ ...previous, [cardKey]: false })), 1600)
    } catch { setError('Could not copy to clipboard in this browser.') }
  }

  async function generatePdf() {
    setGenerating(true)
    setError('')
    try {
      await new Promise((resolve) => setTimeout(resolve, 50))
      const { generateDomainReportPdf } = await import('./utils/generateDomainReportPdf')
      generateDomainReportPdf(report)
    } catch {
      setError('Unable to generate the report PDF. Please try again.')
    } finally { setGenerating(false) }
  }

  const rdap = report?.rdap
  const available = rdap?.availability === 'AVAILABLE'
  const events = (rdap?.events || []).map((event) => {
    if (typeof event === 'string') {
      const [action, ...date] = event.split(/[:|]/)
      return { action: action.trim(), date: date.join(':').trim() }
    }
    return { action: event?.eventAction || event?.action, date: event?.eventDate || event?.date }
  }).filter((event) => event.action || event.date)
  const dns = report?.dns
  const ipRecords = Array.isArray(report?.ip?.records) ? report.ip.records.filter((record) => record && typeof record === 'object') : []
  const technologies = Array.isArray(report?.technology?.technologies) ? report.technology.technologies.filter((technology) => technology && typeof technology === 'object') : []
  const dnssec = report?.dnssec
  const dmarc = report?.dmarc
  const ssl = report?.ssl
  const certificate = ssl?.certificate
  const https = ssl?.https
  const http = report?.http
  const securityHeaders = http?.securityHeaders
  const cookies = Array.isArray(http?.cookies) ? http.cookies : []
  const redirectChain = Array.isArray(http?.redirectChain) ? http.redirectChain.filter((item) => typeof item === 'string' && item.trim()) : []
  const redirectLocation = typeof http?.redirectLocation === 'string' && http.redirectLocation.trim() ? http.redirectLocation : ''
  const websiteUrl = (() => {
    try {
      const url = new URL(http?.finalUrl)
      return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
    } catch { return '' }
  })()
  const dnsRecordTypes = [['A', 'a'], ['AAAA', 'aaaa'], ['CNAME', 'cname'], ['MX', 'mx'], ['Nameservers', 'ns'], ['TXT', 'txt'], ['CAA', 'caa'], ['SOA', 'soa']]
  const displayDnsRecord = (type, value) => {
    const text = String(value)
    if (type !== 'mx') return text
    const match = text.match(/^\s*(\d+)\s+(.+)$/)
    return match ? `Priority ${match[1]}\n${match[2]}` : text
  }
  const copyButton = (key, title, values) => <button type="button" className="copy-button" onClick={() => copyCard(key, title, values)}>{copiedCards[key] ? 'Copied!' : 'Copy'}</button>
  const sectionInfo = (title, description) => {
    const tooltipId = `section-info-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
    return <button type="button" className="section-info" aria-label={`About ${title}`} aria-describedby={tooltipId}><span className="section-info-glyph" aria-hidden="true">i</span><span className="section-info-tooltip" id={tooltipId} role="tooltip">{description}</span></button>
  }
  const actionInfo = (title, description) => {
    const tooltipId = `action-info-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
    return <button type="button" className="section-info action-info" aria-label={`About ${title}`} aria-describedby={tooltipId}><span className="section-info-glyph" aria-hidden="true">i</span><span className="section-info-tooltip" id={tooltipId} role="tooltip">{description}</span></button>
  }
  const overviewCopyValues = [
    `Domain: ${report?.domainName || rdap?.domainName || report?.input || ''}`,
    rdap?.availability && `Status: ${rdap.availability}`,
    rdap?.registrar && `Registrar: ${rdap.registrar}`,
    rdap?.registrarIanaId && `Registrar IANA ID: ${rdap.registrarIanaId}`,
    rdap?.registrarUrl && `Registrar URL: ${rdap.registrarUrl}`,
    rdap?.createdAt && `Created: ${dateLabel(rdap.createdAt)}`,
    rdap?.updatedAt && `Updated: ${dateLabel(rdap.updatedAt)}`,
    rdap?.expiresAt && `Expires: ${dateLabel(rdap.expiresAt)}`,
    rdap?.daysRemaining !== null && rdap?.daysRemaining !== undefined && `Days to expiry: ${rdap.daysRemaining} days`,
  ]
  const dnsCardValues = (key) => dns == null ? ['DNS information unavailable'] : Array.isArray(dns[key]) && dns[key].length ? dns[key].map((value) => displayDnsRecord(key, value).replace('\n', ' — ')) : ['No records found']
  const certificateCopyValues = [
    typeof certificate?.enabled === 'boolean' && `SSL Enabled: ${certificate.enabled ? 'Yes' : 'No'}`,
    typeof certificate?.valid === 'boolean' && `Certificate Valid: ${certificate.valid ? 'Yes' : 'No'}`,
    certificate?.issuer && `Issuer: ${distinguishedNameValue(certificate.issuer, 'O')}`,
    certificate?.subject && `Subject: ${distinguishedNameValue(certificate.subject, 'CN')}`,
    certificate?.validFrom && `Valid From: ${dateLabel(certificate.validFrom)}`,
    certificate?.expiresAt && `Expires At: ${dateLabel(certificate.expiresAt)}`,
    certificate?.daysRemaining !== null && certificate?.daysRemaining !== undefined && `Days Remaining: ${certificate.daysRemaining}`,
    typeof certificate?.tlsVersion === 'string' && `TLS Version: ${certificate.tlsVersion.replace(/^TLSv/, 'TLS ')}`,
  ]
  const httpsCopyValues = [
    https?.statusCode !== null && https?.statusCode !== undefined && `HTTPS Status: ${https.statusCode}`,
    typeof https?.hstsEnabled === 'boolean' && `HSTS: ${https.hstsEnabled ? 'Enabled' : 'Disabled'}`,
    typeof https?.httpRedirectsToHttps === 'boolean' && `HTTP to HTTPS Redirect: ${https.httpRedirectsToHttps ? 'Enabled' : 'Disabled'}`,
  ]
  const headerCopyValues = securityHeaderFields.map(([label, key]) => `${label}: ${headerValue(securityHeaders?.[key])}`)
  const cookieSummary = (cookie) => [
    cookie?.name && `Cookie Name: ${cookie.name}`,
    typeof cookie?.secure === 'boolean' && `Secure: ${cookie.secure ? 'Yes' : 'No'}`,
    typeof cookie?.httpOnly === 'boolean' && `HttpOnly: ${cookie.httpOnly ? 'Yes' : 'No'}`,
    cookie?.sameSite && `SameSite: ${cookie.sameSite}`,
    cookie?.domain && `Domain: ${cookie.domain}`,
    cookie?.path && `Path: ${cookie.path}`,
    cookie?.expires && `Expires: ${dateLabel(cookie.expires)}`,
  ].filter(Boolean)
  const resolveRedirect = (value) => {
    try { return new URL(value, http?.finalUrl || `https://${report?.domainName || report?.input}`).toString() } catch { return value }
  }
  const certificateFields = [
    ['SSL Enabled', typeof certificate?.enabled === 'boolean' ? certificate.enabled : null],
    ['Certificate Valid', typeof certificate?.valid === 'boolean' ? certificate.valid : null],
    ['Issuer', distinguishedNameValue(certificate?.issuer, 'O')], ['Subject', distinguishedNameValue(certificate?.subject, 'CN')],
    ['Valid From', certificate?.validFrom ? dateLabel(certificate.validFrom) : null],
    ['Expires At', certificate?.expiresAt ? dateLabel(certificate.expiresAt) : null],
    ['Days Remaining', certificate?.daysRemaining],
    ['TLS Version', typeof certificate?.tlsVersion === 'string' ? certificate.tlsVersion.replace(/^TLSv/, 'TLS ') : null],
  ]
  const httpResponseFields = [
    ['Status Code', http?.statusCode], ['Final URL', http?.finalUrl],
    ['Response Time', http?.responseTimeMs !== null && http?.responseTimeMs !== undefined ? `${http.responseTimeMs} ms` : null],
    ['Server', http?.server], ['Content Type', http?.contentType],
    ['Content Length', http?.contentLength !== null && http?.contentLength !== undefined ? `${http.contentLength} bytes` : null],
    ['Content Encoding', http?.contentEncoding],
  ]
  const dsRecords = Array.isArray(dnssec?.dsRecords) ? dnssec.dsRecords : []
  const dnskeyRecords = Array.isArray(dnssec?.dnskeyRecords) ? dnssec.dnskeyRecords : []
  const aggregateReports = Array.isArray(dmarc?.aggregateReports) ? dmarc.aggregateReports.filter((address) => typeof address === 'string' && address.trim()) : []
  const forensicReports = Array.isArray(dmarc?.forensicReports) ? dmarc.forensicReports.filter((address) => typeof address === 'string' && address.trim()) : []
  const dmarcCopyValues = [
    `Status: ${dmarc?.present === true ? 'Configured' : 'Not Configured'}`,
    dmarc?.policy && `Policy: ${dmarc.policy}`,
    dmarc?.subdomainPolicy && `Subdomain Policy: ${dmarc.subdomainPolicy}`,
    dmarc?.percentage !== null && dmarc?.percentage !== undefined && `Percentage: ${dmarc.percentage}%`,
    dmarc?.dkimAlignment && `DKIM Alignment: ${alignmentLabel(dmarc.dkimAlignment)}`,
    dmarc?.spfAlignment && `SPF Alignment: ${alignmentLabel(dmarc.spfAlignment)}`,
    dmarc?.failureOptions && `Failure Options: ${dmarc.failureOptions}`,
  ]

  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="/" onClick={(e) => { e.preventDefault(); setReport(null); setError(''); setDomain(''); history.pushState({}, '', '/') }}><span className="brand-mark">D</span><span>domain<span className="brand-light">probe</span></span></a><div className="top-meta"><span className="live-dot"/> RDAP intelligence <span className="top-divider">·</span> v1.0</div></header>
    <main>
      {!report && <section className="hero">
        <div className="eyebrow"><span className="eyebrow-line"/> DOMAIN INTELLIGENCE <span className="eyebrow-line"/></div>
        <h1>Know what’s behind<br/><span>every domain.</span></h1>
        <p className="hero-copy">Registration data, registrar details and DNS signals.<br className="desktop-break"/> Everything you need to make an informed call.</p>
        <form className="search-form" onSubmit={submit}>
          <span className="search-icon">⌕</span><input aria-label="Domain name" value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="Enter a domain name..." autoComplete="url"/>
          <button type="submit" disabled={loading}>{loading ? <><span className="button-spinner"/> Analyzing</> : <>Analyze <span>↗</span></>}</button>
        </form>
        {error && <p className="error-message" role="alert">{error}</p>}
        {loading && <div className="loading-note"><span className="pulse-dot"/> Analyzing domain report</div>}
        {!loading && !error && <p className="search-hint"><kbd>↵</kbd> Press enter to analyze <span>·</span> Powered by RDAP</p>}
        <div className="feature-row" role="region" aria-label="Domain analysis features"><div className="feature-track"><div className="feature-marquee-group"><span><i>◈</i> Registration data</span><span><i>◎</i> Registrar lookup</span><span><i>⌁</i> DNS records</span><span><i>⌘</i> Nameservers</span><span><i>✓</i> DNSSEC status</span><span><i>@</i> DMARC protection</span><span><i>◇</i> SSL / HTTPS</span><span><i>↗</i> HTTP security</span><span><i>◉</i> IP &amp; ASN</span><span><i>⌘</i> Technology detection</span><span><i>✦</i> AI Probe insights</span></div><div className="feature-marquee-group" aria-hidden="true"><span><i>◈</i> Registration data</span><span><i>◎</i> Registrar lookup</span><span><i>⌁</i> DNS records</span><span><i>⌘</i> Nameservers</span><span><i>✓</i> DNSSEC status</span><span><i>@</i> DMARC protection</span><span><i>◇</i> SSL / HTTPS</span><span><i>↗</i> HTTP security</span><span><i>◉</i> IP &amp; ASN</span><span><i>⌘</i> Technology detection</span><span><i>✦</i> AI Probe insights</span></div></div></div>
      </section>}

      {report && <section className="report-wrap">
        <div className="report-toolbar"><button className="back-link" onClick={() => { setReport(null); setDomain(''); setError(''); setAiOpen(false); history.pushState({}, '', '/') }}>← <span>New analysis</span></button><div className="report-actions"><div className="action-with-info"><button type="button" className="generate-button" onClick={openAiProbe} disabled={aiLoading}>{aiLoading ? <><span className="button-spinner"/> Analyzing...</> : `${String.fromCodePoint(0x2728)} ${aiProbe ? 'AI Probe' : aiError ? 'Try Again' : 'Generate AI Probe'}`}</button>{actionInfo('AI Probe', 'Opens an AI-generated summary of this domain report, with key findings and recommendations.')}</div><div className="action-with-info"><button type="button" className="generate-button" onClick={generatePdf} disabled={generating}>{generating ? 'Generating...' : 'Generate Report'} <span>↓</span></button>{actionInfo('Generate Report', 'Downloads a PDF copy of the domain report so you can save or share the findings.')}</div></div></div>
        <nav className="report-nav" aria-label="Jump to report section"><span className="report-nav-title">JUMP TO</span><ul>{reportSections.map(([id, label], index) => <li key={id}><a href={`#${id}`}><span>{String(index + 1).padStart(2, '0')}</span>{label}</a></li>)}</ul></nav>
        {error && <p className="error-message report-error" role="alert">{error}</p>}
        {available ? <>
          <div className="report-heading"><div><h1>{report.domainName || rdap.domainName || report.input}</h1><div className="report-kicker">DOMAIN REPORT <span>·</span> {dateLabel(report.analyzedAt)}</div></div><span className="availability-pill available">AVAILABLE</span></div>
          <div className="available-panel"><div className="available-check">✓</div><div className="availability-pill available-pill">AVAILABLE</div><p>This domain is not registered according to the RDAP response.</p><button onClick={() => { setReport(null); setDomain(''); history.pushState({}, '', '/') }}>Analyze another domain <span>↗</span></button></div>
          <h2 className="report-section-title" id="domain-overview">01 <span>Domain overview</span>{sectionInfo('Domain overview', 'This section summarizes the domain’s registration status and any registration details returned by the registry.')}{copyButton('overview', 'Domain Overview', overviewCopyValues)}</h2>
          <div className="metrics-grid">{[['Registered', rdap.createdAt], ['Last updated', rdap.updatedAt], ['Expires', rdap.expiresAt]].map(([label, value]) => <article className="metric-card" key={label}><div className="metric-copy-heading"><span className="metric-label">{label}</span>{copyButton(`date-${label}`, label, [value ? dateLabel(value) : 'No records found'])}</div><strong className={!value ? 'overview-no-records' : ''}>{value ? dateLabel(value) : 'No records found'}</strong></article>)}<article className="metric-card"><div className="metric-copy-heading"><span className="metric-label">Days to expiry</span>{copyButton('days-to-expiry', 'Days to expiry', [rdap.daysRemaining !== null && rdap.daysRemaining !== undefined ? `${rdap.daysRemaining} days` : 'No records found'])}</div><strong className={rdap.daysRemaining === null || rdap.daysRemaining === undefined ? 'overview-no-records' : ''}>{rdap.daysRemaining !== null && rdap.daysRemaining !== undefined ? `${rdap.daysRemaining} days` : 'No records found'}</strong></article></div>
          <div className="detail-grid">
            <article className="panel registrar-panel"><div className="panel-heading"><span className="panel-icon">↗</span><div><span className="panel-overline">REGISTRATION</span><h2>Registrar</h2></div>{copyButton('registrar', 'Registrar', [rdap.registrar || 'No records found', rdap.registrarIanaId && `IANA Registrar ID: ${rdap.registrarIanaId}`, rdap.registrarUrl && `Website: ${rdap.registrarUrl}`])}</div><div className="detail-row"><span>Registrar name</span><strong className={!rdap.registrar ? 'overview-no-records' : ''}>{rdap.registrar || 'No records found'}</strong></div><div className="detail-row"><span>IANA registrar ID</span><strong className={`mono ${!rdap.registrarIanaId ? 'overview-no-records' : ''}`}>{rdap.registrarIanaId || 'No records found'}</strong></div><div className="detail-row"><span>Website</span>{rdap.registrarUrl ? <a href={rdap.registrarUrl} target="_blank" rel="noreferrer">{rdap.registrarUrl.replace(/^https?:\/\//, '').replace(/\/$/, '')} ↗</a> : <strong className="overview-no-records">No records found</strong>}</div></article>
            <article className="panel status-panel"><div className="panel-heading"><span className="panel-icon">≡</span><div><span className="panel-overline">REGISTRY FLAGS</span><h2>Domain status</h2></div>{copyButton('statuses', 'Registry Flags', rdap.statuses?.length ? rdap.statuses : ['No records found'])}</div>{rdap.statuses?.length ? <ul className="status-list">{rdap.statuses.map((status) => <li key={status}><span/> {status.replaceAll('-', ' ')}</li>)}</ul> : <p className="empty-note overview-no-records">No records found</p>}</article>
          </div>
        </> : <>
          <div className="report-heading"><div><h1>{report.domainName || rdap.domainName || report.input}</h1><div className="report-kicker">DOMAIN REPORT <span>·</span> {dateLabel(report.analyzedAt)}</div><p className="registrar-summary">{rdap.registrar || 'Registrar not provided'}{rdap.registrarIanaId && <span> IANA ID {rdap.registrarIanaId}</span>}</p></div><span className={`availability-pill ${rdap.availability?.toLowerCase() || 'unknown'}`}>{rdap.availability || 'STATUS UNKNOWN'}</span></div>
          <h2 className="report-section-title" id="domain-overview">01 <span>Domain overview</span>{sectionInfo('Domain overview', 'This section summarizes the domain’s registration status, registrar, key dates, and how long remains before expiry.')}{copyButton('overview', 'Domain Overview', overviewCopyValues)}</h2>
          <div className="metrics-grid">{[['Registered', rdap.createdAt], ['Last updated', rdap.updatedAt], ['Expires', rdap.expiresAt]].map(([label, value]) => <article className="metric-card" key={label}><div className="metric-copy-heading"><span className="metric-label">{label}</span>{copyButton(`date-${label}`, label, [value ? dateLabel(value) : 'Not provided'])}</div><strong>{value ? dateLabel(value) : 'Not provided'}</strong></article>)}<article className="metric-card"><div className="metric-copy-heading"><span className="metric-label">Days to expiry</span>{copyButton('days-to-expiry', 'Days to expiry', [rdap.daysRemaining !== null && rdap.daysRemaining !== undefined ? `${rdap.daysRemaining} days` : 'Not provided'])}</div><strong>{rdap.daysRemaining !== null && rdap.daysRemaining !== undefined ? `${rdap.daysRemaining} days` : 'Not provided'}</strong></article></div>
          <div className="detail-grid">
            <article className="panel registrar-panel"><div className="panel-heading"><span className="panel-icon">↗</span><div><span className="panel-overline">REGISTRATION</span><h2>Registrar</h2></div>{copyButton('registrar', 'Registrar', [rdap.registrar && `Registrar: ${rdap.registrar}`, rdap.registrarIanaId && `IANA Registrar ID: ${rdap.registrarIanaId}`, rdap.registrarUrl && `Website: ${rdap.registrarUrl}`])}</div><div className="detail-row"><span>Registrar name</span><strong>{rdap.registrar || 'Not provided'}</strong></div>{rdap.registrarIanaId && <div className="detail-row"><span>IANA registrar ID</span><strong className="mono">{rdap.registrarIanaId}</strong></div>}{rdap.registrarUrl && <div className="detail-row"><span>Website</span><a href={rdap.registrarUrl} target="_blank" rel="noreferrer">{rdap.registrarUrl.replace(/^https?:\/\//, '').replace(/\/$/, '')} ↗</a></div>}</article>
            <article className="panel status-panel"><div className="panel-heading"><span className="panel-icon">≡</span><div><span className="panel-overline">REGISTRY FLAGS</span><h2>Domain status</h2></div>{copyButton('statuses', 'Registry Flags', rdap.statuses?.length ? rdap.statuses : ['No records found'])}</div>{rdap.statuses?.length ? <ul className="status-list">{rdap.statuses.map((status) => <li key={status}><span/> {status.replaceAll('-', ' ')}</li>)}</ul> : <p className="empty-note">No status information provided.</p>}</article>
            <article className="panel timeline-panel"><div className="panel-heading"><span className="panel-icon">◷</span><div><span className="panel-overline">LIFECYCLE</span><h2>Domain timeline</h2></div>{copyButton('timeline', 'Domain Timeline', events.length ? events.map((event) => `${event.action}: ${dateLabel(event.date)}`) : [rdap.createdAt && `Registration: ${dateLabel(rdap.createdAt)}`, rdap.updatedAt && `Last changed: ${dateLabel(rdap.updatedAt)}`, rdap.expiresAt && `Expiration: ${dateLabel(rdap.expiresAt)}`])}</div>{events.length ? <div className="timeline">{events.map((event, index) => <div className="timeline-item" key={`${event.action}-${index}`}><span className="timeline-point"/><div><strong>{event.action || 'Domain event'}</strong><span>{dateLabel(event.date)}</span></div></div>)}</div> : <div className="timeline">{[['Registration', rdap.createdAt], ['Last changed', rdap.updatedAt], ['Expiration', rdap.expiresAt]].filter(([, date]) => date).map(([label, date]) => <div className="timeline-item" key={label}><span className="timeline-point"/><div><strong>{label}</strong><span>{dateLabel(date)}</span></div></div>)}</div>}</article>
          </div>
        </>}
        <section className="dns-section" aria-labelledby="dns-heading">
            <div className="dns-section-heading"><h2 id="dns-heading" className="report-section-title">02 <span>DNS records</span>{sectionInfo('DNS records', 'These public records tell browsers, mail servers, and other services where to find this domain and how to connect to it.')}</h2><span className="dns-section-note">PUBLIC DNS LOOKUP</span></div>
            {dns == null ? <div className="panel dns-unavailable"><span>DNS information unavailable</span>{copyButton('dns-unavailable', 'DNS Records', ['DNS information unavailable'])}</div> : <div className="dns-grid">{dnsRecordTypes.map(([label, key]) => {
              const records = Array.isArray(dns[key]) ? dns[key] : []
              return <article className={`panel dns-card dns-${key}`} key={key}>
                <div className="dns-card-heading"><div className="dns-title-group"><h3>{label}</h3><span className="count">{records.length}</span></div>{copyButton(`dns-${key}`, key === 'ns' ? 'Nameservers' : `${label} Records`, dnsCardValues(key))}</div>
                {records.length ? <ul className="dns-record-list">{records.map((record, index) => <li key={`${key}-${index}`}><code>{displayDnsRecord(key, record)}</code></li>)}</ul> : <p className="empty-note">No records found</p>}
              </article>
            })}</div>}
        </section>
        <section className="analysis-section" aria-labelledby="technology-heading">
          <h2 id="technology-heading" className="report-section-title">03 <span>Technology Detection</span>{sectionInfo('Technology detection', 'This section lists software and services inferred from public website and network signals; detection may not identify every technology in use.')}</h2>
          {technologies.length === 0 ? <div className="panel section-unavailable">No technologies detected</div> : <div className="analysis-grid">{technologies.map((technology, index) => {
            const evidence = Array.isArray(technology.evidence) ? technology.evidence.filter((item) => typeof item === 'string' && item.trim()) : []
            const name = displayValue(technology.name)
            const category = displayValue(technology.category)
            const confidence = displayValue(technology.confidence)
            const copyValues = [
              `Technology: ${name}`, `Category: ${category}`, `Confidence: ${confidence}`,
              ...(evidence.length ? evidence.map((item) => `Evidence: ${item}`) : ['Evidence: None provided']),
            ]
            return <article className="panel analysis-card" key={`${technology.name || 'technology'}-${index}`}>
              <div className="panel-heading"><span className="panel-icon">⌘</span><div className="technology-heading-copy"><span className="panel-overline">{category}</span><h2 className="technology-name">{name}</h2></div><span className="count">{confidence}</span>{copyButton(`technology-${index}`, 'Technology', copyValues)}</div>
              <div className="technology-evidence"><span className="panel-overline">EVIDENCE</span>{evidence.length ? <ul className="analysis-list">{evidence.map((item, evidenceIndex) => <li key={`${index}-evidence-${evidenceIndex}`}>{item}</li>)}</ul> : <p className="empty-note">No evidence provided</p>}</div>
            </article>
          })}</div>}
        </section>
        <section className="analysis-section" aria-labelledby="ip-heading">
          <h2 id="ip-heading" className="report-section-title">04 <span>IP &amp; ASN</span>{sectionInfo('IP & ASN', 'These are the IP addresses resolved for the domain, with the network operator and geographic details reported for each address.')}</h2>
          <p className="ip-section-note">RESOLVED DOMAIN ADDRESSES AND NETWORK INFORMATION</p>
          {report?.ip == null || !Array.isArray(report.ip.records) ? <div className="panel section-unavailable">Resolved IP information unavailable</div> : ipRecords.length === 0 ? <div className="panel section-unavailable">No resolved IP records found</div> : <div className="analysis-grid">{ipRecords.map((record, index) => {
            const valueOrDash = (value) => displayValue(value) === 'Not provided' ? '—' : displayValue(value)
            const locationValue = (name, code) => {
              const locationName = displayValue(name) === 'Not provided' ? '' : String(name)
              const locationCode = displayValue(code) === 'Not provided' ? '' : String(code)
              return locationName && locationCode ? `${locationName} (${locationCode})` : locationName || locationCode || '—'
            }
            const copyValues = [
              `IP Address: ${valueOrDash(record.ip)}`, `ASN: ${valueOrDash(record.asn)}`,
              `AS Name: ${valueOrDash(record.asName)}`, `AS Domain: ${valueOrDash(record.asDomain)}`,
              `Country: ${locationValue(record.country, record.countryCode)}`,
              `Continent: ${locationValue(record.continent, record.continentCode)}`,
            ]
            return <article className="panel analysis-card" key={`${record.ip || 'ip-record'}-${index}`}>
              <div className="panel-heading"><span className="panel-icon">⌁</span><div><span className="panel-overline">RESOLVED IP / NETWORK</span><h2 className="ip-address">{valueOrDash(record.ip)}</h2></div>{copyButton(`ip-record-${index}`, 'IP & ASN', copyValues)}</div>
              <div className="analysis-rows">
                <div className="analysis-row"><span>ASN</span><strong>{valueOrDash(record.asn)}</strong></div>
                <div className="analysis-row"><span>AS Name</span><strong>{valueOrDash(record.asName)}</strong></div>
                <div className="analysis-row"><span>AS Domain</span><strong>{valueOrDash(record.asDomain)}</strong></div>
                <div className="analysis-row"><span>Country</span><strong>{locationValue(record.country, record.countryCode)}</strong></div>
                <div className="analysis-row"><span>Continent</span><strong>{locationValue(record.continent, record.continentCode)}</strong></div>
              </div>
            </article>
          })}</div>}
        </section>
        <section className="analysis-section" aria-labelledby="dnssec-heading">
          <h2 id="dnssec-heading" className="report-section-title">05 <span>DNSSEC</span>{sectionInfo('DNSSEC', 'DNSSEC adds cryptographic signatures to DNS records so resolvers can check that the records are authentic and unchanged.')}</h2>
          {dnssec == null ? <div className="panel section-unavailable">DNSSEC information unavailable</div> : <div className="analysis-grid">
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">◇</span><div><span className="panel-overline">DNS INTEGRITY</span><h2>DNSSEC Status</h2></div>{copyButton('dnssec-status', 'DNSSEC', [`Status: ${dnssec.signed === true ? 'Signed' : 'Not Signed'}`, `Validation: ${dnssec.validated === true ? 'Validated' : 'Not Validated'}`])}</div>
              <div className="analysis-rows">
                <div className="analysis-row"><span>Status</span><strong className={`inline-status ${dnssec.signed === true ? 'status-positive' : 'status-negative'}`}>{dnssec.signed === true ? '✓ Signed' : '✕ Not Signed'}</strong></div>
                <div className="analysis-row"><span>Validation</span><strong className={`inline-status ${dnssec.validated === true ? 'status-positive' : 'status-negative'}`}>{dnssec.validated === true ? '✓ Validated' : '✕ Not Validated'}</strong></div>
              </div>
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">⌘</span><div><span className="panel-overline">DELEGATION SIGNER</span><h2>DS Records</h2></div>{copyButton('dnssec-ds', 'DS Records', dsRecords.length ? dsRecords.flatMap((record, index) => [`Record ${index + 1}`, `Key Tag: ${displayValue(record?.keyTag)}`, `Algorithm: ${displayValue(record?.algorithm)}`, `Digest Type: ${displayValue(record?.digestType)}`, `Digest: ${displayValue(record?.digest)}`]) : ['No records found'])}</div>
              {dsRecords.length ? <div className="dnssec-record-list">{dsRecords.map((record, index) => <div className="dnssec-record" key={`ds-${index}`}><span className="dnssec-record-label">Record {index + 1}</span>{[['Key Tag', record?.keyTag], ['Algorithm', record?.algorithm], ['Digest Type', record?.digestType], ['Digest', record?.digest]].map(([label, value]) => <div className="analysis-row" key={label}><span>{label}</span><strong className="break-value">{displayValue(value)}</strong></div>)}</div>)}</div> : <p className="empty-note">No records found</p>}
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">⌘</span><div><span className="panel-overline">ZONE KEYS</span><h2>DNSKEY Records</h2></div>{copyButton('dnssec-dnskey', 'DNSKEY Records', dnskeyRecords.length ? dnskeyRecords.flatMap((record, index) => [`Record ${index + 1}`, `Flags: ${displayValue(record?.flags)}`, `Protocol: ${displayValue(record?.protocol)}`, `Algorithm: ${displayValue(record?.algorithm)}`, `Public Key: ${displayValue(record?.publicKey)}`]) : ['No records found'])}</div>
              {dnskeyRecords.length ? <div className="dnssec-record-list">{dnskeyRecords.map((record, index) => <div className="dnssec-record" key={`dnskey-${index}`}><span className="dnssec-record-label">Record {index + 1}</span>{[['Flags', record?.flags], ['Protocol', record?.protocol], ['Algorithm', record?.algorithm], ['Public Key', record?.publicKey]].map(([label, value]) => <div className="analysis-row" key={label}><span>{label}</span><strong className="break-value">{displayValue(value)}</strong></div>)}</div>)}</div> : <p className="empty-note">No records found</p>}
            </article>
          </div>}
        </section>
        <section className="analysis-section" aria-labelledby="dmarc-heading">
          <h2 id="dmarc-heading" className="report-section-title">06 <span>DMARC</span>{sectionInfo('DMARC', 'DMARC tells receiving mail systems what to do with messages that fail SPF or DKIM checks and where reports should be sent.')}</h2>
          {dmarc == null ? <div className="panel section-unavailable">DMARC information unavailable</div> : <div className="analysis-grid">
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">◇</span><div><span className="panel-overline">EMAIL AUTHENTICATION</span><h2>DMARC Policy</h2></div>{copyButton('dmarc-policy', 'DMARC', dmarcCopyValues)}</div>
              <div className="analysis-rows">
                <div className="analysis-row"><span>Status</span><strong className={`inline-status ${dmarc.present === true ? 'status-positive' : 'status-negative'}`}>{dmarc.present === true ? '✓ Configured' : '✕ Not Configured'}</strong></div>
                <div className="analysis-row"><span>Policy</span><strong>{dmarc.policy ? dmarc.policy.toUpperCase() : 'Not provided'}</strong></div>
                <div className="analysis-row"><span>Subdomain Policy</span><strong>{dmarc.subdomainPolicy ? dmarc.subdomainPolicy.toUpperCase() : 'Not provided'}</strong></div>
                <div className="analysis-row"><span>Percentage</span><strong>{dmarc.percentage !== null && dmarc.percentage !== undefined ? `${dmarc.percentage}%` : 'Not provided'}</strong></div>
                <div className="analysis-row"><span>DKIM Alignment</span><strong>{alignmentLabel(dmarc.dkimAlignment)}</strong></div>
                <div className="analysis-row"><span>SPF Alignment</span><strong>{alignmentLabel(dmarc.spfAlignment)}</strong></div>
                <div className="analysis-row"><span>Failure Options</span><strong>{dmarc.failureOptions == null || dmarc.failureOptions === '' ? '—' : displayValue(dmarc.failureOptions)}</strong></div>
              </div>
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">⌘</span><div><span className="panel-overline">REPORT DESTINATIONS</span><h2>Aggregate Reports</h2></div>{copyButton('dmarc-aggregate', 'Aggregate Reports', aggregateReports.length ? aggregateReports : ['None'])}</div>
              {aggregateReports.length ? <ul className="analysis-list">{aggregateReports.map((address, index) => <li className="break-value" key={`rua-${index}`}>{address}</li>)}</ul> : <p className="empty-note">None</p>}
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">⌘</span><div><span className="panel-overline">FAILURE REPORTING</span><h2>Forensic Reports</h2></div>{copyButton('dmarc-forensic', 'Forensic Reports', forensicReports.length ? forensicReports : ['None'])}</div>
              {forensicReports.length ? <ul className="analysis-list">{forensicReports.map((address, index) => <li className="break-value" key={`ruf-${index}`}>{address}</li>)}</ul> : <p className="empty-note">None</p>}
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">≡</span><div><span className="panel-overline">DNS POLICY RECORD</span><h2>Full DMARC Record</h2></div>{copyButton('dmarc-record', 'DMARC Record', [dmarc.record || 'Not configured'])}</div>
              <p className="dmarc-record-value">{typeof dmarc.record === 'string' && dmarc.record.trim() ? dmarc.record : 'Not configured'}</p>
            </article>
          </div>}
        </section>
        <section className="analysis-section" aria-labelledby="ssl-heading">
          <h2 id="ssl-heading" className="report-section-title">07 <span>SSL / HTTPS</span>{sectionInfo('SSL / HTTPS', 'This section shows whether the site has a valid TLS certificate and how its HTTPS connection and redirects are configured.')}</h2>
          {ssl == null ? <div className="panel section-unavailable">SSL information unavailable</div> : <div className="analysis-grid">
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">◇</span><div><span className="panel-overline">CERTIFICATE</span><h2>SSL Certificate</h2></div>{copyButton('ssl-certificate', 'SSL Certificate', certificateCopyValues.some(Boolean) ? certificateCopyValues : ['SSL certificate information unavailable'])}</div>
              {certificate ? <div className="analysis-rows">{certificateFields.map(([label, value]) => <div className="analysis-row" key={label}><span>{label}</span>{typeof value === 'boolean' ? <strong className={`inline-status ${value ? 'status-positive' : 'status-negative'}`}>{label === 'Certificate Valid' ? (value ? '✓ Valid' : '✕ Invalid') : (value ? '✓ Enabled' : '✕ Disabled')}</strong> : <strong>{displayValue(value)}</strong>}</div>)}</div> : <p className="empty-note">SSL certificate information unavailable</p>}
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">⌘</span><div><span className="panel-overline">CERTIFICATE COVERAGE</span><h2>Covered Domains</h2></div>{copyButton('ssl-domains', 'Covered Domains', Array.isArray(certificate?.domains) && certificate.domains.length ? certificate.domains : ['No domains provided'])}</div>
              {Array.isArray(certificate?.domains) && certificate.domains.length ? <ul className="analysis-list">{certificate.domains.map((name, index) => <li key={`${name}-${index}`}>{name}</li>)}</ul> : <p className="empty-note">No domains provided</p>}
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">↗</span><div><span className="panel-overline">SECURE CONNECTION</span><h2>HTTPS</h2></div>{copyButton('https', 'HTTPS', httpsCopyValues.some(Boolean) ? httpsCopyValues : ['HTTPS information unavailable'])}</div>
              {https ? <div className="analysis-rows">
                <div className="analysis-row"><span>HTTPS Status</span><strong>{displayValue(https.statusCode)}</strong></div>
                <div className="analysis-row"><span>HSTS</span>{typeof https.hstsEnabled === 'boolean' ? <strong className={`inline-status ${https.hstsEnabled ? 'status-positive' : 'status-negative'}`}>{https.hstsEnabled ? '✓ Enabled' : '✕ Disabled'}</strong> : <strong>Not provided</strong>}</div>
                <div className="analysis-row"><span>HTTP → HTTPS</span>{typeof https.httpRedirectsToHttps === 'boolean' ? <strong className={`inline-status ${https.httpRedirectsToHttps ? 'status-positive' : 'status-negative'}`}>{https.httpRedirectsToHttps ? '✓ Enabled' : '✕ Disabled'}</strong> : <strong>Not provided</strong>}</div>
              </div> : <p className="empty-note">HTTPS information unavailable</p>}
            </article>
          </div>}
        </section>
        <section className="analysis-section" aria-labelledby="http-heading">
          <h2 id="http-heading" className="report-section-title">08 <span>HTTP / Website</span>{sectionInfo('HTTP / Website', 'These details describe the website’s response to a web request, including redirects, security headers, and cookies it returned.')}</h2>
          {http == null ? <div className="panel section-unavailable">HTTP information unavailable</div> : <div className="analysis-grid">
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">↗</span><div><span className="panel-overline">RESPONSE</span><h2>HTTP Response</h2></div>{copyButton('http-response', 'HTTP Response', httpResponseFields.map(([label, value]) => `${label}: ${displayValue(value)}`))}</div>
              <div className="analysis-rows">{httpResponseFields.map(([label, value]) => <div className="analysis-row" key={label}><span>{label}</span><strong className={label === 'Final URL' ? 'break-value' : ''}>{label === 'Final URL' && websiteUrl ? <a className="website-link" href={websiteUrl} target="_blank" rel="noreferrer">{displayValue(value)} ↗</a> : displayValue(value)}</strong></div>)}</div>
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">⇄</span><div><span className="panel-overline">NAVIGATION</span><h2>Redirects</h2></div>{copyButton('http-redirects', 'Redirects', [redirectLocation && `Redirect Location: ${redirectLocation}`, ...(redirectChain.length ? redirectChain.map((item, index) => `${index + 1}. ${resolveRedirect(item)}`) : !redirectLocation ? ['No redirects'] : [])])}</div>
              {redirectLocation && <div className="analysis-row"><span>Redirect Location</span><strong className="break-value">{redirectLocation}</strong></div>}
              {redirectChain.length ? <ol className="analysis-list redirect-list">{redirectChain.map((item, index) => <li key={`${item}-${index}`}><span>{index + 1}.</span> {resolveRedirect(item)}</li>)}</ol> : !redirectLocation && <p className="empty-note">No redirects</p>}
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">≡</span><div><span className="panel-overline">POLICY</span><h2>Security Headers</h2></div>{copyButton('security-headers', 'Security Headers', headerCopyValues)}</div>
              <div className="analysis-rows">{securityHeaderFields.map(([label, key]) => <div className="analysis-row" key={key}><span>{label}</span><strong className="break-value">{headerValue(securityHeaders?.[key])}</strong></div>)}</div>
            </article>
            <article className="panel analysis-card"><div className="panel-heading"><span className="panel-icon">⌘</span><div><span className="panel-overline">BROWSER STORAGE</span><h2>Cookies</h2></div>{copyButton('http-cookies', 'Cookies', cookies.length ? cookies.flatMap((cookie, index) => [`Cookie ${index + 1}`, ...cookieSummary(cookie)]) : ['No cookies detected'])}</div>
              {cookies.length ? <div className="cookie-list">{cookies.map((cookie, index) => <div className="cookie-entry" key={`${cookie?.name || 'cookie'}-${index}`}><div className="cookie-name">{cookie?.name || `Cookie ${index + 1}`}</div><div className="analysis-rows">{cookieSummary(cookie).filter((value) => !value.startsWith('Cookie Name: ')).map((value) => { const [label, ...rest] = value.split(': '); return <div className="analysis-row" key={label}><span>{label}</span><strong className="break-value">{rest.join(': ')}</strong></div> })}</div></div>)}</div> : <p className="empty-note">No cookies detected</p>}
            </article>
          </div>}
        </section>
      </section>}
      {report && aiOpen && <div className="ai-overlay" onClick={(event) => { if (event.target === event.currentTarget) setAiOpen(false) }}>
        <section className="ai-dialog" role="dialog" aria-modal="true" aria-labelledby="ai-dialog-title">
          <header className="ai-dialog-header"><div className="ai-dialog-title"><span className="panel-icon">{String.fromCodePoint(0x2728)}</span><div><span className="panel-overline">AI DOMAIN PROBE</span><h1 id="ai-dialog-title">AI Probe</h1></div></div><div className="ai-dialog-actions">{aiProbe && <span className={`ai-status-pill ai-status-${String(aiProbe.status || '').toLowerCase()}`}>{displayValue(aiProbe.status)}</span>}<button className="ai-close-button" type="button" aria-label="Close AI Probe" onClick={() => setAiOpen(false)}>×</button></div></header>
          {aiLoading ? <div className="ai-dialog-state"><span className="button-spinner"/><p>Analyzing your domain configuration...</p></div> : aiError ? <div className="ai-dialog-state"><p className="ai-error-message">AI Probe could not be generated. Please try again.</p><button className="generate-button" type="button" onClick={requestAiProbe}>Try Again</button></div> : aiProbe && <>
            <article className="panel ai-summary-card"><span className="panel-overline">OVERALL SUMMARY</span><p>{displayValue(aiProbe.summary)}</p></article>
            <div className="analysis-grid ai-analysis-grid">{aiProbeSections.map(([key, title, icon]) => {
              const section = aiProbe[key] || {}
              return <article className="panel analysis-card ai-analysis-card" key={key}>
                <div className="panel-heading"><span className="panel-icon">{icon}</span><div><span className="panel-overline">DOMAIN ANALYSIS</span><h2>{title}</h2></div></div>
                <div className="ai-insight ai-insight-positive"><span><b>✓</b> Positive</span><p>{displayValue(section.positive)}</p></div>
                <div className="ai-insight ai-insight-negative"><span><b>!</b> Negative</span><p>{displayValue(section.negative)}</p></div>
                <div className="ai-insight ai-insight-recommendation"><span><b>→</b> Recommendation</span><p>{displayValue(section.recommendation)}</p></div>
              </article>
            })}</div>
            <article className="panel ai-recommendation-card"><span className="panel-overline">OVERALL RECOMMENDATION</span><p>{displayValue(aiProbe.recommendation)}</p></article>
          </>}
        </section>
      </div>}
    </main>
    <footer><span>DOMAINPROBE <span className="footer-muted">· DOMAIN INTELLIGENCE, SIMPLIFIED</span></span><span>Public registration data <i>↗</i></span></footer>
  </div>
}

export default App
