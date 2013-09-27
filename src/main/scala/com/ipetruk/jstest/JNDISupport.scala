package com.ipetruk.jstest

import javax.naming.{NamingException, InitialContext}
import grizzled.slf4j.Logger

trait JNDISupport {
  def readJndi(paramName:String)={
    val fullName = "java:comp/env/" + paramName
    try {
        val ic = new InitialContext();
        ic.lookup(fullName);
    } catch {
      case (e: NamingException)=>{
        null
      }
    }
  }
}
