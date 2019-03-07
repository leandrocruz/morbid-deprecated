package services

import java.time.Clock

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}

import scala.concurrent.ExecutionContext

trait Services {
  def ec()         : ExecutionContext
  def rnd()        : Random
  def env()        : Environment
  def conf()       : Configuration
  def clock()      : Clock
  def secrets()    : SecretValidator
  def actorSystem(): ActorSystem
}

@Singleton
class BasicServices @Inject() (
  executionContext : ExecutionContext,
  random           : Random,
  environment      : Environment,
  config           : Configuration,
  theClock         : Clock,
  secretValidator  : SecretValidator,
  system           : ActorSystem) extends Services {

  override def ec()          : ExecutionContext = executionContext
  override def rnd()         : Random           = random
  override def env()         : Environment      = environment
  override def conf()        : Configuration    = config
  override def clock()       : Clock            = theClock
  override def secrets()     : SecretValidator  = secretValidator
  override def actorSystem() : ActorSystem      = system
}
