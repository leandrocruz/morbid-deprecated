package controllers

import java.time.Clock
import java.util.Date

import com.typesafe.config.ConfigRenderOptions
import javax.inject.Inject
import play.api.libs.json.Json._
import play.api.mvc.InjectedController
import domain.json._
import domain.ServerTime
import play.api.Configuration
import play.api.http.MimeTypes

class InternalController @Inject() (clock: Clock, config: Configuration) extends InjectedController {
  var count = 0
  def ping() = Action { count +=1 ; Ok(s"pong: $count") }
  def time() = Action { Ok { toJson(ServerTime(Date.from(clock.instant()))) } }
  def conf() = Action { Ok(config.get[Configuration]("app").underlying.root().render(ConfigRenderOptions.concise())).as(MimeTypes.JSON) }
  def stat() = Action { Ok }
}