package controllers

import domain._
import domain.json._
import javax.inject.Inject
import services.AppServices
import store.Stores

class AccountController @Inject()(
  services : AppServices,
  stores   : Stores) extends ControllerSupport (services) {

  def byId (it: Long)= Action.async {
    toResult {
      stores.accounts().byId(it)
    }
  }

  def create() = Action.async(parse.json) { implicit r =>
    createResourceDirectly[Account, CreateAccountRequest](stores.accounts())
  }
}