package store

import java.sql.Timestamp
import java.util.Date

import domain._
import domain.collections.secrets
import domain.utils._
import org.slf4j.LoggerFactory
import services.{Services, TokenGenerator}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

trait Passwords extends ObjectStore[Password, CreatePasswordRequest] {}

class DatabasePasswords (services: Services, db: Database, tokens: TokenGenerator) extends Passwords {

  implicit val ec = services.ec()

  val log = LoggerFactory.getLogger(getClass)

  override def byId(id: Long) : Future[Option[Password]] = Future.failed(new Exception("TODO"))

  override def create(request: CreatePasswordRequest) : Future[Password] = {
    val token   = services.rnd().generate(32)
    val instant = services.clock().instant()
    val created = new Timestamp(instant.toEpochMilli)
    val secret  = request.password.sha256()

    db.run {
      (secrets returning secrets.map(_.id)) += (
        0l,
        request.user,
        created,
        null,
        request.method,
        secret,
        token
      )
    } map { id =>
      Password(
        id        = id,
        user      = request.user,
        created   = Date.from(instant),
        deleted   = None,
        method    = request.method,
        password  = secret,
        token     = token
      )
    }

  }
}
