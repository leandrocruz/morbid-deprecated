package morbid.client

import java.net.URLEncoder

import morbid.client.domain._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

import org.apache.commons.text.StringEscapeUtils.escapeJson

import okhttp3._

trait MorbidClient {
  def createAccount (request: CreateAccountRequest) : Future[Try[Account]]
  def createUser    (request: CreateUserRequest)    : Future[Try[User]]
  def byToken(token: String)                        : Future[Try[User]]
  def authenticateUser(request: AuthenticateRequest): Future[Try[Token]]
}

abstract class HttpMorbidClientSupport (
  location         : String,
  implicit val ec  : ExecutionContext) extends MorbidClient {

  val json = MediaType.get("application/json") //; charset=utf-8

  val client = new OkHttpClient()

  def handle[T](fn: String => Try[T])(request: Request) =
    Try {
      client.newCall(request).execute()
    } flatMap { resp =>
      if(resp.isSuccessful) {
        fn(resp.body().string())
      } else {
        Failure(new Exception(s"Not 200: ${resp.code}"))
      }
    }


  def get[T](path: String)(fn: String => Try[T]): Future[Try[T]] = Future {
    handle(fn) {
      new Request.Builder().url(s"$location$path").build
    }
  }

  def post[T](path: String, body: Option[String])(fn: String => Try[T]): Future[Try[T]] = Future {
    handle(fn) {
      body map { it =>
        new Request.Builder().url(s"$location$path").post(RequestBody.create(it, json)).build
      } getOrElse {
        new Request.Builder().url(s"$location$path").build
      }
    }
  }

  override def byToken(token: String) =
    get(s"/user/token/${URLEncoder.encode(token, "utf8")}") { toUser }

  override def authenticateUser(r: AuthenticateRequest) = {
    val body = s"""{"username":"${escapeJson(r.username)}","password":"${escapeJson(r.password)}"}"""
    post("/user/login", Some(body)) { toToken }
  }

  override def createAccount(r: CreateAccountRequest) = {
    val body = s"""{"name":"${escapeJson(r.name)}","type":"${escapeJson(r.`type`)}"}"""
    post("/account", Some(body)) { toAccount }
  }

  override def createUser(r: CreateUserRequest) = {
    val body =
      s"""{
         |"account"  : ${r.account},
         |"username" :"${escapeJson(r.username)}",
         |"email"    :"${escapeJson(r.username)}",
         |"type"     :"${escapeJson(r.`type`)}"
         |}""".stripMargin.replaceAll("\n", " ")

    post("/user", Some(body)) { toUser }
  }

  def toAccount (response: String) : Try[Account]
  def toUser    (response: String) : Try[User]
  def toToken   (response: String) : Try[Token]
}