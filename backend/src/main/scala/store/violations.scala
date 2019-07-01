package store

import org.postgresql.util.PSQLException
import store.violations._

trait Violation

object violations {
  case class UnknownViolation     (t: Throwable) extends Violation
  case class ForeignKeyViolation  (t: Throwable) extends Violation
  case class UniqueViolation      (t: Throwable) extends Violation
}

object Violations {
  def of(t: Throwable): Violation = t match {
    case sqlErr: PSQLException => sqlErr.getSQLState match {
      case "23503" => ForeignKeyViolation (t)
      case "23505" => UniqueViolation     (t)
      case _       => UnknownViolation    (t)
    }
    case _ => UnknownViolation(t)
  }
}
