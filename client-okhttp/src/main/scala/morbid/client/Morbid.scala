package morbid.client

import java.net.URLEncoder

import morbid.client.domain._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import org.apache.commons.text.StringEscapeUtils.escapeJson
import okhttp3._
import org.slf4j.LoggerFactory

trait MorbidClient {
  def createAccount    (request: CreateAccountRequest)    : Future[Either[Violation, Account]]
  def createUser       (request: CreateUserRequest)       : Future[Either[Violation, User]]
  def authenticateUser (request: AuthenticateRequest)     : Future[Either[Violation, Token]]
  def resetPassword    (request: ResetPasswordRequest)    : Future[Either[Violation, User]]
  def changePassword   (request: ChangePasswordRequest)   : Future[Either[Violation, Unit]]
  def assignPermission (request: AssignPermissionRequest) : Future[Either[Violation, Unit]]
  def byId             (id: Long)                         : Future[Either[Throwable,User]]
  def byToken          (token: String)                    : Future[Either[Throwable,User]]
  def byEmail          (email: String)                    : Future[Either[Throwable,User]]
  def usersBy          (account: Long)                    : Future[Either[Throwable, Seq[User]]]
}

abstract class HttpMorbidClientSupport (
  location         : String,
  implicit val ec  : ExecutionContext) extends MorbidClient {

  val json = MediaType.get("application/json") //; charset=utf-8

  val client = new OkHttpClient()

  val log = LoggerFactory.getLogger(getClass)

  val SeeServerLog = new Exception("See server log for details")

  def error(body: String) = {
    body match {
      case "UnknownUser"                  => Left(Violation(body))
      case "PasswordTooOld"               => Left(Violation(body))
      case "PasswordMismatch"             => Left(Violation(body))
      case "NoPasswordAvailable"          => Left(Violation(body))
      case "PasswordAlreadyUsed"          => Left(Violation(body))
      case "PasswordTooWeak"              => Left(Violation(body))
      case "UniqueViolation"              => Left(Violation(body              , Some(SeeServerLog)))
      case "ForeignKeyViolation"          => Left(Violation(body              , Some(SeeServerLog)))
      case "IntegrityConstraintViolation" => Left(Violation(body              , Some(SeeServerLog)))
      case _                              => Left(Violation("UnknownViolation", Some(new Exception(body))))
    }
  }

  def handleViolation[T](fn: String => Either[Violation, T])(request: Request) = {
    Try(client.newCall(request).execute()) match {
      case Failure(e) =>
        log.error("Morbid Client Error", e)
        Left(Violation("UnknownViolation", Some(e)))
      case Success(r) =>
        val body = r.body().string()
        r.code() match {
        case 200 => fn    (body)
        case _   => error (body)
      }
    }
  }

  def handleError[T](fn: String => Either[Throwable, T])(request: Request): Either[Throwable, T] = {
    Try(client.newCall(request).execute()) match {
      case Failure(e) =>
        log.error("Morbid Client Error", e)
        Left(e)
      case Success(r) =>
        if(r.isSuccessful) {
          fn(r.body().string())
        } else {
          log.error(s"Morbid Client Error 'Not 200: ${r.code()}'\n${r.body().string()}")
          Left(new Exception(s"Not 200: ${r.code()}"))
        }
    }
  }

  def getRequest(path: String) = {
    new Request.Builder().url(s"$location$path").build
  }

  def postRequest(path: String, body: Option[String]) = {
    body map { it =>
      new Request.Builder().url(s"$location$path").post(RequestBody.create(it, json)).build
    } getOrElse {
      new Request.Builder().url(s"$location$path").build
    }
  }

  override def byEmail(email: String) = {
    Future {
      handleError(toUser) {
        getRequest(s"/user/email/${URLEncoder.encode(email, "utf8")}")
      }
    }
  }

  override def byId(id: Long) = {
    Future {
      handleError(toUser) {
        getRequest(s"/user/id/$id")
      }
    }
  }

  override def byToken(token: String) = {
    Future {
      handleError(toUser) {
        getRequest(s"/user/token/${URLEncoder.encode(token, "utf8")}")
      }
    }
  }

  override def authenticateUser(r: AuthenticateRequest) = {
    val body = s"""{"email":"${escapeJson(r.email)}","password":"${escapeJson(r.password)}"}"""
    Future {
      handleViolation(toToken) {
        postRequest("/user/login", Some(body))
      }
    }
  }

  override def createAccount(r: CreateAccountRequest) = {
    val body = s"""{"name":"${escapeJson(r.name)}","type":"${escapeJson(r.`type`)}"}"""
    Future {
      handleViolation(accountOrViolation) {
        postRequest("/account", Some(body))
      }
    }
  }

  override def resetPassword(r: ResetPasswordRequest): Future[Either[Violation, User]] = {
    val body = s"""{"email":"${escapeJson(r.email)}"}"""
    Future {
      handleViolation(userOrViolation) {
        postRequest("/user/password/reset", Some(body))
      }
    }
  }

  override def createUser(r: CreateUserRequest) = {
    val body =
      s"""{
         |"account"  : ${r.account},
         |"name"     :"${escapeJson(r.name)}",
         |"email"    :"${escapeJson(r.email)}",
         |"type"     :"${escapeJson(r.`type`)}"
         |}""".stripMargin.replaceAll("\n", " ")

    Future {
      handleViolation(userOrViolation) {
        postRequest("/user", Some(body))
      }
    }
  }

  override def changePassword(request: ChangePasswordRequest) = {
    val body =
      s"""{
         |"email"       : "${escapeJson(request.email)}",
         |"old"         : "${escapeJson(request.old)}",
         |"replacement" : "${escapeJson(request.replacement)}"
         |}""".stripMargin.replaceAll("\n", " ")

    Future {
      handleViolation(discard) {
        postRequest("/user/password/change", Some(body))
      }
    }
  }

  override def assignPermission(request: AssignPermissionRequest) = {
    val body =
      s"""{
         |"user"      : ${request.user},
         |"permission": "${escapeJson(request.permission)}"
         |}""".stripMargin.replaceAll("\n", " ")

    Future {
      handleViolation(discard) {
        postRequest("/user/permission/assign", Some(body))
      }
    }
  }

  override def usersBy(account: Long) = {
    Future {
      handleError(toUsers) {
        getRequest(s"/account/${account}/users")
      }
    }
  }

  def discard  (response: String): Either[Violation, Unit]   = Right()
  def toString (response: String): Either[Violation, String] = Right(response)

  def accountOrViolation (response: String) : Either[Violation, Account]
  def userOrViolation    (response: String) : Either[Violation, User]
  def toToken            (response: String) : Either[Violation, Token]
  def toUser             (response: String) : Either[Throwable, User]
  def toUsers            (response: String) : Either[Throwable, Seq[User]]
}