package services

import java.time.Clock

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}

import scala.concurrent.ExecutionContext

trait Services {
  def actorSystem(): ActorSystem
  def clock()      : Clock
  def conf()       : Configuration
  def env()        : Environment
  def ec()         : ExecutionContext
}

@Singleton
class BasicServices @Inject() (
  theActorSystem      : ActorSystem,
  theClock            : Clock,
  theConf             : Configuration,
  theEnvironment      : Environment,
  theExecutionContext : ExecutionContext) extends Services {

  override def actorSystem() : ActorSystem      = theActorSystem
  override def clock()       : Clock            = theClock
  override def conf()        : Configuration    = theConf
  override def env()         : Environment      = theEnvironment
  override def ec()          : ExecutionContext = theExecutionContext
}
