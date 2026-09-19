// Regression tests for the Raw-body CRLF round-trip bug: a browser
// <textarea>'s `.value` getter silently rewrites \r\n / \r to \n on every
// read (including the very first onChange after loading a body that still
// has literal \r\n), so "type a character, then delete it" looked like a
// no-op in the UI but permanently swapped the imported bytes for an LF-only
// copy. That broke CRLF-significant bodies (e.g. a multipart/form-data body
// copied from Chrome DevTools, where \r\n delimits each boundary line per
// RFC 2046) even though nothing the user could see had changed.
//
// Run with: node --test src/lib/request.test.js

import assert from 'node:assert/strict'
import { test } from 'node:test'
import { normalizeTextareaNewlines, reconcileBodyEdit, toRequestPayload } from './request.js'

test('normalizeTextareaNewlines collapses CRLF and lone CR to LF', () => {
  assert.equal(normalizeTextareaNewlines('a\r\nb\rc\nd'), 'a\nb\nc\nd')
  assert.equal(normalizeTextareaNewlines('no newlines here'), 'no newlines here')
})

test('reconcileBodyEdit: browser-rewritten CRLF is discarded when nothing visible changed', () => {
  // A multipart body as it comes out of the cURL importer: real \r\n before
  // every boundary line, exactly as required by RFC 2046.
  const pristine =
    '------WebKitFormBoundaryXXXX\r\n' +
    'Content-Disposition: form-data; name="_1_email"\r\n\r\n' +
    'user@example.com\r\n' +
    '------WebKitFormBoundaryXXXX--\r\n'

  // What a <textarea> hands back from onChange after the user types one
  // character and deletes it again: content identical, but the browser has
  // already collapsed every \r\n to \n while computing its "API value".
  const afterTypeAndUndo = pristine.replace(/\r\n/g, '\n')
  assert.notEqual(afterTypeAndUndo, pristine, 'the browser rewrite must actually differ in bytes')

  const reconciled = reconcileBodyEdit(pristine, afterTypeAndUndo)
  assert.equal(reconciled, pristine, 'must restore the exact original bytes, CRLFs included')
})

test('reconcileBodyEdit: a real content edit is accepted as-is', () => {
  const pristine = 'foo=bar\r\nbaz=qux'
  const edited = pristine.replace(/\r\n/g, '\n').replace('bar', 'BARX')
  assert.equal(reconcileBodyEdit(pristine, edited), edited)
})

test('reconcileBodyEdit: with no pristine baseline (hand-built request), edits pass through untouched', () => {
  assert.equal(reconcileBodyEdit(null, 'anything the user typed'), 'anything the user typed')
})

test('reconcileBodyEdit: repeated edits that net out to no visible change still snap back to pristine bytes', () => {
  const pristine = 'a=1\r\nb=2\r\nc=3'
  const step1 = pristine.replace(/\r\n/g, '\n') + 'X' // browser already ate the CRs, plus a typed char
  const step2 = step1.slice(0, -1) // delete the typed char
  assert.equal(reconcileBodyEdit(pristine, step1), step1) // real change: content differs
  // After deleting the char, the *content* is back to the pristine text
  // (only line endings differ) - the fix must recover the exact bytes.
  assert.equal(reconcileBodyEdit(pristine, step2), pristine)
})

test('toRequestPayload never touches an untouched raw body: special characters, empty values, duplicate keys, and a field literally named "0" all survive byte-for-byte', () => {
  // Modelled on the reported Next.js Server Action body: percent-encoding,
  // '@', '+', '[', ']', '"', '$', underscores, an empty value, a duplicate
  // key, and a field named "0" holding a JSON-array-looking string.
  const body =
    '_1_email=user%40example.com&' +
    '_1_password=p%2Bassw0rd&' +
    '_1_cf-turnstile-response=&' + // empty value must survive
    '_1_next=%2F&' +
    '_1_captchaToken=&' +
    'foo=a&foo=b&' + // duplicate key must survive, not collapse
    '0=%5B%22%24K1%22%5D' // field named "0", value ["$K1"] percent-encoded

  const request = {
    method: 'POST',
    url: '  https://example.com/action  ', // toRequestPayload trims the URL, not the body
    headers: [{ id: 1, key: 'Content-Type', value: 'application/x-www-form-urlencoded' }],
    cookies: [],
    body,
    bodyType: 'raw',
    multipart: [],
    curlOptions: null,
  }

  const payload = toRequestPayload(request)

  // The body is opaque text end-to-end - toRequestPayload must not decode,
  // re-encode, reorder, or deduplicate any of it.
  assert.equal(payload.body, body)
})
