package store

import javax.inject.{Inject, Singleton}
import services.{AppServices, TokenGenerator}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Either

trait ObjectStore[T, CREATE] {
  def byId(id: Long): Future[Option[T]]
  def create(request: CREATE): Future[Either[Throwable, T]]
}

trait Stores {
  def accounts()    : Accounts
  def users()       : Users
  def passwords()   : Passwords
  def permissions() : Permissions
}

@Singleton
class StoresImpl @Inject()(
  services: AppServices,
  tokens  : TokenGenerator) extends Stores {

  private val db             : PostgresProfile.backend.Database = Database.forConfig("database")
  private val ACCOUNTS       : Accounts    = new DatabaseAccounts    (services, db)
  private val USERS          : Users       = new DatabaseUsers       (services, db, tokens)
  private val PASSWORDS      : Passwords   = new DatabasePasswords   (services, db, tokens)
  private val PERMISSIONS    : Permissions = new DatabasePermissions (services, db)
  override def accounts()    : Accounts    = ACCOUNTS
  override def users()       : Users       = USERS
  override def passwords()   : Passwords   = PASSWORDS
  override def permissions() : Permissions = PERMISSIONS
}
