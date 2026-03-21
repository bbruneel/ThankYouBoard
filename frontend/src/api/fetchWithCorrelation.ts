/**
 * Wrapper around native fetch that attaches a unique X-Request-Id header
 * to every outgoing request and exposes it for error correlation.
 *
 * The same ID is returned by the backend in the x-request-id response
 * header, making it easy to correlate browser failures with CloudWatch
 * Logs / X-Ray traces.
 */
export async function fetchWithCorrelation(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const requestId = crypto.randomUUID();
  const headers = new Headers(init?.headers);
  headers.set('X-Request-Id', requestId);

  const response = await fetch(input, { ...init, headers });

  if (!response.ok) {
    console.error(
      `[API ${response.status}] ${init?.method ?? 'GET'} ${String(input)} — x-request-id: ${requestId}`,
    );
  }

  return response;
}

/**
 * Extracts the correlation / request ID from a response so it can be
 * shown in error messages.
 */
export function getRequestId(response: Response): string | null {
  return response.headers.get('x-request-id');
}
