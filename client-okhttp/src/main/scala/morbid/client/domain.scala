package morbid.client

import java.util.Date

trait Violation

object violations {
  case object NoPasswordAvailable extends Violation
  case object PasswordAlreadyUsed extends Violation
  case object PasswordTooWeak     extends Violation
  case object PasswordTooOld      extends Violation
  case object PasswordMismatch    extends Violation
  case object NotImplemented      extends Violation
  case class  UnknownViolation             (t: Throwable) extends Violation
  case class  ForeignKeyViolation          (t: Throwable) extends Violation
  case class  UniqueViolation              (t: Throwable) extends Violation
  case class  IntegrityConstraintViolation (t: Throwable) extends Violation
}

object domain {

  case class CreateAccountRequest(name: String, `type`: String)

  case class CreateUserRequest(account: Long, username: String, password: Option[String], email: String, `type`: String)

  case class AuthenticateRequest(username: String, password: String)

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
    account     : Account,
    created     : Date,
    deleted     : Option[Date],
    active      : Boolean,
    username    : String,
    email       : String,
    `type`      : String,
    password    : Option[Password],
    permissions : Option[Seq[Permission]])
}