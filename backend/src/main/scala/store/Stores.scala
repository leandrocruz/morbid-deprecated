package store

import javax.inject.{Inject, Singleton}
import services.{Services, TokenGenerator}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.language.postfixOps

trait ObjectStore[T, CREATE] {
  def byId(id: Long): Future[Option[T]]
  def create(request: CREATE): Future[T]
}

trait Stores {
  def accounts()  : Accounts
  def users()     : Users
  def passwords() : Passwords
}

@Singleton
class StoresImpl @Inject()(
  services: Services,
  tokens  : TokenGenerator) extends Stores {

  private val db           : PostgresProfile.backend.Database = Database.forConfig("database")
  private val ACCOUNTS     : Accounts  = new DatabaseAccounts  (services, db)
  private val USERS        : Users     = new DatabaseUsers     (services, db, tokens)
  private val PASSWORDS    : Passwords = new DatabasePasswords (services, db, tokens)
  override def accounts()  : Accounts  = ACCOUNTS
  override def users()     : Users     = USERS
  override def passwords() : Passwords = PASSWORDS
}
