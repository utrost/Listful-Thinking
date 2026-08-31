export interface HealthResponse {
  status: string;
}

export interface AuthUser {
  id: string;
  username: string;
  email: string | null;
  role: 'ADMIN' | 'USER';
}

export interface RegisterRequest {
  username: string;
  email?: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AdminSettings {
  registrationEnabled: boolean;
}

export interface UpdateAdminSettingsRequest {
  registrationEnabled: boolean;
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers ?? {})
    }
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export async function getHealth(): Promise<HealthResponse> {
  return requestJson<HealthResponse>('/api/v1/health');
}

export async function register(request: RegisterRequest): Promise<AuthUser> {
  return requestJson<AuthUser>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function login(request: LoginRequest): Promise<AuthUser> {
  return requestJson<AuthUser>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  const response = await fetch('/api/v1/auth/me', {
    credentials: 'include'
  });

  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json() as Promise<AuthUser>;
}

export async function logout(): Promise<void> {
  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
}

export async function getAdminSettings(): Promise<AdminSettings> {
  return requestJson<AdminSettings>('/api/v1/admin/settings');
}

export async function updateAdminSettings(request: UpdateAdminSettingsRequest): Promise<AdminSettings> {
  return requestJson<AdminSettings>('/api/v1/admin/settings', {
    method: 'PUT',
    body: JSON.stringify(request)
  });
}
