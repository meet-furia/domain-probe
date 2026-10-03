import { jsPDF } from 'jspdf'

const recordTypes = [['A Records', 'a'], ['AAAA Records', 'aaaa'], ['CNAME Records', 'cname'], ['MX Records', 'mx'], ['NS Records', 'ns'], ['TXT Records', 'txt'], ['CAA Records', 'caa'], ['SOA Records', 'soa']]
const dateText = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeZone: 'UTC' }).format(date)
}
const hasValue = (value) => value !== undefined && value !== null && typeof value !== 'object' && String(value).trim() !== ''
const distinguishedNameValue = (value, key) => {
  if (typeof value !== 'string') return value
  const match = value.match(new RegExp(`(?:^|,)\\s*${key}=([^,]+)`, 'i'))
  return match ? match[1].trim() : value
}

function mxText(value) {
  const text = String(value)
  const match = text.match(/^\s*(\d+)\s+(.+)$/)
  return match ? `Priority ${match[1]} — ${match[2]}` : text
}

export function generateDomainReportPdf(report) {
  const doc = new jsPDF({ unit: 'mm', format: 'a4' })
  const pageWidth = doc.internal.pageSize.getWidth()
  const pageHeight = doc.internal.pageSize.getHeight()
  const margin = 18
  const contentWidth = pageWidth - margin * 2
  let y = 42

  function pageHeader() {
    doc.setFillColor(11, 13, 16)
    doc.rect(0, 0, pageWidth, pageHeight, 'F')
    doc.setFillColor(17, 19, 24)
    doc.rect(0, 0, pageWidth, 24, 'F')
    doc.setDrawColor(36, 40, 48)
    doc.line(0, 24, pageWidth, 24)
    doc.setTextColor(245, 246, 250)
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(11)
    doc.text('DOMAINPROBE', margin, 15)
    doc.setTextColor(170, 157, 255)
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(8)
    doc.text('DOMAIN INTELLIGENCE', pageWidth - margin, 15, { align: 'right' })
  }

  function addPage() {
    doc.addPage()
    pageHeader()
    y = 34
  }

  function ensureSpace(height) {
    if (y + height > pageHeight - 18) addPage()
  }

  function paragraph(text, { indent = 0, color = [214, 215, 220], size = 11, gap = 2 } = {}) {
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(size)
    doc.setTextColor(...color)
    const lines = doc.splitTextToSize(String(text), contentWidth - indent)
    const lineHeight = size * 0.48
    for (const line of lines) {
      ensureSpace(lineHeight + gap)
      doc.text(line, margin + indent, y)
      y += lineHeight
    }
    y += gap
  }

  function section(title) {
    ensureSpace(31)
    y += 6
    const boxTop = y
    doc.setFillColor(17, 19, 24)
    doc.setDrawColor(36, 40, 48)
    doc.roundedRect(margin, boxTop, contentWidth, 13, 2, 2, 'FD')
    doc.setFillColor(112, 93, 218)
    doc.roundedRect(margin, boxTop, 1.5, 13, 0.7, 0.7, 'F')
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(13)
    doc.setTextColor(190, 180, 255)
    doc.text(title, margin + 5, boxTop + 8.5)
    y = boxTop + 20
  }

  pageHeader()
  const domainName = report.domainName || report.rdap?.domainName || report.input || 'Domain report'
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(26)
  doc.setTextColor(245, 246, 250)
  const domainLines = doc.splitTextToSize(String(domainName), contentWidth)
  doc.text(domainLines, margin, y)
  y += domainLines.length * 12 + 1
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(10)
  doc.setTextColor(190, 180, 255)
  doc.text('Domain Analysis Report', margin, y)
  y += 6
  paragraph(`Generated: ${new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date())}`, { color: [139, 145, 158], size: 9, gap: 5 })

  const rdap = report.rdap || {}
  section('Domain Overview')
  const overview = [
    ['Domain', domainName], ['Availability', rdap.availability], ['Registrar', rdap.registrar],
    ['Registrar IANA ID', rdap.registrarIanaId], ['Registrar URL', rdap.registrarUrl],
    ['Created', dateText(rdap.createdAt)], ['Updated', dateText(rdap.updatedAt)],
    ['Expires', dateText(rdap.expiresAt)], ['Days to expiry', rdap.daysRemaining !== null && rdap.daysRemaining !== undefined ? `${rdap.daysRemaining} days` : ''],
  ]
  for (const [label, value] of overview) if (hasValue(value)) paragraph(`${label}: ${value}`, { indent: 2 })

  section('Registry Flags')
  const statuses = Array.isArray(rdap.statuses) ? rdap.statuses : []
  if (statuses.length) statuses.forEach((status) => paragraph(`- ${status}`, { indent: 2 }))
  else paragraph('No records found', { indent: 2, color: [139, 145, 158] })

  section('Nameservers')
  const nameservers = Array.isArray(rdap.nameservers) ? rdap.nameservers : []
  if (nameservers.length) nameservers.forEach((name) => paragraph(`- ${name}`, { indent: 2 }))
  else paragraph('No records found', { indent: 2, color: [139, 145, 158] })

  section('Domain Timeline')
  const events = Array.isArray(rdap.events) ? rdap.events : []
  const timeline = events.map((event) => {
    if (typeof event !== 'string') return ''
    const split = event.indexOf(':')
    const action = split < 0 ? event.trim() : event.slice(0, split).trim()
    const date = split < 0 ? '' : event.slice(split + 1).trim()
    return date ? `${action}: ${dateText(date) || date}` : action
  }).filter(Boolean)
  if (timeline.length) timeline.forEach((event) => paragraph(`- ${event}`, { indent: 2, color: [214, 215, 220] }))
  else {
    const fallback = [['Registration', rdap.createdAt], ['Last changed', rdap.updatedAt], ['Expiration', rdap.expiresAt]].filter(([, value]) => hasValue(value))
    fallback.forEach(([label, value]) => paragraph(`- ${label}: ${dateText(value)}`, { indent: 2, color: [214, 215, 220] }))
  }

  section('DNS Records')
  if (!report.dns || typeof report.dns !== 'object') {
    paragraph('DNS information unavailable', { indent: 2, color: [139, 145, 158] })
  } else {
    for (const [title, key] of recordTypes) {
      ensureSpace(12)
      doc.setFont('helvetica', 'bold')
      doc.setFontSize(11)
      doc.setTextColor(233, 233, 237)
      doc.text(title, margin + 2, y)
      y += 6
      const records = Array.isArray(report.dns[key]) ? report.dns[key] : []
      if (records.length) records.forEach((record) => paragraph(`- ${key === 'mx' ? mxText(record) : String(record)}`, { indent: 4, size: 10, gap: 2 }))
      else paragraph('No records found', { indent: 4, color: [139, 145, 158], size: 10, gap: 3 })
    }
  }

  section('IP & ASN')
  const ipRecords = Array.isArray(report.ip?.records) ? report.ip.records.filter((record) => record && typeof record === 'object') : []
  if (!ipRecords.length) {
    paragraph(report.ip == null || !Array.isArray(report.ip.records) ? 'Resolved IP information unavailable' : 'No resolved IP records found', { indent: 2, color: [139, 145, 158] })
  } else {
    const valueOrDash = (value) => hasValue(value) ? value : '—'
    const locationValue = (name, code) => {
      const locationName = hasValue(name) ? String(name) : ''
      const locationCode = hasValue(code) ? String(code) : ''
      return locationName && locationCode ? `${locationName} (${locationCode})` : locationName || locationCode || '—'
    }
    ipRecords.forEach((record, index) => {
      paragraph(`${index + 1}. ${valueOrDash(record.ip)}`, { indent: 2, color: [190, 180, 255], size: 11, gap: 1 })
      const fields = [
        ['ASN', record.asn], ['AS Name', record.asName], ['AS Domain', record.asDomain],
        ['Country', locationValue(record.country, record.countryCode)],
        ['Continent', locationValue(record.continent, record.continentCode)],
      ]
      fields.forEach(([label, value]) => paragraph(`${label}: ${valueOrDash(value)}`, { indent: 4, size: 10 }))
    })
  }

  section('Technology Detection')
  const technologies = Array.isArray(report.technology?.technologies) ? report.technology.technologies.filter((technology) => technology && typeof technology === 'object') : []
  if (!technologies.length) {
    paragraph('No technologies detected', { indent: 2, color: [139, 145, 158] })
  } else {
    technologies.forEach((technology, index) => {
      paragraph(`${index + 1}. ${hasValue(technology.name) ? technology.name : '—'}`, { indent: 2, color: [190, 180, 255], size: 11, gap: 1 })
      paragraph(`Category: ${hasValue(technology.category) ? technology.category : '—'}`, { indent: 4, size: 10, gap: 1 })
      paragraph(`Confidence: ${hasValue(technology.confidence) ? technology.confidence : '—'}`, { indent: 4, size: 10, gap: 1 })
      const evidence = Array.isArray(technology.evidence) ? technology.evidence.filter((item) => typeof item === 'string' && item.trim()) : []
      paragraph('Evidence', { indent: 4, color: [233, 233, 237], size: 10, gap: 1 })
      if (evidence.length) evidence.forEach((item) => paragraph(`- ${item}`, { indent: 6, size: 10 }))
      else paragraph('No evidence provided', { indent: 6, color: [139, 145, 158], size: 10 })
    })
  }

  const dnssec = report.dnssec
  section('DNSSEC')
  if (!dnssec || typeof dnssec !== 'object') {
    paragraph('DNSSEC information unavailable', { indent: 2, color: [139, 145, 158] })
  } else {
    paragraph(`Status: ${dnssec.signed === true ? 'Signed' : 'Not Signed'}`, { indent: 2 })
    paragraph(`Validation: ${dnssec.validated === true ? 'Validated' : 'Not Validated'}`, { indent: 2 })
    for (const [title, key, fields] of [
      ['DS Records', 'dsRecords', [['Key Tag', 'keyTag'], ['Algorithm', 'algorithm'], ['Digest Type', 'digestType'], ['Digest', 'digest']]],
      ['DNSKEY Records', 'dnskeyRecords', [['Flags', 'flags'], ['Protocol', 'protocol'], ['Algorithm', 'algorithm'], ['Public Key', 'publicKey']]],
    ]) {
      const records = Array.isArray(dnssec[key]) ? dnssec[key] : []
      ensureSpace(12)
      doc.setFont('helvetica', 'bold')
      doc.setFontSize(11)
      doc.setTextColor(233, 233, 237)
      doc.text(title, margin + 2, y)
      y += 6
      if (!records.length) paragraph('No records found', { indent: 4, color: [139, 145, 158], size: 10 })
      records.forEach((record, index) => {
        paragraph(`Record ${index + 1}`, { indent: 4, color: [190, 180, 255], size: 10, gap: 1 })
        fields.forEach(([label, field]) => {
          const value = record?.[field]
          if (hasValue(value)) paragraph(`${label}: ${value}`, { indent: 6, size: 10 })
        })
      })
    }
  }

  const dmarc = report.dmarc
  section('DMARC')
  if (!dmarc || typeof dmarc !== 'object') {
    paragraph('DMARC information unavailable', { indent: 2, color: [139, 145, 158] })
  } else {
    const alignment = (value) => value === 'r' ? 'Relaxed' : value === 's' ? 'Strict' : (hasValue(value) ? value : 'Not provided')
    const dmarcInfo = [
      ['Status', dmarc.present === true ? 'Configured' : 'Not Configured'],
      ['Policy', hasValue(dmarc.policy) ? String(dmarc.policy).toUpperCase() : 'Not provided'],
      ['Subdomain Policy', hasValue(dmarc.subdomainPolicy) ? String(dmarc.subdomainPolicy).toUpperCase() : 'Not provided'],
      ['Percentage', dmarc.percentage !== null && dmarc.percentage !== undefined ? `${dmarc.percentage}%` : 'Not provided'],
      ['DKIM Alignment', alignment(dmarc.dkimAlignment)], ['SPF Alignment', alignment(dmarc.spfAlignment)],
      ['Failure Options', hasValue(dmarc.failureOptions) ? dmarc.failureOptions : '—'],
    ]
    dmarcInfo.forEach(([label, value]) => paragraph(`${label}: ${value}`, { indent: 2 }))
    for (const [title, key] of [['Aggregate Reports', 'aggregateReports'], ['Forensic Reports', 'forensicReports']]) {
      ensureSpace(12)
      doc.setFont('helvetica', 'bold')
      doc.setFontSize(11)
      doc.setTextColor(233, 233, 237)
      doc.text(title, margin + 2, y)
      y += 6
      const addresses = Array.isArray(dmarc[key]) ? dmarc[key].filter((value) => typeof value === 'string' && value.trim()) : []
      if (addresses.length) addresses.forEach((address) => paragraph(address, { indent: 4, size: 10 }))
      else paragraph('None', { indent: 4, color: [139, 145, 158], size: 10 })
    }
    ensureSpace(12)
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(11)
    doc.setTextColor(233, 233, 237)
    doc.text('Record', margin + 2, y)
    y += 6
    paragraph(hasValue(dmarc.record) ? dmarc.record : 'Not configured', { indent: 4, size: 10 })
  }

  section('SSL / HTTPS')
  if (!report.ssl || typeof report.ssl !== 'object') {
    paragraph('SSL information unavailable', { indent: 2, color: [139, 145, 158] })
  } else {
    const certificate = report.ssl.certificate
    const https = report.ssl.https
    if (!certificate || typeof certificate !== 'object') paragraph('SSL certificate information unavailable', { indent: 2, color: [139, 145, 158] })
    else {
      paragraph('SSL Certificate', { indent: 2, color: [233, 233, 237], size: 11, gap: 1 })
      const certificateInfo = [
        ['SSL Enabled', typeof certificate.enabled === 'boolean' ? (certificate.enabled ? 'Yes' : 'No') : certificate.enabled],
        ['Certificate Valid', typeof certificate.valid === 'boolean' ? (certificate.valid ? 'Yes' : 'No') : certificate.valid],
        ['Issuer', distinguishedNameValue(certificate.issuer, 'O')], ['Subject', distinguishedNameValue(certificate.subject, 'CN')],
        ['Valid From', dateText(certificate.validFrom)], ['Expires At', dateText(certificate.expiresAt)],
        ['Days Remaining', certificate.daysRemaining],
        ['TLS Version', typeof certificate.tlsVersion === 'string' ? certificate.tlsVersion.replace(/^TLSv/, 'TLS ') : certificate.tlsVersion],
      ]
      certificateInfo.forEach(([label, value]) => { if (hasValue(value)) paragraph(`${label}: ${value}`, { indent: 4, size: 10 }) })
      const domains = Array.isArray(certificate.domains) ? certificate.domains : []
      paragraph('Covered Domains', { indent: 2, color: [233, 233, 237], size: 10, gap: 1 })
      if (domains.length) domains.forEach((domain) => paragraph(`- ${domain}`, { indent: 4, size: 10 }))
      else paragraph('No domains provided', { indent: 4, color: [139, 145, 158], size: 10 })
    }
    if (!https || typeof https !== 'object') paragraph('HTTPS information unavailable', { indent: 2, color: [139, 145, 158] })
    else {
      paragraph('HTTPS', { indent: 2, color: [233, 233, 237], size: 11, gap: 1 })
      const httpsInfo = [
        ['HTTPS Status', https.statusCode],
        ['HSTS', typeof https.hstsEnabled === 'boolean' ? (https.hstsEnabled ? 'Enabled' : 'Disabled') : https.hstsEnabled],
        ['HTTP to HTTPS Redirect', typeof https.httpRedirectsToHttps === 'boolean' ? (https.httpRedirectsToHttps ? 'Enabled' : 'Disabled') : https.httpRedirectsToHttps],
      ]
      httpsInfo.forEach(([label, value]) => { if (hasValue(value)) paragraph(`${label}: ${value}`, { indent: 4, size: 10 }) })
    }
  }

  section('HTTP / Website')
  const http = report.http
  if (!http || typeof http !== 'object') {
    paragraph('HTTP information unavailable', { indent: 2, color: [139, 145, 158] })
  } else {
    const httpInfo = [
      ['Status Code', http.statusCode], ['Final URL', http.finalUrl],
      ['Response Time', http.responseTimeMs !== null && http.responseTimeMs !== undefined ? `${http.responseTimeMs} ms` : null],
      ['Server', http.server], ['Content Type', http.contentType],
      ['Content Length', http.contentLength !== null && http.contentLength !== undefined ? `${http.contentLength} bytes` : null],
      ['Content Encoding', http.contentEncoding],
    ]
    httpInfo.forEach(([label, value]) => { if (hasValue(value)) paragraph(`${label}: ${value}`, { indent: 2 }) })

    section('Redirects')
    const chain = Array.isArray(http.redirectChain) ? http.redirectChain.filter((value) => typeof value === 'string' && value.trim()) : []
    if (http.redirectLocation) paragraph(`Redirect Location: ${http.redirectLocation}`, { indent: 2 })
    if (chain.length) chain.forEach((value, index) => {
      let target = value
      try { target = new URL(value, http.finalUrl || `https://${domainName}`).toString() } catch { /* Keep the returned relative URL readable. */ }
      paragraph(`${index + 1}. ${target}`, { indent: 2 })
    })
    else if (!http.redirectLocation) paragraph('No redirects', { indent: 2, color: [139, 145, 158] })

    section('Security Headers')
    const securityHeaders = http.securityHeaders && typeof http.securityHeaders === 'object' ? http.securityHeaders : {}
    const headerFields = [
      ['Content-Security-Policy', 'contentSecurityPolicy'], ['Strict-Transport-Security', 'strictTransportSecurity'],
      ['X-Content-Type-Options', 'xContentTypeOptions'], ['X-Frame-Options', 'xFrameOptions'],
      ['Referrer-Policy', 'referrerPolicy'], ['Permissions-Policy', 'permissionsPolicy'],
    ]
    headerFields.forEach(([label, key]) => paragraph(`${label}: ${hasValue(securityHeaders[key]) ? securityHeaders[key] : 'Not configured'}`, { indent: 2, size: 10 }))

    section('Cookies')
    const cookies = Array.isArray(http.cookies) ? http.cookies : []
    if (!cookies.length) paragraph('No cookies detected', { indent: 2, color: [139, 145, 158] })
    cookies.forEach((cookie, index) => {
      paragraph(cookie?.name || `Cookie ${index + 1}`, { indent: 2, color: [233, 233, 237], size: 11, gap: 1 })
      const cookieInfo = [
        ['Secure', typeof cookie?.secure === 'boolean' ? (cookie.secure ? 'Yes' : 'No') : cookie?.secure],
        ['HttpOnly', typeof cookie?.httpOnly === 'boolean' ? (cookie.httpOnly ? 'Yes' : 'No') : cookie?.httpOnly],
        ['SameSite', cookie?.sameSite], ['Domain', cookie?.domain], ['Path', cookie?.path],
        ['Expires', dateText(cookie?.expires) || cookie?.expires],
      ]
      cookieInfo.forEach(([label, value]) => { if (hasValue(value)) paragraph(`${label}: ${value}`, { indent: 4, size: 10 }) })
    })
  }

  const pageCount = doc.getNumberOfPages()
  for (let page = 1; page <= pageCount; page += 1) {
    doc.setPage(page)
    doc.setDrawColor(36, 40, 48)
    doc.line(margin, pageHeight - 13, pageWidth - margin, pageHeight - 13)
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(8)
    doc.setTextColor(139, 145, 158)
    doc.text('DomainProbe · Domain Analysis Report', margin, pageHeight - 8)
    doc.text(`Page ${page} of ${pageCount}`, pageWidth - margin, pageHeight - 8, { align: 'right' })
  }

  const filename = `domainprobe-${String(domainName).toLowerCase().replace(/[^a-z0-9]+/g, '-')}-report.pdf`
  doc.save(filename)
}
