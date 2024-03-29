package morbid.client

import java.util.Date


object domain {

  case class Violation (reason: String, trace: Option[Throwable] = None)
  case class CreateAccountRequest(name: String, `type`: String)
  case class CreateUserRequest(account: Long, name: String, email: String, `type`: String, password: Option[String] = None)
  case class UpdateUserRequest(account: Long, id: Long, name: String, email: String, `type`: String)
  case class AuthenticateRequest(email: String, password: String)
  case class ChangePasswordRequest(email: String, old: String, replacement: String)
  case class ResetPasswordRequest(email: String)
  case class AssignPermissionRequest(user: Long, permission: String)

  case class Account(
    id      : Long,
    created : Date,
    deleted : Option[Date],
    active  : Boolean,
    name    : String,
    `type`  : String)

  case class Token(
    token     : String,
    issuer    : String,
    subject   : String,
    issuedAt  : Date,
    expiresAt : Option[Date]) {
    def expiresAtInSeconds() = expiresAt.map(_.getTime / 1000 toInt)
  }

  case class Permission(
    id      : Long,
    user    : Long,
    created : Date,
    deleted : Option[Date],
    name    : String)

  case class Password(
    id       : Long,
    user     : Long,
    created  : Date,
    deleted  : Option[Date],
    method   : String,
    password : String,
    token    : String)

  case class User(
    id          : Long,
    account     : Option[Account],
    created     : Date,
    deleted     : Option[Date],
    active      : Boolean,
    name        : String,
    email       : String,
    `type`      : String,
    password    : Option[Password],
    permissions : Option[Seq[Permission]])
}