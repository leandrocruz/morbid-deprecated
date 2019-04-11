package domain

import java.sql.Timestamp
import java.util.Date

import play.api.libs.json.Json
import play.api.libs.json.Reads.dateReads
import play.api.libs.json.Writes.dateWrites

case object Done
case object UnknownUser
case object ResourceAlreadyExists

case class  GetByToken(token: String)
case class  GetById(id: Long)
case class  CreateResource[T](request: T)

case class Account(
  id       : Long,
  created  : Date,
  deleted  : Option[Date],
  active   : Boolean,
  name     : String,
)

case class Password(
  id       : Long,
  user     : Long,
  created  : Date,
  deleted  : Option[Date],
  method   : String,
  password : String,
  token    : String
)

case class User(
  id          : Long,
  account     : Long,
  created     : Date,
  deleted     : Option[Date],
  active      : Boolean,
  username    : String,
  email       : String,
  `type`      : String,
  password    : Option[Password],
  permissions : Option[Seq[Permission]]
)

case class Token(
  token     : String,
  issuer    : String,
  subject   : String,
  issuedAt  : Date,
  expiresAt : Option[Date]
)

case class Permission(
  id        : Long,
  user      : Long,
  created   : Date,
  deleted   : Option[Date],
  name      : String
)

case class AuthenticateRequest(username: String, password: String)
case class CreateAccountRequest(name: String)
case class CreateUserRequest(account: Long, username: String, password: Option[String], email: String, `type`: String)
case class CreatePasswordRequest(user: Long, method: String, password: String, token: String)
case class ResetPasswordRequest(username: Option[String], email: Option[String])
case class RefreshUserRequest(user: Long)
case class AssignPermissionRequest(user: Long, permission: String)
case class ServerTime(time: Date)

object json {
  val format = "yyyyMMdd'T'HHmmss"
  implicit val CustomDateWrites             = dateWrites(format)
  implicit val CustomDateReads              = dateReads(format)
  implicit val ServerTimeWriter             = Json.writes[ServerTime]
  implicit val AccountWriter                = Json.writes[Account]
  implicit val PasswordWriter               = Json.writes[Password]
  implicit val PermissionWriter             = Json.writes[Permission]
  implicit val UserWriter                   = Json.writes[User]
  implicit val TokenWriter                  = Json.writes[Token]
  implicit val AuthenticateRequestReader    = Json.reads[AuthenticateRequest]
  implicit val CreateAccountRequestReader   = Json.reads[CreateAccountRequest]
  implicit val CreateUserRequestReader      = Json.reads[CreateUserRequest]
  implicit val ResetPasswordRequestReader   = Json.reads[ResetPasswordRequest]
  implicit val RefreshUserRequestReader     = Json.reads[RefreshUserRequest]
  implicit val AddPermissionRequestReader   = Json.reads[AssignPermissionRequest]
}

import slick.jdbc.PostgresProfile.api._

object tuples {
  type AccountTuple    = (Long, Timestamp, Option[Timestamp], Boolean, String)
  type UserTuple       = (Long, Long, Timestamp, Option[Timestamp], Boolean, String, String, String)
  type SecretTuple     = (Long, Long, Timestamp, Option[Timestamp], String, String, String)
  type PermissionTuple = (Long, Long, Timestamp, Option[Timestamp], String)

  def toPermission(tuple: Option[PermissionTuple]) = tuple map {
    case (id, user, created, deleted, name) =>
      Permission(
        id        = id,
        user      = user,
        created   = created,
        deleted   = deleted,
        name      = name)
  }

  def toPassword(tuple: Option[SecretTuple]) = tuple map {
    case (id, user, created, deleted, method, password, token) =>
      Password(
        id       = id,
        user     = user,
        created  = new Date(created.getTime),
        deleted  = deleted.map(it => new Date(it.getTime)),
        method   = method,
        password = password,
        token    = token)
  }

  def toUser(tuple: UserTuple) = tuple match {
    case (id, account, created, deleted, active, username, email, accType) =>
      User(
        id          = id,
        account     = account,
        created     = new Date(created.getTime),
        deleted     = deleted.map(it => new Date(it.getTime)),
        active      = active,
        username    = username,
        email       = email,
        `type`      = accType,
        password    = None,
        permissions = None)
  }
}

class AccountTable(tag: Tag) extends Table[tuples.AccountTuple](tag, "account") {
  def id       : Rep[Long]              = column[Long]              ("id", O.PrimaryKey, O.AutoInc)
  def created  : Rep[Timestamp]         = column[Timestamp]         ("created")
  def deleted  : Rep[Option[Timestamp]] = column[Option[Timestamp]] ("deleted")
  def active   : Rep[Boolean]           = column[Boolean]           ("active")
  def name     : Rep[String]            = column[String]            ("name")
  def * = (id, created, deleted, active, name)
}

class UserTable(tag: Tag) extends Table[tuples.UserTuple](tag, "users") {
  def id       : Rep[Long]              = column[Long]              ("id", O.PrimaryKey, O.AutoInc)
  def account  : Rep[Long]              = column[Long]              ("account")
  def created  : Rep[Timestamp]         = column[Timestamp]         ("created")
  def deleted  : Rep[Option[Timestamp]] = column[Option[Timestamp]] ("deleted")
  def active   : Rep[Boolean]           = column[Boolean]           ("active")
  def username : Rep[String]            = column[String]            ("username")
  def email    : Rep[String]            = column[String]            ("email")
  def `type`   : Rep[String]            = column[String]            ("type")
  def * = (id, account, created, deleted, active, username, email, `type`)
}
class SecretTable(tag: Tag) extends Table[tuples.SecretTuple](tag, "secret") {
  def id       : Rep[Long]              = column[Long]              ("id", O.PrimaryKey, O.AutoInc)
  def user     : Rep[Long]              = column[Long]              ("user_id")
  def created  : Rep[Timestamp]         = column[Timestamp]         ("created")
  def deleted  : Rep[Option[Timestamp]] = column[Option[Timestamp]] ("deleted")
  def method   : Rep[String]            = column[String]            ("method")
  def password : Rep[String]            = column[String]            ("password")
  def token    : Rep[String]            = column[String]            ("token")
  def * = (id, user, created, deleted, method, password, token)
}

class PermissionTable(tag: Tag) extends Table[tuples.PermissionTuple](tag, "permission") {
  def id        : Rep[Long]              = column[Long]              ("id", O.PrimaryKey, O.AutoInc)
  def user      : Rep[Long]              = column[Long]              ("user_id")
  def created   : Rep[Timestamp]         = column[Timestamp]         ("created")
  def deleted   : Rep[Option[Timestamp]] = column[Option[Timestamp]] ("deleted")
  def name      : Rep[String]            = column[String]            ("name")
  def * = (id, user, created, deleted, name)
}

object collections {
  val accounts    = TableQuery[AccountTable]
  val users       = TableQuery[UserTable]
  val secrets     = TableQuery[SecretTable]
  val permissions = TableQuery[PermissionTable]
}