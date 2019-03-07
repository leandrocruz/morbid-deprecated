package controllers

import domain._
import domain.json._
import javax.inject.Inject
import services.Services
import store.Stores

class AccountController @Inject()(
  services : Services,
  stores   : Stores) extends ControllerSupport (services) {

  def byId (it: Long)= Action.async {
    toResult {
      stores.accounts().byId(it)
    }
  }

  def create() = Action.async(parse.json) { req =>
    createResourceDirectly[Account, CreateAccountRequest](req, stores.accounts())
  }
}