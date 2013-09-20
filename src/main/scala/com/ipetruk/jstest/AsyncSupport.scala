package com.ipetruk.jstest

import concurrent.Future
import org.scalatra.{FutureSupport, ScalatraServlet, AsyncResult}

trait AsyncSupport {
  self: ScalatraServlet with FutureSupport =>

  protected def async(f: =>Future[_] )={
    new AsyncResult() {
      val is: Future[_] = f
    }
  }
}
