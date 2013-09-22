package com.ipetruk.jstest

import org.json4s.{Extraction, Formats}
import concurrent.Future
import dispatch.as
import org.scalatra.{FutureSupport, ScalatraServlet, AsyncResult}
import org.scalatra.json.JacksonJsonSupport

import scala.concurrent.future
import java.util.UUID

case class Assertion(value: String)

case class ValidationResponse(status: String,
  audience: Option[String], expires: Option[Long], issuer: Option[String],email: Option[String],
  reason: Option[String])

case class ValidationClientResponse(email: Option[String] = None,error: Option[String] = None)

trait AuthenticationOperations {
  self:  AppStack with SessionDaoComponentApi =>

  private val sidCookieName="app.sid.cookie"

  private def asyncPost[A](url:String, params:Map[String,String])(implicit
                                                          formats: Formats, mf: scala.reflect.Manifest[A]):Future[A]={
    dispatch.Http((dispatch.url(url).POST << params) OK as.String).map{str=>
      Extraction.extract(readJsonFromBody(str))(formats, mf)}
  }

  def getSid = cookies.get(sidCookieName)

  def reverseOption[T](i:Option[Future[T]]):Future[Option[T]]={
    i match {
      case Some(fut) => fut.map(Some.apply _)
      case None => future{ None }
    }
  }

  def asyncAuth(future: Session => Future[_]) = {
    import Future._

    val cookieOpt = getSid

    val sessionFOpt:Future[Option[Session]]=reverseOption(
        cookieOpt.map { cookie =>
          println("uuid = "+UUID.fromString(cookie))
          sessionDao.getSession(UUID.fromString(cookie)).map{s=>
            println("!! "+s)
            s.get("uid")
          }
        }).map(_.flatten.map(Session.apply _))

    val fResult = sessionFOpt.flatMap{ sessionOpt =>
      println(sessionOpt)
      sessionOpt match {
        case Some(session) => try{
          future(session)
        } catch {
          case e: Exception => {
            e.printStackTrace()
            future{
              halt(500)
            }
          }
        }
        case None => future{
          halt(401, "Unauthenticated")
        }
      }
    }

    fResult
  }

  get("/auth/currentUser") {
    contentType = formats("json")
    val cookie = cookies.get(sidCookieName)
    new AsyncResult {
      val is = cookie match {
        case None => future("")
        case Some(sid) => for (
          sessionData <- sessionDao.getSession(UUID.fromString(sid))
          ) yield {
            sessionData.getOrElse("uid","")
        }
      }
    }
  }

  post("/auth/logout") {
    contentType = formats("json")
    for (sid <- cookies.get(sidCookieName)){
      sessionDao.deleteSession(UUID.fromString(sid))
    }
    cookies.delete(sidCookieName)
  }

  post("/auth/validateAssertion"){
    contentType = formats("json")
    val assertion = parsedBody.extract[Assertion]
    new AsyncResult {
      val is = {
        asyncPost[ValidationResponse](
          "https://verifier.login.persona.org/verify",
          Map(
            "assertion"->assertion.value,
            "audience"->"http://localhost:8080/"
          )
        ).flatMap{ response =>
          response match {
            case r: ValidationResponse if r.status=="okay" => {
              val newEmail = r.email.get;
              val sidFuture:Future[UUID] = cookies.get(sidCookieName) match {
                case None => future {UUID.randomUUID()}
                case Some(sidStr) => {
                  val sidUUID = UUID.fromString(sidStr)
                  for (session <- sessionDao.getSession(sidUUID)) yield {
                    val oldEmail = session.get("uid")
                    oldEmail match {
                      case Some(`newEmail`) => sidUUID
                      case _ => {
                        for (_ <- oldEmail){
                          sessionDao.deleteSession(sidUUID)
                        }
                        UUID.randomUUID()
                      }
                    }
                  }
                }
              }

              for (sid <- sidFuture) yield {
                sessionDao.updateSessionFields(sid, Map("uid"->newEmail))
                cookies.set(sidCookieName, sid.toString)
                ValidationClientResponse(email = response.email)
              }
            }
            case other: ValidationResponse => {
              future(ValidationClientResponse(error = response.reason))
            }
          }
        }
      }
    }
  }

}
