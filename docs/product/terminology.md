# Terminology

## Actor terms

- **Admin:** registered user with instance administration privileges.
- **List owner:** registered user who created and owns a list.
- **Item owner:** user-visible label for the person, role, or agent responsible for making sure an item happens. This is coordination metadata, not an access grant.
- **Assistant/helper:** user-visible label for a person, role, bot, or automation assisting with an item. Assistants do not receive permissions by being named.
- **Registered user:** authenticated account on the same instance.
- **Internal shared user:** registered user granted access to another user's list.
- **Guest:** unauthenticated visitor using a public share token.

## Domain terms

- **List:** a collection of items owned by one user. Lists have a type: `WISH`, `TODO`, `GROCERY`, `CHORE`, or `EVENT`.
- **Item:** an entry inside a list. Fields are interpreted by list type; work-style items can also carry owner/helper labels.
- **Share token:** cryptographically random URL-safe token that exposes a public guest view of one list.
- **Public share mode:** owner-selected behavior for a public link: `VIEW`, `WISH_CLAIM`, or `SIGNUP`.
- **Internal share:** access grant from a list owner to another registered user, currently `READ` or `CONTRIBUTE`.
- **Claim/signup:** guest reservation of an open item to prevent duplicate purchases or duplicate slot signups.
- **Notification:** in-app reminder or system message stored in the database.
- **Setting:** global key/value configuration, such as registration enabled.

## Implementation naming

- Java entity class for a list should be `ListEntity` to avoid collision with `java.util.List`.
- Database table names use plural nouns: `users`, `lists`, `items`, `settings`, `list_shares`, `notifications`.
- Public API DTOs must not be JPA entities.