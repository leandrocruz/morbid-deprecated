package store

import java.sql.Timestamp
import java.util.Date

import domain._
import domain.collections.secrets
import services.{AppServices, TokenGenerator}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

trait Passwords extends ObjectStore[Password, CreatePasswordRequest] {}

class DatabasePasswords (services: AppServices, db: Database, tokens: TokenGenerator) extends Passwords {

  implicit val ec = services.ec()

  override def byId(id: Long) : Future[Option[Password]] = Future.failed(new Exception("TODO"))

  override def create(request: CreatePasswordRequest) : Future[Either[Throwable, Password]] = {
    val instant = services.clock().instant()
    val created = new Timestamp(instant.toEpochMilli)
    db.run {
      (secrets returning secrets.map(_.id)) += (
        0l,
        request.user,
        created,
        null,
        request.method,
        request.password,
        request.token
      )
    } map { id =>
      Right(
        Password(
        id        = id,
        user      = request.user,
        created   = Date.from(instant),
        deleted   = None,
        method    = request.method,
        password  = request.password,
        token     = request.token
      ))
    }
  }
}
