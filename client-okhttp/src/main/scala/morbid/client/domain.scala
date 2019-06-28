package morbid.client

import java.util.Date

object domain {

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