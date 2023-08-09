package store

import java.sql.Timestamp
import java.time.Instant
import java.util.Date

import domain._
import domain.collections.secrets
import services.{AppServices, TokenGenerator}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.util.control.NonFatal

trait Passwords extends ObjectStore[Password, CreatePasswordRequest] {
  def deleteByUser(userId: Long): Future[Either[Violation, Int]]
}

class DatabasePasswords (services: AppServices, db: Database, tokens: TokenGenerator) extends Passwords {

  implicit val ec = services.ec()

  private def now: Timestamp = new Timestamp(services.clock().instant().toEpochMilli)

  override def byId(id: Long) : Future[Option[Password]] = Future.failed(new Exception("TODO"))

  override def create(request: CreatePasswordRequest) : Future[Either[Violation, Password]] = {

    val instant = /* if(request.forceUpdate) Instant.EPOCH else */ services.clock().instant()
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
    } recover {
      case NonFatal(e) => Left(violations.of(e))
    }
  }

  override def deleteByUser(userId: Long): Future[Either[Violation, Int]] = {
    val query =
      for {
        secret <- secrets if secret.user === userId && secret.deleted.isEmpty
      } yield secret.deleted

    val delete = query.update(Some(now))

    db.run(delete) map {
      case i           => Right(i)
    } recover {
      case NonFatal(e) => Left(violations.of(e))
    }
  }
}
