# Terminology

## Actor terms

- **Admin:** registered user with instance administration privileges.
- **Owner:** registered user who created and owns a list.
- **Registered user:** authenticated account on the same instance.
- **Internal shared user:** registered user granted access to another user's list.
- **Guest:** unauthenticated visitor using a public share token.

## Domain terms

- **List:** a collection of items owned by one user. Lists have a type: `WISH`, `CHORE`, or `EVENT`.
- **Item:** an entry inside a list. Fields are interpreted by list type.
- **Share token:** cryptographically random URL-safe token that exposes a public guest view of one list.
- **Internal share:** access grant from a list owner to another registered user.
- **Claim:** guest reservation of an open item to prevent duplicate purchases.
- **Notification:** in-app reminder or system message stored in the database.
- **Setting:** global key/value configuration, such as registration enabled.

## Implementation naming

- Java entity class for a list should be `ListEntity` to avoid collision with `java.util.List`.
- Database table names use plural nouns: `users`, `lists`, `items`, `settings`, `list_shares`, `notifications`.
- Public API DTOs must not be JPA entities.