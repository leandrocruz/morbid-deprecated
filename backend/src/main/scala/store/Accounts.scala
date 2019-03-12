package store

import java.sql.Timestamp
import java.util.Date

import akka.actor.{Actor, ActorLogging, Props}
import domain.collections.accounts
import domain.{Account, AccountTable, CreateAccountRequest, collections}
import services.AppServices
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

trait Accounts extends ObjectStore[Account, CreateAccountRequest] {}

class DatabaseAccounts (services: AppServices, db: Database) extends Accounts {

  implicit val ec = services.ec()

  override def byId(id: Long): Future[Option[Account]] = toAccount { collections.accounts.filter(_.id === id) }

  def toAccount(query: Query[AccountTable, (Long, Timestamp, Option[Timestamp], Boolean, String), Seq]) =
    db.run(query.result) map {
      _ map {
        case (id, created, deleted, active, name) =>
          Account(
            id       = id,
            created  = new Date(created.getTime),
            deleted  = deleted.map(it => new Date(it.getTime)),
            active   = active,
            name     = name
          )
      }
    } map {
      _.headOption
    }


  override def create(request: CreateAccountRequest): Future[Account] = {
    val instant = services.clock().instant()
    val created = new Timestamp(instant.toEpochMilli)
    val result  = db.run {
      (accounts returning accounts.map(_.id)) += (
        0l,
        created,
        null,
        true,
        request.name
      )
    }

    result map { id =>
      Account(
        id      = id,
        created = Date.from(instant),
        deleted = None,
        active  = true,
        name    = request.name)
    }
  }
}

object AccountManagerSupervisor {
  def props(services: AppServices) = Props(classOf[AccountManagerSupervisor], services)
}

class AccountManagerSupervisor (services: AppServices) extends Actor with ActorLogging {
  override def receive = {
    case "hi" => sender ! "hallo"
  }
}