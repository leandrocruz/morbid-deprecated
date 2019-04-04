package services

import java.time.Clock

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}
import store.Permissions
import xingu.commons.play.services.{BasicServices, Services}

import scala.concurrent.ExecutionContext

trait AppServices extends Services {
  def rnd()    : Random
  def secrets(): SecretValidator
}

@Singleton
class AppServicesImpl @Inject() (
  ec         : ExecutionContext,
  random     : Random,
  env        : Environment,
  config     : Configuration,
  clock      : Clock,
  validator  : SecretValidator,
  system     : ActorSystem) extends BasicServices(ec, env, config, clock, system) with AppServices {

  override def rnd()    : Random           = random
  override def secrets(): SecretValidator  = validator
}
