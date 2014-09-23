package controllers

import enums.RoleEnum
import play.api.mvc._
import scala.concurrent.Future
import play.api.cache.Cache
import models.conf.{UserHelper, User, PermissionHelper}
import enums.FuncEnum._

case class RequestWithUser[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)

trait BaseController extends Controller with Security {

  val ALogger = play.Logger.of("ActionLog")

  def AuthAction[A](implicit func: Func*) = new ActionBuilder[({ type R[A] = RequestWithUser[A] })#R] {

    def invokeBlock[A](request: Request[A], block: (RequestWithUser[A]) => Future[SimpleResult]) = {
      authenticate(request, block)
    }
  }

  private def authenticate[A](request: Request[A], block: (RequestWithUser[A]) => Future[SimpleResult])(implicit func: Seq[Func]) = {
    val maybeToken = request.headers.get(AuthTokenHeader).orElse(request.getQueryString(AuthTokenUrlKey))
    maybeToken flatMap { token =>
      Cache.getAs[String](token) map { jobNo =>
        UserHelper.findByJobNo(jobNo) match {
          case Some(user) if user.role == RoleEnum.admin => block(new RequestWithUser[A](user, request))
          case Some(user) if user.role == RoleEnum.user =>
            findPermission(jobNo, func) match {
              case Some(true) => block(new RequestWithUser[A](user, request))
              case _ => Future.successful(Forbidden)
            }
          case _ => Future.successful(NotFound)
        }
      }
    } getOrElse Future.successful(Unauthorized)
  }

  private def findPermission(jobNo: String, func: Seq[Func]): Option[Boolean] = {
    PermissionHelper.findByJobNo(jobNo).map{ p =>
      p.functions.exists(func.contains(_))
    }
  }

  // 页面返回
  import play.api.libs.json.Json

  val _Success = Json.obj("r" -> "ok")
  val _Fail = Json.obj("r" -> "error")
  val _Exist = Json.obj("r" -> "exist")
  val _None = Json.obj("r" -> "none")

  def resultUnique(data: String) = _Exist.+("u", Json.toJson(data))

}
