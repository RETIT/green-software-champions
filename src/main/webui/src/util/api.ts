/**
 * The status Quarkus answers the frontend's requests with once the login session
 * has expired, instead of redirecting them to the identity provider, which fetch()
 * could not follow (quarkus.oidc.authentication.java-script-auto-redirect=false).
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
 * fetch() for the backend's API. Marks the request as one of JavaScript, so that
 * Quarkus answers it with 499 rather than a redirect once the session has
 * expired. The page is then reloaded, which leads through the identity provider's
 * login back to it, without asking for the password while the provider's own
 * session is active.
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
