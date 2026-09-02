// The HTTP methods the editor offers. GET is the default (see lib/request.js).
// HEAD and OPTIONS rarely carry a body, but we still let the user type one -
// the backend will decide what to actually send in a later phase.
export const HTTP_METHODS = [
  'GET',
  'POST',
  'PUT',
  'PATCH',
  'DELETE',
  'HEAD',
  'OPTIONS',
]
