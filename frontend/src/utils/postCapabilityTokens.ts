const TOKEN_KEY_PREFIX = 'thankyouboard:post-capability:';

function makeKey(postId: string) {
  return `${TOKEN_KEY_PREFIX}${postId}`;
}

export function getPostCapabilityToken(postId: string): string | null {
  if (typeof window === 'undefined') return null;
  if (!postId) return null;
  return window.sessionStorage.getItem(makeKey(postId));
}

export function setPostCapabilityToken(postId: string, token: string) {
  if (typeof window === 'undefined') return;
  if (!postId || !token) return;
  window.sessionStorage.setItem(makeKey(postId), token);
}

export function clearPostCapabilityToken(postId: string) {
  if (typeof window === 'undefined') return;
  if (!postId) return;
  window.sessionStorage.removeItem(makeKey(postId));
}

