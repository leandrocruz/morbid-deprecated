package morbid.client

import java.net.URLEncoder

import morbid.client.domain._
import morbid.client.violations._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import org.apache.commons.text.StringEscapeUtils.escapeJson
import okhttp3._
import org.slf4j.LoggerFactory

trait MorbidClient {
  def createAccount    (request: CreateAccountRequest) : Future[Either[Violation, Account]]
  def createUser       (request: CreateUserRequest)    : Future[Either[Violation, User]]
  def authenticateUser (request: AuthenticateRequest)  : Future[Either[Violation, Token]]
  def byToken          (token: String)                 : Future[Try[User]]
}

abstract class HttpMorbidClientSupport (
  location         : String,
  implicit val ec  : ExecutionContext) extends MorbidClient {

  val json = MediaType.get("application/json") //; charset=utf-8

  val client = new OkHttpClient()

  val log = LoggerFactory.getLogger(getClass)

  val SeeServerLog = new Exception("See server log for details")

  def error(body: String) = body match {
    case "UnknownUser"                  => Left(UnknownUser)
    case "PasswordTooOld"               => Left(PasswordTooOld)
    case "PasswordMismatch"             => Left(PasswordMismatch)
    case "NoPasswordAvailable"          => Left(NoPasswordAvailable)
    case "PasswordAlreadyUsed"          => Left(PasswordAlreadyUsed)
    case "PasswordTooWeak"              => Left(PasswordTooWeak)
    case "UniqueViolation"              => Left(UniqueViolation              (SeeServerLog))
    case "ForeignKeyViolation"          => Left(ForeignKeyViolation          (SeeServerLog))
    case "IntegrityConstraintViolation" => Left(IntegrityConstraintViolation (SeeServerLog))
    case _ => Left(UnknownViolation(new Exception(body)))
  }

  def handleViolation[T](fn: String => Either[Violation, T])(request: Request) =
    Try(client.newCall(request).execute()) match {
      case Failure(e) =>
        log.error("Morbid Client Error", e)
        Left(UnknownViolation(e))
      case Success(r) =>
        val body = r.body().string()
        r.code() match {
        case 200 => fn    (body)
        case _   => error (body)
      }
    }

  def handleError[T](fn: String => Try[T])(request: Request): Try[T] =
    Try(client.newCall(request).execute()) match {
      case Failure(e) =>
        log.error("Morbid Client Error", e)
        Failure(e)
      case Success(r) =>
        if(r.isSuccessful) {
          fn(r.body().string())
        } else {
          log.error(s"Morbid Client Error 'Not 200: ${r.code()}'\n${r.body().string()}")
          Failure(new Exception(s"Not 200: ${r.code()}"))
        }
    }

  def getRequest(path: String) =
    new Request.Builder().url(s"$location$path").build

  def postRequest(path: String, body: Option[String]) =
    body map { it =>
      new Request.Builder().url(s"$location$path").post(RequestBody.create(it, json)).build
    } getOrElse {
      new Request.Builder().url(s"$location$path").build
    }

  override def byToken(token: String) =
    Future {
      handleError(toUser) {
        getRequest(s"/user/token/${URLEncoder.encode(token, "utf8")}")
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

  def accountOrViolation (response: String) : Either[Violation, Account]
  def userOrViolation    (response: String) : Either[Violation, User]
  def toToken            (response: String) : Either[Violation, Token]
  def toUser             (response: String) : Try[User]
}