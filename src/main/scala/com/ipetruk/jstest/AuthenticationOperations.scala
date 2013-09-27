package com.ipetruk.jstest

import org.json4s.{Extraction, Formats}
import concurrent.{ExecutionContext, Future, future}
import dispatch.{as}
import org.scalatra.{AsyncResult}

import java.util.UUID
import grizzled.slf4j.Logger
import javax.servlet.http.Cookie

case class Assertion(value: String)

case class ValidationResponse(status: String,
  audience: Option[String], expires: Option[Long], issuer: Option[String],email: Option[String],
  reason: Option[String])

case class ValidationClientResponse(email: Option[String] = None,error: Option[String] = None)

trait AuthenticationOperations {
  self:  AppStack
    with SessionDaoComponentApi
    with CSRFServiceComponentApi
    with JNDISupport =>

  implicit private[this] val executor = ExecutionContext.Implicits.global
  private[this] val logger = Logger(classOf[AuthenticationOperations])

  private val sidCookieName="app.sid.cookie"

  private def asyncPost[A](url:String, params:Map[String,String])(implicit
                                                          formats: Formats, mf: scala.reflect.Manifest[A]):Future[A]={
    logger.debug("Posting "+url+":"+params)
    val f = dispatch.Http((dispatch.url(url).POST << params) OK as.String).map{str=>
      Extraction.extract(readJsonFromBody(str))(formats, mf)}
    for (e<-f.failed){
      e.printStackTrace
    }
    f
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
          logger.debug("uuid = "+UUID.fromString(cookie))
          sessionDao.getSession(UUID.fromString(cookie)).map{s=>
            logger.debug("!! "+s)
            s.get("uid")
          }
        }).map(_.flatten.map(Session.apply _))

    val fResult = sessionFOpt.flatMap{ sessionOpt =>
      logger.debug("Got session: "+sessionOpt)
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
    csrfService.assertNoCSRF

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
    csrfService.assertNoCSRF

    contentType = formats("json")
    for (sid <- cookies.get(sidCookieName)){
      sessionDao.deleteSession(UUID.fromString(sid))
    }
    cookies.delete(sidCookieName)
  }

  post("/auth/validateAssertion"){
    contentType = formats("json")
    val assertion = parsedBody.extract[Assertion]
    logger.debug("Got assertion "+assertion)
    new AsyncResult {
      val is = {
        logger.debug("Running async post")

        val jndiSelfHost = Option(readJndi("app.host").asInstanceOf[String])

        val selfHost = jndiSelfHost.getOrElse( "http://localhost:8080/")

        logger.debug(s"Self host: $selfHost")

        asyncPost[ValidationResponse](
          "https://verifier.login.persona.org/verify",
          Map(
            "assertion"->assertion.value,
            "audience"->selfHost
          )
        ).flatMap{ personaResponse =>
          logger.debug("Got response"+response)
          personaResponse match {
            case r: ValidationResponse if r.status=="okay" => {
              logger.debug("Status good")
              val newEmail = r.email.get;
              val sidFuture:Future[UUID] = cookies.get(sidCookieName) match {
                case None => {
                  future {
                    val c = UUID.randomUUID()
                    logger.debug("No cookie found, generated = "+c)
                    c
                  }
                }
                case Some(sidStr) => {
                  logger.debug("Cookie found "+sidStr)
                  val sidUUID = UUID.fromString(sidStr)
                  for (session <- sessionDao.getSession(sidUUID)) yield {
                    logger.debug("Entering future")
                    val oldEmail = session.get("uid")
                    logger.debug("Got oldEMail "+oldEmail)
                    oldEmail match {
                      case Some(`newEmail`) => {
                        sidUUID
                      }
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
              for (e <- sidFuture.failed) yield {
                logger.warn("Error in session future",e)
                e.printStackTrace()
              }

              for (sid <- sidFuture) yield {
                logger.debug("Updating sid "+sid+", email="+newEmail)
                sessionDao.updateSessionFields(sid, Map(
                  "uid"->newEmail
                ))
                val cookie = new Cookie(sidCookieName,sid.toString)
                cookie.setPath("/")
                response.addCookie(cookie)
                csrfService.injectCSRFCookie
                logger.debug("Coookies configured ")
                val vr = ValidationClientResponse(email = personaResponse.email)
                logger.debug("Login ok "+vr)
                vr
              }
            }
            case other: ValidationResponse => {
              logger.debug("Invalid "+other)
              future(ValidationClientResponse(error = personaResponse.reason))
            }
          }
        }
      }
    }
  }

}
