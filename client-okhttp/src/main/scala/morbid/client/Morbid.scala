package morbid.client

import java.net.URLEncoder

import morbid.client.domain._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

import okhttp3._

trait MorbidClient {
  def byToken(token: String): Future[Try[User]]
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

  override def authenticateUser(request: AuthenticateRequest) = {
    val body = s"""{"username":"${request.username}","password":"${request.password}"}"""
    post("/user/login", Some(body)) { toToken }
  }

  def toUser(response: String)  : Try[User]
  def toToken(response: String) : Try[Token]
}