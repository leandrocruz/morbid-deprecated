# Change Log

## Release 2.4.1
__LTS 23/07/2026__

 - [Leandro] Corrigindo erro em prod: `java.lang.NoClassDefFoundError: store/Violations$`

## Release 2.4.0
__LTS 10/07/2026__

 - [Leandro] Nova coluna `accounts.identifier` (nullable, `VARCHAR(256)`) para armazenar CPF/CNPJ. Índice único parcial `accounts_identifier_key ON accounts (identifier) WHERE identifier IS NOT NULL` — duas contas só podem compartilhar `identifier` se ambos forem `NULL`. Removida a constraint `UNIQUE` da coluna `accounts.name`: o nome da conta volta a ser apenas rótulo de exibição; a chave de negócio passa a ser `identifier`. Script idempotente em `backend/migrations/2026-06-19-add-account-identifier.sql` (`DROP CONSTRAINT IF EXISTS accounts_name_key` + `ADD COLUMN IF NOT EXISTS identifier` + `CREATE UNIQUE INDEX IF NOT EXISTS`, dentro de `BEGIN/COMMIT`). Propagado por todo o stack: `AccountTuple` ganha `Option[String]` no fim, `Account` e `CreateAccountRequest` ganham `identifier: Option[String]` no backend e no `client-okhttp`, `AccountTable` mapeia a coluna, e `DatabaseAccounts.create` injeta o valor no insert
 - [Leandro] Espelha a mudança do morbid novo (vide changelog em `/Users/leandro/dev/projects/morbid`). Endpoint `POST /account` aceita `identifier` opcional no body; respostas (`GET /account/...`) incluem o campo
 - [Leandro] Nova rota `GET /account/identifier/:it` → `AccountController.byIdentifier(it: String)` → `DatabaseAccounts.byIdentifier(identifier: String): Future[Option[Account]]`. Retorna 200 com `Account` JSON quando encontrado, 404 caso contrário (via `toResult` que mapeia `None` → 404). A `Accounts` trait passa a expor `def byIdentifier(identifier: String): Future[Option[Account]]`

## Release v2.3.0
__LTS 16/06/2026__

 - [Leandro] Coluna `accounts.name` aumentada de VARCHAR(64) para VARCHAR(256) em `backend/schema.sql`

## Release v2.2.10
__LTS 13/08/2024__

 - Setting User.account upon user creation

## Release v2.2.9
__LTS 26/06/2023__

 - Setting `deleted` of the old password when the password is changed

## Release v2.2.8
__LTS 17/05/2023__

 - Setting `deleted` of the old password when the password is reset

## Release v2.2.7
__LTS 20/09/2022__

 - Always create passwords using the current time (may disable forced updates)

## Release v2.2.6
__LTS 10/09/2022__

 - Added /users to return the list of all accounts/users/secrets so that morbid clients can implement aggressive caching

## Release v2.2.5
__LTS 09/08/2022__

 - Logging errors with invalid signatures

## Release v2.2.4
__LTS 09/11/2021__

 - Using email 'toLowerCase' when creating users and testing secrets

## Release v2.2.3
__LTS ??/2021__

 - Invalidando o acesso de usuário removidos

## Release v2.2.2
__LTS 30/08/2021__

 - Adicionando endpoing `DELETE /account/:a/user/:u`

## Release v2.2.1
__LTS 22/06/2021__

 - Adicionando rota '/user/password/force'
