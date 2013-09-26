package com.ipetruk.jstest

import org.scalatra.{ScalatraServlet, CookieContext}
import java.security.SecureRandom
import com.ning.http.util.Base64
import grizzled.slf4j.Logger

trait СSRFServiceApi {
  def assertNoCSRF:Unit

  def injectCSRFCookie:Unit
}

trait CSRFServiceComponentApi{
  def csrfService:СSRFServiceApi
}

trait CSRFServiceComponent extends CSRFServiceComponentApi{
  self: ScalatraServlet =>

  lazy val csrfService: СSRFServiceApi = new СSRFServiceApi{
    private[this] val logger = Logger(classOf[CSRFServiceComponent])

    private val csrfCookieName="XSRF-TOKEN"
    private val csrfHeaderName="X-XSRF-TOKEN"
    private val secureRandom = new SecureRandom()

    private def provideCSRFCookieValue = {
      synchronized{
        logger.debug("Generating CSRF token...")
        val bytes = Array.ofDim[Byte](128)
        secureRandom.nextBytes(bytes)
        val r = Base64.encode(bytes)
        logger.debug("Done CSRF: "+r) // Should not be logged in real life
        r
      }
    }


    def assertNoCSRF {
      val matchStatus = for{
        cookie <- cookies.get(csrfCookieName).map('"'+_+'"');
        header <- Option(request.getHeader(csrfHeaderName))
      } yield {
        cookie == header
      }

      matchStatus match {
        case Some(false) => halt(403, reason = "CSRF mismatch")
        case None => halt(403, reason="No CSRF cookie or header")
        case Some(true) => {}
      }
    }

    def injectCSRFCookie {
      cookies+=(csrfCookieName->provideCSRFCookieValue)
    }
  }
}
