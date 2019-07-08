package morbid.client

import java.net.URLEncoder

import morbid.client.domain._
import morbid.client.violations._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import org.apache.commons.text.StringEscapeUtils.escapeJson
import okhttp3._

trait MorbidClient {
  def createAccount (request: CreateAccountRequest) : Future[Either[Violation, Account]]
  def createUser    (request: CreateUserRequest)    : Future[Either[Violation, User]]
  def byToken(token: String)                        : Future[Try[User]]
  def authenticateUser(request: AuthenticateRequest): Future[Try[Token]]
}

abstract class HttpMorbidClientSupport (
  location         : String,
  implicit val ec  : ExecutionContext) extends MorbidClient {

  val json = MediaType.get("application/json") //; charset=utf-8

  val client = new OkHttpClient()

  val SeeServerLog = new Exception("See server log for details")

  def error(body: String) = body match {
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
      case Failure(e) => Left(UnknownViolation(e))
      case Success(r) =>
        val body = r.body().string()
        r.code() match {
        case 200 => fn    (body)
        case _   => error (body)
      }
    }

  def handleError[T](fn: String => Try[T])(request: Request) =
    Try(client.newCall(request).execute()) flatMap { r =>
      if(r.isSuccessful)
        fn(r.body().string())
      else
        Failure(new Exception(s"Not 200: ${r.code()}"))
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
    val body = s"""{"username":"${escapeJson(r.username)}","password":"${escapeJson(r.password)}"}"""
    Future {
      handleError(toToken) {
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
         |"username" :"${escapeJson(r.username)}",
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
  def toUser    (response: String) : Try[User]
  def toToken   (response: String) : Try[Token]
}