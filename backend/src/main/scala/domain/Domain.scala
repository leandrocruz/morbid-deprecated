package domain

import java.sql.Timestamp
import java.util.Date

import play.api.libs.json.Json
import play.api.libs.json.Reads.dateReads
import play.api.libs.json.Writes.dateWrites

case object Done
case object UnknownUser
case object ResourceAlreadyExists

case class  GetByEmail(email: String)
case class  GetByToken(token: String)
case class  GetById(id: Long)
case class  CreateResource[T](request: T)

case class Account(
  id       : Long,
  created  : Date,
  deleted  : Option[Date],
  active   : Boolean,
  name     : String,
  `type`   : String
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
  account     : Option[Account],
  created     : Date,
  deleted     : Option[Date],
  active      : Boolean,
  name        : String,
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

class UserWithAccount(

)

case class ServerTime(time: Date)
case class AuthenticateRequest(email: String, password: String)
case class ImpersonateRequest(email: String, master: String)
case class CreateAccountRequest(name: String, `type`: String)
case class CreateUserRequest(account: Long, name: String, email: String, `type`: String, password: Option[String] = None)
case class CreatePasswordRequest(user: Long, method: String, password: String, token: String, forceUpdate: Boolean = false)
case class ResetPasswordRequest(email: String)
case class ChangePasswordRequest(email: String, old: String, replacement: String)
case class ForcePasswordRequest(email: String, password: String)
case class RefreshUserRequest(user: Long)
case class AssignPermissionRequest(user: Long, permission: String)
case class ByAccount(id: Long)
case class DeleteUser(account: Long, user: Long)

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
  implicit val ChangePasswordRequestReader  = Json.reads[ChangePasswordRequest]
  implicit val ForcePasswordRequestReader   = Json.reads[ForcePasswordRequest]
  implicit val ImpersonateRequestReader     = Json.reads[ImpersonateRequest]
}

import slick.jdbc.PostgresProfile.api._

object tuples {
  type AccountTuple    = (Long, Timestamp, Option[Timestamp], Boolean, String, String)
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

  def toAccount(tuple: AccountTuple): Account = tuple match {
    case (id, created, deleted, active, name, kind) =>
      Account(
        id      = id,
        created = new Date(created.getTime),
        deleted = deleted.map(it => new Date(it.getTime)),
        active  = active,
        name    = name,
        `type`  = kind,
      )
  }

  def toUser(tuple: UserTuple) = tuple match {
    case (id, _ /* account id */, created, deleted, active, name, email, kind) =>
      User(
        id          = id,
        account     = None,
        created     = new Date(created.getTime),
        deleted     = deleted.map(it => new Date(it.getTime)),
        active      = active,
        name        = name,
        email       = email,
        `type`      = kind,
        password    = None,
        permissions = None)
  }
}

class AccountTable(tag: Tag) extends Table[tuples.AccountTuple](tag, "accounts") {
  def id       : Rep[Long]              = column[Long]              ("id", O.PrimaryKey, O.AutoInc)
  def created  : Rep[Timestamp]         = column[Timestamp]         ("created")
  def deleted  : Rep[Option[Timestamp]] = column[Option[Timestamp]] ("deleted")
  def active   : Rep[Boolean]           = column[Boolean]           ("active")
  def name     : Rep[String]            = column[String]            ("name")
  def `type`   : Rep[String]            = column[String]            ("type")
  def * = (id, created, deleted, active, name, `type`)
}

class UserTable(tag: Tag) extends Table[tuples.UserTuple](tag, "users") {
  def id       : Rep[Long]              = column[Long]              ("id", O.PrimaryKey, O.AutoInc)
  def account  : Rep[Long]              = column[Long]              ("account")
  def created  : Rep[Timestamp]         = column[Timestamp]         ("created")
  def deleted  : Rep[Option[Timestamp]] = column[Option[Timestamp]] ("deleted")
  def active   : Rep[Boolean]           = column[Boolean]           ("active")
  def name     : Rep[String]            = column[String]            ("name")
  def email    : Rep[String]            = column[String]            ("email")
  def `type`   : Rep[String]            = column[String]            ("type")
  def * = (id, account, created, deleted, active, name, email, `type`)
}
class SecretTable(tag: Tag) extends Table[tuples.SecretTuple](tag, "secrets") {
  def id       : Rep[Long]              = column[Long]              ("id", O.PrimaryKey, O.AutoInc)
  def user     : Rep[Long]              = column[Long]              ("user_id")
  def created  : Rep[Timestamp]         = column[Timestamp]         ("created")
  def deleted  : Rep[Option[Timestamp]] = column[Option[Timestamp]] ("deleted")
  def method   : Rep[String]            = column[String]            ("method")
  def password : Rep[String]            = column[String]            ("password")
  def token    : Rep[String]            = column[String]            ("token")
  def * = (id, user, created, deleted, method, password, token)
}

class PermissionTable(tag: Tag) extends Table[tuples.PermissionTuple](tag, "permissions") {
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