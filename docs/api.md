# API Contract

Base path: `/api/v1`

## Health

- `GET /api/v1/health`

## Auth

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

## Lists

- `GET /api/v1/lists`
- `POST /api/v1/lists`
- `PUT /api/v1/lists/{id}`
- `DELETE /api/v1/lists/{id}`

## Items

- `POST /api/v1/lists/{id}/items`
- `PUT /api/v1/items/{id}`
- `DELETE /api/v1/items/{id}`

## Scraper

- `POST /api/v1/utils/scrape`

## Public access

- `GET /api/v1/share/{token}`
- `POST /api/v1/share/{token}/items/{itemId}/claim`

## Admin

- `GET /api/v1/admin/users`
- `PUT /api/v1/admin/settings`
