package store

import java.sql.Timestamp
import java.util
import java.util.Date

import domain._
import domain.collections._
import javax.inject.Inject
import org.slf4j.{Logger, LoggerFactory}
import services.{AppServices, TokenGenerator}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait Permissions extends ObjectStore[List[Permission], AddPermissionRequest] {
  def byId(userId: Long) : Future[Option[List[Permission]]]
}

class DatabasePermissions(services: AppServices, db: Database) extends Permissions {

  implicit val ec: ExecutionContext = services.ec()
  val log: Logger = LoggerFactory.getLogger(getClass)

  override def byId(it: Long): Future[Option[List[Permission]]] = Future.failed(new Exception("ERR - TODO"))

  override def create(request: AddPermissionRequest): Future[List[Permission]] = {
    val option: Option[Seq[String]] = request.permissions
    val permissionsList = List[Permission]()

    if(option.isEmpty)
      Future.failed(new Exception("Permissions must be provided."))

    option.get.foreach { permission =>

      val instant = services.clock().instant()
      val created = new Timestamp(instant.toEpochMilli)

      val v = db.run {
      (permissions returning permissions.map(_.id)) += (
          0l,
          request.userId,
          permission,
          request.createdBy.get,
          created,
          None,
          None,
        )
      } map { id =>
        Permission(
          id = id,
          userId = request.userId,
          permission = permission,
          createdBy = request.createdBy.get,
          created = Date.from(instant),
          deletedBy = None,
          deleted = None)
      }

      permissionsList.::(v)
    }

    Future.successful(permissionsList)
  }
}