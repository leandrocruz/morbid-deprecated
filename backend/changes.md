# Change Log

## Release v2.2.10
__LTS 12/07/2023__

 - Changing the expiration time for tokens to 1 day

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