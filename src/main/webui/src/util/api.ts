/**
 * Quarkus's answer once the session has expired, instead of a redirect to the
 * identity provider, which fetch() could not follow (java-script-auto-redirect).
 */
const SESSION_EXPIRED = 499;

/** Thrown when the logged-in user lacks the role the API requires. */
export class AccessDeniedError extends Error {
  constructor() {
    super(
      "Your account has no access to Green Software Champions. Ask an administrator for the role “user”.",
    );
    this.name = "AccessDeniedError";
  }
}

/**
 * fetch() for the backend's API. Once the session has expired, reloads the page,
 * which leads through the identity provider's login back to it.
 *
 * @throws AccessDeniedError if the user lacks the required role (403)
 */
export async function apiFetch(
  path: string,
  signal?: AbortSignal,
): Promise<Response> {
  const response = await fetch(path, {
    signal,
    headers: { "X-Requested-With": "JavaScript" },
  });
  if (response.status === SESSION_EXPIRED) {
    window.location.reload();
    // Never settles, as the page is about to be replaced
    return new Promise<Response>(() => {});
  }
  if (response.status === 403) {
    throw new AccessDeniedError();
  }
  return response;
}
